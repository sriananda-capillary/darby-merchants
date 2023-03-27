package com.capillary.spar.processor;

import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.capillary.spar.dao.SparDataImportDao;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.capillary.spar.util.SparStockPriceSyncUtil;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.SymbolUtil;

@Component("SparDeleteStockPriceRecordsProcessor")
public class SparDeleteStockPriceRecordsProcessor extends DarbyBaseProcessor {

    public static final int DEFAULT_TTL_DAYS = 1;

    @Autowired
    @Qualifier("SparStockDataImportDao")
    private SparDataImportDao dataImportDao;

    @Override
    public void startProcess(Exchange exchange) {
        int noOfdays = ExchangeHeaderKeys.getValueOrOnNull(
                SparExchangeHeaderKeys.SPAR_DELETE_STOCK_PRICE_RECORDS_TTL_DAYS, exchange, DEFAULT_TTL_DAYS);
        String priceTableName =
                SparStockPriceSyncUtil.PRICE_DUMP_TABLE_PREFIX + SymbolUtil.UNDERSCORE + RequestContext
                        .getTenantInfo().getAccountId();
        String stockTableName =
                SparStockPriceSyncUtil.STOCK_DUMP_TABLE_PREFIX + SymbolUtil.UNDERSCORE + RequestContext
                        .getTenantInfo().getAccountId();
        Timestamp createdAt = new Timestamp(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(noOfdays));
        dataImportDao.deleteOldRecordsOfStockAndPrice(stockTableName, createdAt);
        dataImportDao.deleteOldRecordsOfStockAndPrice(priceTableName, createdAt);

    }

}
