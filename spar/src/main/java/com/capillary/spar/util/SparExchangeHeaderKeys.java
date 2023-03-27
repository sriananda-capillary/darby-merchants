package com.capillary.spar.util;

import java.util.List;

import com.capillary.spar.model.SparStockAndPriceDataImport;
import com.sellerworx.darby.model.StockPriceDataImport;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.KeyValueUtil;

public class SparExchangeHeaderKeys extends ExchangeHeaderKeys {
    public static final KeyValueUtil IS_EMP_EXIST = new
            KeyValueUtil("is_emp_exist", Boolean.class);
    public static final KeyValueUtil<String> STOCK_AND_PRICE_SYNC_TABLE = new
            KeyValueUtil<>("stockAndPriceSyncTable", String.class);
    public static final KeyValueUtil<String> PRODUCT_DETAILS_LOOK_UP_TABLE = new
            KeyValueUtil<>("productDetailsLookUpTable", String.class);
    public static final KeyValueUtil<List<SparStockAndPriceDataImport>> SPAR_STOCK_PRICE_RECORDS =
            new KeyValueUtil<>("sparStockPriceRecords", List.class);
    public static final KeyValueUtil<List<SparStockAndPriceDataImport>> SPAR_STOCK_PRICE_INVALID_RECORDS =
            new KeyValueUtil<>("sparStockPriceInvalidRecords", List.class);
    public static final KeyValueUtil<List<SparStockAndPriceDataImport>> SPAR_STOCK_PRICE_FAILED_RECORDS =
            new KeyValueUtil<>("sparStockPriceFailedRecords", List.class);
    public static final KeyValueUtil<List<SparStockAndPriceDataImport>> SPAR_STOCK_PRICE_VALID_RECORDS =
            new KeyValueUtil<>("sparStockPriceValidRecords", List.class);
    public static final KeyValueUtil<List<SparStockAndPriceDataImport>> SPAR_STOCK_PRICE_IGNORED_RECORDS =
            new KeyValueUtil<>("sparStockPriceIgnoredRecords", List.class);

    public static final KeyValueUtil<String> STOCK_AND_PRICE_STATUS = new
            KeyValueUtil<>("stockAndPriceStatus", String.class);
    public static final KeyValueUtil<Integer> STOCK_AND_PRICE_FETCH_DURATION_MINUTES = new
            KeyValueUtil<>("sparStockAndPriceFetchDuration", Integer.class);
    public static final KeyValueUtil<Integer> SPAR_DELETE_STOCK_PRICE_RECORDS_TTL_DAYS = new
            KeyValueUtil<>("sparDeleteStockPriceRecordsTtlDays", Integer.class);
    public static final KeyValueUtil<Integer> SPAR_CATALOG_SIZE = new
            KeyValueUtil<>("sparCatalogSize", Integer.class);
    public static final KeyValueUtil<Integer> SPAR_STOCK_SYNC_BATCH_SIZE = new
            KeyValueUtil<>("sparStockSyncBatchSize", Integer.class);
    public static final KeyValueUtil<Integer> SPAR_PRICE_SYNC_BATCH_SIZE = new
            KeyValueUtil<>("sparPriceSyncBatchSize", Integer.class);

}
