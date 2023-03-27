package com.capillary.spar.service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capillary.spar.dao.SparDataImportDao;
import com.capillary.spar.dao.SparStockDataImportDao;
import com.capillary.spar.db.dto.SparStockPriceFetchReq;
import com.capillary.spar.model.SparStockAndPriceDataImport;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;

@Component("SparStockDataImportService")
public class SparStockDataImportService implements SparDataImportService {

    @Autowired
    private SparStockDataImportDao stockDataImportDao;

    public void loadFileDataIntoTable(String tableName, String filePath, String fileName,
                                      Timestamp createdDate) {
        stockDataImportDao.loadFileDataIntoTable(tableName, filePath, fileName, createdDate);
    }

    public List<SparStockAndPriceDataImport> fetchAndUpdateRecords(SparStockPriceFetchReq req,
                                                                   String status) {
        List<SparStockAndPriceDataImport> dataImportList = stockDataImportDao.fetchRecords(req);
        List<List<String>> values = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataImportList)) {
            dataImportList.stream().forEach(dataImport -> {
                List<String> value = new ArrayList<>(2);
                value.add(status);
                value.add(dataImport.getId());
                values.add(value);
            });

            batchUpdateStatus(req.getTableName(), values);
        }
        return dataImportList;
    }

    @Override
    public SparDataImportDao getDao() {
        return stockDataImportDao;
    }

}
