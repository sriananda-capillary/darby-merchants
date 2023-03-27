package com.capillary.spar.processor;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capillary.spar.db.dto.SparStockPriceFetchReq;
import com.capillary.spar.enums.STOCK_PRICE_SYNC_STATUS;
import com.capillary.spar.model.SparStockAndPriceDataImport;
import com.capillary.spar.service.SparPriceDataImportService;
import com.capillary.spar.service.SparStockDataImportService;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.capillary.spar.util.SparStockPriceSyncUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.frontend.request.APIParams;
import com.sellerworx.darby.frontend.request.GetAllProductsAPIParams;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.GroupByAny;
import com.sellerworx.darby.util.Grouper;
import com.sellerworx.darby.util.ProductCustomKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.martjack.dto.ProductWithMJProductDetails;
import com.sellerworx.modules.martjack.frontend.response.FrontProductVariants;
import com.sellerworx.modules.martjack.frontend.response.LocationResource;
import com.sellerworx.modules.martjack.frontend.response.Product;
import com.sellerworx.modules.martjack.frontend.services.MJFrontEndCatalogService;
import com.sellerworx.modules.martjack.util.MJUtil;
import com.sellerworx.modules.martjack.util.StockPriceSyncConstants;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("SparStockAndPriceFetchProcessor")
public class SparStockAndPriceFetchProcessor extends DarbyBaseProcessor {
    private static final String LOCATION_REFERENCE_CODE = ProductCustomKeys.LOCATION_REF_CODE;
    private static final String STOCK = "stock";
    private static final int DEFAULT_LIMIT = 2500;
    private static final int BATCH_LIMIT = 25000;
    private static final int PRODUCT_DETAILS_API_SKU_LIMIT = 20;

    @Autowired
    private MJFrontEndCatalogService mjFronEndCtlgSvc;
    @Autowired
    private SparStockDataImportService stockDataImportService;
    @Autowired
    private SparPriceDataImportService priceDataImportService;

