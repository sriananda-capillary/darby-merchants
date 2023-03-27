package com.capillary.spar.processor;

import java.sql.Timestamp;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capillary.spar.service.SparPriceDataImportService;
import com.capillary.spar.service.SparStockDataImportService;
import com.capillary.spar.util.SparStockPriceSyncUtil;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.martjack.util.StockPriceSyncConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("SparLoadStockAndPriceSyncFileDataToTableProcessor")
public class SparLoadStockAndPriceSyncFileDataToTableProcessor extends DarbyBaseProcessor {

    @Autowired
    private SparStockDataImportService stockDataImportService;

    @Autowired
    private SparPriceDataImportService priceDataImportService;

    @Override
    public void startProcess(Exchange exchange) {
        GenericFile genericFile = (GenericFile) ExchangeUtil.getBody(exchange, GenericFile.class);
        String filePath = genericFile.getAbsoluteFilePath();
        String fileName = genericFile.getFileName();
        String syncType = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.SYNC_TYPE,
                                                                                 exchange, true);
        String tableName;
        Timestamp currentTimeMillis = new Timestamp(System.currentTimeMillis());
        if (syncType.equals(StockPriceSyncConstants.PRICE)) {
            tableName = SparStockPriceSyncUtil.PRICE_DUMP_TABLE_PREFIX +
                        SymbolUtil.UNDERSCORE + RequestContext.getTenantInfo().getAccountId();
            priceDataImportService.loadFileDataIntoTable(tableName, filePath, fileName, currentTimeMillis);
        }
        else {
            tableName = SparStockPriceSyncUtil.STOCK_DUMP_TABLE_PREFIX +
                        SymbolUtil.UNDERSCORE + RequestContext.getTenantInfo().getAccountId();
            stockDataImportService.loadFileDataIntoTable(tableName, filePath, fileName, currentTimeMillis);
        }

    }

}
