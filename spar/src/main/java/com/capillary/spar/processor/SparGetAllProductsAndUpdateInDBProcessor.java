package com.capillary.spar.processor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capillary.spar.service.SparStockDataImportService;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.capillary.spar.util.SparStockPriceSyncUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.frontend.request.APIParams;
import com.sellerworx.darby.frontend.request.GetAllProductsAPIParams;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.martjack.frontend.response.FrontProductVariants;
import com.sellerworx.modules.martjack.frontend.response.Product;
import com.sellerworx.modules.martjack.frontend.services.MJFrontEndCatalogService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("SparGetAllProductsAndUpdateInDBProcessor")
public class SparGetAllProductsAndUpdateInDBProcessor extends DarbyBaseProcessor {

    private static final int PAGE_SIZE = 200;
    private static final int PAGE_OFFSET_LIMIT = 200;
    private static final int DEFAULT_CATALOG_SIZE = 30000;

    @Autowired
    private MJFrontEndCatalogService catalogService;

    @Autowired
    private SparStockDataImportService sparStockImportService;

    @Override
    public void startProcess(Exchange exchange) {
        int catalogSize = SparExchangeHeaderKeys.getValueOrOnNull(SparExchangeHeaderKeys.SPAR_CATALOG_SIZE,
                                                                  exchange, DEFAULT_CATALOG_SIZE);
        List<Product> productList = null;
        int pageOffset = 0;
        GetAllProductsAPIParams apiParams = new GetAllProductsAPIParams();
        apiParams.setIncludeUnavailable(false);
        apiParams.setSkus(new String[0]);
        apiParams.setLanguageCode(APIParams.LANGUAGE_CODE.EN);
        apiParams.setPageOffset(String.valueOf(pageOffset));
        apiParams.setPageSize(String.valueOf(PAGE_SIZE));
        String currentDate = new SimpleDateFormat(DatePatternUtil.YYYYMMDDHHMMSS_FORMAT).format(new Date());
        String lookUpTableName =
                SparStockPriceSyncUtil.LOOK_UP_TABLE_PREFIX + SymbolUtil.UNDERSCORE + RequestContext
                        .getTenantInfo().getId() + SymbolUtil.UNDERSCORE + currentDate;
        log.info("creating look up table with name {}", lookUpTableName);
        sparStockImportService.createLookUpTableForProductDetails(lookUpTableName);

        for (int i = 0; i <= catalogSize; i += PAGE_OFFSET_LIMIT) {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                productList = new ArrayList<>();
                productList = catalogService.getAllProducts(apiParams);
                log.info("getallproducts took {} milliseconds with offset {} and pagesize {}",
                         System.currentTimeMillis() - currentTimeMillis, pageOffset, PAGE_SIZE);
                if (CollectionUtils.isNotEmpty(productList)) {
                    log.info("inserting data to  table with name {}", lookUpTableName);
                    for (Product product : productList) {
                        product.setCategories(null);
                        product.setProductAttributes(null);
                        product.setBundleProductGroups(null);
                    }
                    insertProductDataInDB(productList, lookUpTableName);
                }

            } catch (DarbyException e) {
                log.error("error while calling get all products api with offset and page size", pageOffset,
                          PAGE_SIZE, e);
            }
            pageOffset += PAGE_OFFSET_LIMIT;
            apiParams.setPageOffset(String.valueOf(pageOffset));
        }

        log.info("getallproducts data is fetched and saved in table {}", lookUpTableName);

    }


    private void insertProductDataInDB(List<Product> productList, String tableName) {
        List<List<String>> batchInsertValues = new ArrayList<>();
        for (Product product : productList) {
            List<String> productDetail = new ArrayList<>();
            String productAsJsonString;
            try {
                productAsJsonString = new ObjectMapper().writeValueAsString(product);
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
            } catch (JsonProcessingException e) {
                log.error("error while converting product object to json:{}", product, e);
            }

        }
        try {
            sparStockImportService.batchInsertForProductDetails(tableName, batchInsertValues);
        } catch (DarbyException e) {
            log.error("error occured while inserting product details in table", e);
        }

    }
}