    public void startProcess(Exchange exchange) {
        List<LocationResource> locationList =
                (List<LocationResource>) ExchangeHeaderKeys.getValueFromExchangeHeader(
                        ExchangeHeaderKeys.MJ_LOCATION, exchange);
        int loopSize;
        String tableName;
        String lookUpTablePrefix = SparStockPriceSyncUtil.LOOK_UP_TABLE_PREFIX + SymbolUtil.UNDERSCORE;
        String lookUpTableName = stockDataImportService.getLastCreatedTableName(lookUpTablePrefix);
        List<FieldErrorModel> fieldErrorModelList = new ArrayList<>();
        String syncType = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.SYNC_TYPE,
                                                                                 exchange);
        int stockSyncBatchLimit = (int) ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_STOCK_SYNC_BATCH_SIZE, exchange, BATCH_LIMIT);
        int priceSyncBatchLimit = (int) ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_PRICE_SYNC_BATCH_SIZE, exchange, BATCH_LIMIT);
        if (StockPriceSyncConstants.STOCK.equals(syncType)) {
            tableName =
                    SparStockPriceSyncUtil.STOCK_DUMP_TABLE_PREFIX + SymbolUtil.UNDERSCORE + RequestContext
                            .getTenantInfo().getAccountId();
            loopSize = stockSyncBatchLimit / DEFAULT_LIMIT;
        }
        else {
            tableName =
                    SparStockPriceSyncUtil.PRICE_DUMP_TABLE_PREFIX + SymbolUtil.UNDERSCORE + RequestContext
                            .getTenantInfo().getAccountId();
            loopSize = priceSyncBatchLimit / DEFAULT_LIMIT;
        }
        Set<String> locCodes = MJUtil.getLocationCodes(locationList);
        log.info("started : {} sync to MJ with loopsize {}", syncType, loopSize);
        int offset = 0;
        List<SparStockAndPriceDataImport> totalRecords = new ArrayList<>();

        int fetchDurationMinutes = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.STOCK_AND_PRICE_FETCH_DURATION_MINUTES, exchange, 60);

        STOCK_PRICE_SYNC_STATUS[] statusIn =
                new STOCK_PRICE_SYNC_STATUS[]{STOCK_PRICE_SYNC_STATUS.PROCESSING, STOCK_PRICE_SYNC_STATUS.FAILED};
        long currentTimeMillis = System.currentTimeMillis();
        Timestamp createdAtTo = new Timestamp(currentTimeMillis);
        Timestamp createdAtFrom = new Timestamp(
                currentTimeMillis - TimeUnit.MINUTES.toMillis(fetchDurationMinutes));

        try {
            SparStockPriceFetchReq req = SparStockPriceFetchReq.builder().tableName(tableName)
                                                               .limit(DEFAULT_LIMIT).locationCodes(locCodes)
                                                               .productDetailsLookUpTable(lookUpTableName)
                                                               .createdAtFrom(createdAtFrom)
                                                               .createdAtTo(createdAtTo)
                                                               .statusIn(statusIn)
                                                               .build();
            req.setOffset(offset);
            for (int i = 0; i < loopSize; i++) {

                List<SparStockAndPriceDataImport> queryResult;
                if (syncType.equals(StockPriceSyncConstants.PRICE)) {
                    queryResult = priceDataImportService.fetchAndUpdateRecords(req,
                                                                               STOCK_PRICE_SYNC_STATUS.PROCESSING
                                                                                       .toString());
                }
                else {
                    queryResult = stockDataImportService.fetchAndUpdateRecords(req,
                                                                               STOCK_PRICE_SYNC_STATUS.PROCESSING
                                                                                       .toString());
                }
                totalRecords.addAll(queryResult);
                if (CollectionUtils.isEmpty(queryResult) || queryResult.size() < DEFAULT_LIMIT) {
                    log.info("resultset is empty exiting from loop");
                    break;
                }
                log.info(
                        "the fetched records during sync {} for iteration {} are of size {} with offset {} are {} ",
                        syncType, i, queryResult.size(), offset, queryResult);
                offset += DEFAULT_LIMIT;
                req.setOffset(offset);

            }
            log.info("total number of fetched records found for {} are {} ", syncType, totalRecords.size());
        } catch (DarbyException ex) {
            log.error("error while fetching the stock price sync records for:" + syncType, ex);
            throw ex;
        }


        List<ProductWithMJProductDetails> products = prepareProductListForAPICall(totalRecords, syncType,
                                                                                  fieldErrorModelList,
                                                                                  exchange, lookUpTableName);

        log.info("product list before api calling: {}", products.size());
        addSkuForVariants(products);
        ExchangeUtil.setBody(products, exchange);
        setBatchProcessingDetails(exchange, fieldErrorModelList, products.size());
    }

    private void addSkuForVariants(List<ProductWithMJProductDetails> products) {
        products.stream().forEach(p -> {
            if (isVariantSku(p)) {
                p.setVariantSku(p.getSku());
                p.setSku(p.getMjProdDetails().getSku());
            }
        });
    }

    protected List<ProductWithMJProductDetails> prepareProductListForAPICall(
            List<SparStockAndPriceDataImport> sparImportDataList,
            String reportName, List<FieldErrorModel> fieldErrorModelList, Exchange exchange, String lookUpTableName) {
        List<ProductWithMJProductDetails> productList = new ArrayList<>();
        List<SparStockAndPriceDataImport> invalidRecords = new ArrayList<>();
        List<SparStockAndPriceDataImport> failedRecords = new ArrayList<>();
        List<SparStockAndPriceDataImport> ignoredProductList = getIgnoredProductsList(sparImportDataList);
        if (CollectionUtils.isNotEmpty(ignoredProductList)) {
            sparImportDataList.removeAll(ignoredProductList);
        }
        if (CollectionUtils.isNotEmpty(sparImportDataList)) {

            List<SparStockAndPriceDataImport> blankProductDtlsSparDataLst = new ArrayList<>();

            ListIterator<SparStockAndPriceDataImport> dataImportIterator = sparImportDataList.listIterator();
            while (dataImportIterator.hasNext()) {
                SparStockAndPriceDataImport dataImport = dataImportIterator.next();
                if (StringUtils.isBlank(dataImport.getProductDetails())) {
                    blankProductDtlsSparDataLst.add(dataImport);
                    dataImportIterator.remove();
                }
            }

            List<SparStockAndPriceDataImport> validEmptyProducts = new ArrayList<>();
            for (List<SparStockAndPriceDataImport> dataList : ListUtils.partition(blankProductDtlsSparDataLst,
                                                                                  PRODUCT_DETAILS_API_SKU_LIMIT)) {

                validEmptyProducts.addAll(
                        handleProductsWithEmptyEcomProductDetails(dataList, invalidRecords, failedRecords, lookUpTableName,
                                                                  reportName, productList));


            }
            if (reportName.equals(StockPriceSyncConstants.STOCK)) {
                productList.addAll(buildStockObject(sparImportDataList, null,invalidRecords));
            }
            else {
                productList.addAll(buildPriceObject(sparImportDataList, null,invalidRecords));
            }

            /**
             * adding back valid products for empty ecom details
             */
            sparImportDataList.addAll(validEmptyProducts);

        }
        else {
            log.info("no records found while processing file");
        }

        if (CollectionUtils.isNotEmpty(ignoredProductList)) {
            String errorMsg = "product is ignored as old stock or price data encountered";
            for (SparStockAndPriceDataImport ignoredRecord : ignoredProductList) {
                FieldErrorModel fieldErrorModel = new FieldErrorModel(ignoredRecord.toString(), errorMsg,
                                                                      ErrorCode.CONFLICT.toString());
                fieldErrorModelList.add(fieldErrorModel);
            }
        }

        if (CollectionUtils.isNotEmpty(failedRecords)) {
            String errorMsg = "get all products api failed";
            for (SparStockAndPriceDataImport failedRecord : failedRecords) {
                FieldErrorModel fieldErrorModel = new FieldErrorModel(failedRecord.toString(), errorMsg,
                                                                      ErrorCode.UNKNOWN.toString());
                fieldErrorModelList.add(fieldErrorModel);
            }
        }

        if (CollectionUtils.isNotEmpty(invalidRecords)) {
            String errorMsg = "product not found in mj";
            for (SparStockAndPriceDataImport invalidRecord : invalidRecords) {
                FieldErrorModel fieldErrorModel = new FieldErrorModel(invalidRecord.toString(), errorMsg,
                                                                      ErrorCode.NOTFOUND.toString());
                fieldErrorModelList.add(fieldErrorModel);
            }
        }

        log.info("spar valid stock price records size: {}", sparImportDataList.size());

        ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.SPAR_STOCK_PRICE_VALID_RECORDS, sparImportDataList,
                                       exchange);
        ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.SPAR_STOCK_PRICE_INVALID_RECORDS,
                                       invalidRecords, exchange);
        ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.SPAR_STOCK_PRICE_FAILED_RECORDS,
                                       failedRecords, exchange);
        log.info("spar invalid stock price records size: {}", invalidRecords.size());
        log.info("spar mj api failed stock price records size: {}", failedRecords.size());
        ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.SPAR_STOCK_PRICE_IGNORED_RECORDS,
                                       ignoredProductList,
                                       exchange);
        log.info("spar ignored stock price records size: {}", ignoredProductList.size());
        log.info("the product list is of size {}", productList.size());
        return productList;
    }


    private List<SparStockAndPriceDataImport> handleProductsWithEmptyEcomProductDetails(
            List<SparStockAndPriceDataImport> dataImportWithoutEcomDataList,
            List<SparStockAndPriceDataImport> invalidRecords,
            List<SparStockAndPriceDataImport> failedRecords, String lookUpTableName, String syncType,
            List<ProductWithMJProductDetails> productList) {

        log.trace("validating stock object"); //todo correct
        List<SparStockAndPriceDataImport> copyOfImportWithoutEcomDataList = new ArrayList<>(dataImportWithoutEcomDataList);
        List<ProductWithMJProductDetails> productWithMJDetailsList = new ArrayList<>();
        Map<String, List<SparStockAndPriceDataImport>> dataImportBySkuMap = new HashMap<>();
        List<SparStockAndPriceDataImport> validProductList = new ArrayList<>();

        dataImportWithoutEcomDataList.stream().forEach(importItem -> {
            List<SparStockAndPriceDataImport> sparStockAndPriceDataImportList = dataImportBySkuMap.computeIfAbsent(
                    importItem.getSku(), item -> new ArrayList<>());

            sparStockAndPriceDataImportList.add(importItem);
        });

        List<Product> ecomProductDataList = null;
        try {
            ecomProductDataList = fetchProductDetailsFromMJ(dataImportBySkuMap.keySet());
            if (CollectionUtils.isEmpty(ecomProductDataList)) {
                invalidRecords.addAll(dataImportWithoutEcomDataList);
                return validProductList;
            }
        } catch (Exception e) {
            String errMsg = "error while calling get all products api";
            log.error(errMsg, e);
            failedRecords.addAll(dataImportWithoutEcomDataList);
            return validProductList;
        }

        insertProductDataInDB(ecomProductDataList, lookUpTableName);
        /**
         * TODO
         * the one which are not present in response
         * add them to invalid list.
         * if api fails add all to failed list.
         */
        ecomProductDataList.stream().forEach(ecomProductData -> {
            List<SparStockAndPriceDataImport> fetchedImportFromMap = getSparDataBySku(ecomProductData, dataImportBySkuMap);
            if (CollectionUtils.isNotEmpty(fetchedImportFromMap)) {
                validProductList.addAll(fetchedImportFromMap);
                if (StockPriceSyncConstants.STOCK.equals(syncType)) {
                    productWithMJDetailsList.addAll(
                            buildStockObject(fetchedImportFromMap, ecomProductData,invalidRecords));
                }
                else {
                    productWithMJDetailsList.addAll(
                            buildPriceObject(fetchedImportFromMap, ecomProductData,invalidRecords));

                }
            }
        });


        copyOfImportWithoutEcomDataList.removeAll(validProductList);
        invalidRecords.addAll(copyOfImportWithoutEcomDataList);
        productList.addAll(productWithMJDetailsList);
        return validProductList;
    }

    private List<SparStockAndPriceDataImport> getSparDataBySku(Product ecomProductData,
                                                               Map<String, List<SparStockAndPriceDataImport>> dataImportBySkuMap) {
        if (dataImportBySkuMap.containsKey(ecomProductData.getSku())) {
            return dataImportBySkuMap.get(ecomProductData.getSku());
        }
        else if (CollectionUtils.isNotEmpty(ecomProductData.getProductVariants())) {
            FrontProductVariants frontProductVariants = ecomProductData.getProductVariants().stream().filter(
                    ecomVariant -> dataImportBySkuMap.containsKey(ecomVariant.getVariantSku())).findFirst().orElse(null);
            if (null != frontProductVariants) {
                return dataImportBySkuMap.get(frontProductVariants.getVariantSku());
            }
        }
        return null;
    }


    private List<ProductWithMJProductDetails> buildPriceObject(List<SparStockAndPriceDataImport> dataImportList,
                                                               Product ecomProductData,List<SparStockAndPriceDataImport> invalidRecords) {
        log.trace("validating price object");
        BigDecimal mrp = null;
        BigDecimal webPrice = null;
        String sku = null;
        String locationRefCode = null;
        List<ProductWithMJProductDetails> productWithMJDetailsList = new ArrayList<>();
        for (SparStockAndPriceDataImport dataImport : dataImportList) {
            if (!StringUtils.isBlank(dataImport.getSku())) {
                sku = dataImport.getSku();
            }
            if (!StringUtils.isBlank(dataImport.getLocationCode())) {
                locationRefCode = dataImport.getLocationCode();
            }
            if (!StringUtils.isBlank(dataImport.getMrp())) {
                if (new BigDecimal(dataImport.getMrp()).compareTo(BigDecimal.ZERO) > 0) {
                    mrp = new BigDecimal(dataImport.getMrp());
                }
            }
            if (!StringUtils.isBlank(dataImport.getWebPrice())) {
                if (new BigDecimal(dataImport.getWebPrice()).compareTo(BigDecimal.ZERO) > 0) {
                    webPrice = new BigDecimal(dataImport.getWebPrice());
                }
            }
            ProductWithMJProductDetails product = new ProductWithMJProductDetails();
            product.setSku(sku);
            product.addCustomField(LOCATION_REFERENCE_CODE, locationRefCode);
            product.addCustomField(ProductCustomKeys.QUANTITY, 1);
            product.setMrp(mrp);
            product.setPrice(webPrice);
            if (null != ecomProductData) {
                product.setMjProdDetails(ecomProductData);
            }
            else {
                Product mjProductDetails;
                try {
                    mjProductDetails = new ObjectMapper().readValue(dataImport.getProductDetails(), Product.class);
                    product.setMjProdDetails(mjProductDetails);
                } catch (IOException e) {
                    log.error("io exception while forming product obj from json:"+dataImport.getProductDetails(), e);
                    invalidRecords.add(dataImport);
                    continue;
                }

            }
            productWithMJDetailsList.add(product);
        }

        return productWithMJDetailsList;
    }


    private List<ProductWithMJProductDetails> buildStockObject(List<SparStockAndPriceDataImport> dataImportList,
                                                               Product ecomProductData,List<SparStockAndPriceDataImport> invalidRecords) {
        log.trace("validating stock object");
        String stockValue = null;
        String sku = null;
        String locationRefCode = null;
        List<ProductWithMJProductDetails> productWithMJDetailsList = new ArrayList<>();
        for (SparStockAndPriceDataImport dataImport : dataImportList) {
            ProductWithMJProductDetails product = new ProductWithMJProductDetails();
            if (StringUtils.isNotBlank(dataImport.getSku())) {
                sku = dataImport.getSku();
            }
            if (StringUtils.isNotBlank(dataImport.getLocationCode())) {
                locationRefCode = dataImport.getLocationCode();
            }
            if (StringUtils.isNotBlank(dataImport.getStock())) {
                stockValue = dataImport.getStock();
            }
            if (null != ecomProductData) {
                product.setMjProdDetails(ecomProductData);
            }
            else {
                Product mjProductDetails;
                try {
                    mjProductDetails = new ObjectMapper().readValue(dataImport.getProductDetails(), Product.class);
                    product.setMjProdDetails(mjProductDetails);
                } catch (IOException e) {
                    log.error("io exception while forming product obj from json"+dataImport.getProductDetails(), e);
                    invalidRecords.add(dataImport);
                    continue;
                }
            }
            product.addCustomField(STOCK, stockValue);
            product.setSku(sku);
            product.addCustomField(LOCATION_REFERENCE_CODE, locationRefCode);
            productWithMJDetailsList.add(product);
        }

        return productWithMJDetailsList;
    }

    private List<Product> fetchProductDetailsFromMJ(Collection<String> skuList) {
        GetAllProductsAPIParams apiParams = new GetAllProductsAPIParams();
        apiParams.setIncludeUnavailable(false);
        apiParams.setSkus(skuList.toArray(new String[0]));
        apiParams.setLanguageCode(APIParams.LANGUAGE_CODE.EN);

        return mjFronEndCtlgSvc.getAllProducts(apiParams);
    }

    private void setBatchProcessingDetails(Exchange exchange, List<FieldErrorModel> fieldErrorList,
                                           int totalCount) {
        BatchProcessDetails batchProcessDetails = null;
        List<FieldErrorModel> fieldErrorModels = null;
        Object batchProcessingDetails = ExchangeHeaderKeys.getValueFromExchangeHeader(
                ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, exchange, false);
        if (null != batchProcessingDetails) {
            batchProcessDetails = (BatchProcessDetails) batchProcessingDetails;
        }
        else {
            batchProcessDetails = new BatchProcessDetails();
            String fileName =
                    (String) ExchangeHeaderKeys.getValueFromExchangeHeader(Exchange.FILE_NAME_CONSUMED,
                                                                           exchange);
            batchProcessDetails.setFileName(fileName);
        }
        batchProcessDetails.setTotalCount(batchProcessDetails.getTotalCount() + totalCount);
        fieldErrorModels = batchProcessDetails.getFieldErrorModelList();
        fieldErrorModels = fieldErrorModels == null ? new ArrayList<>() : fieldErrorModels;

        fieldErrorModels.addAll(fieldErrorList);
        batchProcessDetails.setValidationCount(
                batchProcessDetails.getValidationCount() + fieldErrorList.size());

        batchProcessDetails.setFieldErrorModelList(fieldErrorModels);
        ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, batchProcessDetails,
                                       exchange);
    }


    //if multiple records are there with same loc code and sku find the latest one and ignore others
    //TODO comaprison to be done based on created date
    private List<SparStockAndPriceDataImport> getIgnoredProductsList(
            List<SparStockAndPriceDataImport> dataImportsList) {


        List<SparStockAndPriceDataImport> ignoredRecordsList = new ArrayList<>();

        GroupByAny<SparStockAndPriceDataImport> groupBySkuLoc = new GroupBySkuLoc();
        List<List<SparStockAndPriceDataImport>> productListGroupBy = Grouper.group(dataImportsList,
                                                                                   groupBySkuLoc);

        for (List<SparStockAndPriceDataImport> groupedProduct : productListGroupBy) {
            Set<SparStockAndPriceDataImport> sparProduct = new HashSet<>(groupedProduct);
            if (sparProduct.size() > 1) {
                log.info("multiple records with same loccode and sku {}", sparProduct);
                Optional<SparStockAndPriceDataImport> optional = sparProduct.stream().max(
                        Comparator.comparing(SparStockAndPriceDataImport::getId));
                SparStockAndPriceDataImport dataImport = optional.get();
                sparProduct.remove(dataImport);
                if (CollectionUtils.isNotEmpty(sparProduct)) {
                    log.info("adding records to ignored list {}", sparProduct);
                    ignoredRecordsList.addAll(sparProduct);
                }
            }
        }
        return ignoredRecordsList;
    }

    //TODO need to refactor this method later
    private boolean isVariantSku(ProductWithMJProductDetails product) {
        if (CollectionUtils.isEmpty(product.getMjProdDetails().getProductVariants())) {
            return false;
        }
        return product.getMjProdDetails().getProductVariants().stream().anyMatch(
                v -> v.getVariantSku().equals(product.getSku()));
    }

    private class GroupBySkuLoc implements GroupByAny<SparStockAndPriceDataImport> {


        public Object getKey(SparStockAndPriceDataImport product) {
            return new SkuLocRefCode(product);
        }


        @Data
        @FieldDefaults(level = AccessLevel.PRIVATE)
        private class SkuLocRefCode {

            final String sku;
            final String locationRefCode;

            public SkuLocRefCode(SparStockAndPriceDataImport dataImport) {
                this.sku = dataImport.getSku();
                //TODO  currently empty  sku and location code is not possible but need to handle later
                if (StringUtils.isBlank(sku)) {
                    log.error("sku is empty in product: {}", dataImport);
                    /**
                     *TODO
                     * handle exception so that execution should not break
                     * and details get added in batchDetails
                     */
                    throw new DarbyException("sku and vsku are empty in product: " + dataImport,
                                             ErrorCode.EMPTY);

                }
                this.locationRefCode = (String) dataImport.getLocationCode();
                if (StringUtils.isBlank(this.locationRefCode)) {
                    log.error("location ref code in product is empty sku: {}", dataImport.getSku());
                    /**
                     *TODO
                     * handle exception so that execution should not break
                     * and details get added in batchDetails
                     */
                    throw new DarbyException("location ref code i product is empty sku: " + dataImport
                            .getSku(), ErrorCode.EMPTY);
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof SkuLocRefCode)) {
                    return false;
                }
                SkuLocRefCode skuLocRefCode = (SkuLocRefCode) o;
                return StringUtils.equalsIgnoreCase(sku, skuLocRefCode.sku) &&
                       StringUtils.equalsIgnoreCase(locationRefCode, skuLocRefCode.locationRefCode);
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(sku.toLowerCase(), locationRefCode.toLowerCase());
            }
        }

    }


    private void insertProductDataInDB(List<Product> productList, String tableName) {
        List<List<String>> batchInsertValues = new ArrayList<>();
        for (Product product : productList) {
            product.setCategories(null);
            product.setProductAttributes(null);
            product.setBundleProductGroups(null);
            List<String> productDetail = new ArrayList<>();
            String productAsJsonString = null;
            try {
                log.info("converting product with sku object to json after fetching from mj:{}", product.getSku());
                productAsJsonString = new ObjectMapper().writeValueAsString(product);
            } catch (JsonProcessingException e) {
                log.error("error while converting product object to json after fetching from mj:{}", product, e);
                continue;
            }
            productDetail.add(product.getSku());
            if (CollectionUtils.isNotEmpty(product.getProductVariants())) {
                for (FrontProductVariants variants : product.getProductVariants()) {
                    List<String> variantValues = new ArrayList<>();
                    variantValues.add(variants.getVariantSku());
                    variantValues.add(productAsJsonString);
                    variantValues.add(RequestContext.getTenantInfo().getAccountId());
                    batchInsertValues.add(variantValues);
                }
            }
            productDetail.add(productAsJsonString);
            productDetail.add(RequestContext.getTenantInfo().getAccountId());
            batchInsertValues.add(productDetail);
        }
        try {
            stockDataImportService.batchInsertForProductDetails(tableName, batchInsertValues);
        } catch (DarbyException e) {
            log.error("error occured while inserting product details in table", e);
        }

    }


}
