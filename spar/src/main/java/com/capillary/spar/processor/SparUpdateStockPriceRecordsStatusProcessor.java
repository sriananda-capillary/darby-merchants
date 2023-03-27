package com.capillary.spar.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.capillary.spar.enums.STOCK_PRICE_SYNC_STATUS;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ProductCustomKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.martjack.models.MJUpdateDeltaInventoryModel;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.capillary.spar.model.SparStockAndPriceDataImport;
import com.capillary.spar.service.SparDataImportService;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.util.ExchangeHeaderKeys;

import lombok.extern.slf4j.Slf4j;

@Component("SparUpdateStockPriceRecordsStatusProcessor")
@Slf4j
public class SparUpdateStockPriceRecordsStatusProcessor extends DarbyBaseProcessor {

    @Autowired
    @Qualifier("SparStockDataImportService")
    private SparDataImportService dataImportService;

    @Override
    public void startProcess(Exchange exchange) {

        //total valid records -> api call eligible.
        List<SparStockAndPriceDataImport> validRecords = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_STOCK_PRICE_VALID_RECORDS, exchange, new ArrayList<>());

        // failed records  -> retry required
        List<SparStockAndPriceDataImport> mjAPIfailedRecords = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_STOCK_PRICE_FAILED_RECORDS, exchange, new ArrayList<>());

        // failed records  -> retry not required these products are not present in mj
        List<SparStockAndPriceDataImport> productsNotInMJRecords = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_STOCK_PRICE_INVALID_RECORDS, exchange, new ArrayList<>());

        // ignore records > retry not required
        List<SparStockAndPriceDataImport> ignoredRecords = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_STOCK_PRICE_IGNORED_RECORDS, exchange, new ArrayList<>());

        //for stock api success - main success key
        List<Product> successRecords = ExchangeHeaderKeys.getValueOrOnNull(
                ExchangeHeaderKeys.MJ_GATEWAY_STOCK_AND_PRICE_SUCCESS_LIST, exchange, new ArrayList<>());

        //for price api success
        List<Product> successRecordsFromOtherKeys = ExchangeHeaderKeys.getValueOrOnNull(
                ExchangeHeaderKeys.ECOM_PRICE_LIST_SYNC_VALID_PRODUCTS, exchange, new ArrayList<>());

        /**
         * failed records of API call.
         */
        List<Product> apiFailureRecords = ExchangeHeaderKeys.getValueOrOnNull(
                ExchangeHeaderKeys.MJ_GATEWAY_STOCK_AND_PRICE_FAILURE_LIST, exchange, new ArrayList<>());

        List<Product> priceApiFailureRecords = ExchangeHeaderKeys.getValueOrOnNull(
                ExchangeHeaderKeys.ECOM_PRICE_LIST_SYNC_INVALID_PRODUCTS, exchange, new ArrayList<>());

        List<Product> invalidWeightRecords = (List<Product>) (List<?>)
                ExchangeHeaderKeys.getValueOrOnNull(
                        ExchangeHeaderKeys.PRODUCT_WEIGHT_HANDLER_FAILED_PRODUCTS, exchange,
                        new ArrayList<>());

        log.info("valid records :{},success records for stock :{}," +
                 " success records for price : {}, failed required: {}, api failed records for stock :{}," +
                 " api failed records for price :{}, ignore records: {}, weighted failed records :{}," +
                 " products not found in mj:{}",
                 validRecords.size(), successRecords.size(), successRecordsFromOtherKeys.size(),
                 mjAPIfailedRecords.size(), apiFailureRecords.size(), priceApiFailureRecords.size(),
                 ignoredRecords.size(), invalidWeightRecords.size(), productsNotInMJRecords.size());

        String tableName = ExchangeHeaderKeys.getValueFromExchangeHeader(
                SparExchangeHeaderKeys.STOCK_AND_PRICE_SYNC_TABLE, exchange);

        /**
         * completed records.
         */
        calculateAndUpdateStatus(validRecords, successRecords, successRecordsFromOtherKeys, tableName,
                                 STOCK_PRICE_SYNC_STATUS.COMPLETED.toString());


        /**
         * separate out invalid and failed from API failure.
         */
        List<Product> apiFailureInvalidRecords = new ArrayList<>();

        /**
         * add price api error details
         */
        if (CollectionUtils.isNotEmpty(priceApiFailureRecords)) {
            apiFailureRecords.addAll(priceApiFailureRecords);
        }
        log.info("added price error details: {}, total api error details: {}", priceApiFailureRecords.size(),
                 apiFailureRecords.size());

        Iterator<Product> itr = apiFailureRecords.iterator();
        while (itr.hasNext()) {
            Product product = itr.next();
            if (ErrorCode.BAD_REQUEST.equals(
                    product.getCustomField(ProductCustomKeys.ECOM_STOCK_SYNC_FAIL_ERR_CODE)) || ErrorCode.INVALID.equals(
                    product.getCustomField(ProductCustomKeys.ECOM_STOCK_SYNC_FAIL_ERR_CODE))) {
                apiFailureInvalidRecords.add(product);
                itr.remove();
            }
        }
        log.info("failed records: {}, invalid records: {}", apiFailureRecords.size(),
                 apiFailureInvalidRecords.size());

        List<SparStockAndPriceDataImport> sparFailedRecords =
                getSparStockAndPriceDataImportRecords(validRecords, apiFailureRecords);

        log.info("before transform  failed: {}, after transform :{}", apiFailureRecords.size(),
                 sparFailedRecords.size());

        if (CollectionUtils.isNotEmpty(sparFailedRecords)) {
            mjAPIfailedRecords.addAll(sparFailedRecords);
        }

        /**
         * failed required  -> retry required.
         */
        if (CollectionUtils.isNotEmpty(mjAPIfailedRecords)) {
            log.info("total failed records: {}", mjAPIfailedRecords.size());
            updateStatusInTable(mjAPIfailedRecords, STOCK_PRICE_SYNC_STATUS.FAILED.toString(), tableName);
        }

        /**
         * failed required  -> retry required.
         */
        if (CollectionUtils.isNotEmpty(productsNotInMJRecords)) {
            log.info("records not in mj : {}", productsNotInMJRecords.size());
            updateStatusInTable(productsNotInMJRecords, STOCK_PRICE_SYNC_STATUS.INVALID.toString(),
                                tableName);
        }

        /**
         * invalid records -> retry not required.
         */
        if (CollectionUtils.isNotEmpty(invalidWeightRecords) || CollectionUtils.isNotEmpty(apiFailureInvalidRecords)) {
            calculateAndUpdateStatus(validRecords, invalidWeightRecords, apiFailureInvalidRecords, tableName,
                                     STOCK_PRICE_SYNC_STATUS.INVALID.toString());
        }


        /**
         * ignored required -> retry not
         */
        if (CollectionUtils.isNotEmpty(ignoredRecords)) {
            log.info("total ignore records: {}", ignoredRecords.size());
            updateStatusInTable(ignoredRecords, STOCK_PRICE_SYNC_STATUS.IGNORED.toString(), tableName);
        }

    }

    private void calculateAndUpdateStatus(List<SparStockAndPriceDataImport> validRecords,
                                          List<Product> finalRecords, List<Product> addedRecords,
                                          String tableName, String status) {

        if (CollectionUtils.isNotEmpty(addedRecords)) {
            finalRecords.addAll(addedRecords);
        }

        List<SparStockAndPriceDataImport> sparStockAndPriceDataImportRecords =
                getSparStockAndPriceDataImportRecords(validRecords, finalRecords);

        log.info(
                "marking : {} for total records: {}, from valid spar pojo records: {} and product records: {}",
                status, sparStockAndPriceDataImportRecords.size(), validRecords.size(), finalRecords.size());

        if (CollectionUtils.isNotEmpty(sparStockAndPriceDataImportRecords)) {
            log.info("total records : {}, updated as status: {}", sparStockAndPriceDataImportRecords.size(),
                     status);
            updateStatusInTable(sparStockAndPriceDataImportRecords, status, tableName);
        }
    }

    private void updateStatusInTable(List<SparStockAndPriceDataImport> failedRecords, String status,
                                     String tableName) {
        log.info("updating spar stock and price status update for table {} and status {}", tableName, status);
        List<List<String>> queryParams = new ArrayList<>();
        failedRecords.stream().forEach(dataImport -> {
            List<String> statusAndIdParam = new ArrayList<>(2);
            statusAndIdParam.add(status);
            statusAndIdParam.add(dataImport.getId());
            queryParams.add(statusAndIdParam);
        });
        dataImportService.batchUpdateStatus(tableName, queryParams);
    }

    private List<SparStockAndPriceDataImport> getSparStockAndPriceDataImportRecords(
            List<SparStockAndPriceDataImport> validRecords, List<Product> successRecords) {
        return validRecords.stream()
                           .filter(validRecord -> successRecords
                                   .stream()
                                   .anyMatch(successRecord ->
                                                     (validRecord.getSku().equals(successRecord.getVariantSku())
                                                      || validRecord.getSku().equals(successRecord.getSku()))
                                                     && (validRecord.getLocationCode().equals(successRecord.getCustomField(
                                                             ProductCustomKeys.LOCATION_REF_CODE)))))
                           .collect(Collectors.toList());
    }

}
