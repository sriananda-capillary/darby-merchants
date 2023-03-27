package com.capillary.spar.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capillary.spar.dao.SparDataImportDao;
import com.capillary.spar.dao.SparPriceDataImportDao;
import com.capillary.spar.db.dto.SparStockPriceFetchReq;
import com.capillary.spar.model.SparStockAndPriceDataImport;

@Component("SparPriceDataImportService")
public class SparPriceDataImportService implements SparDataImportService {
    @Autowired
    private SparPriceDataImportDao priceDataImportDao;

    public void loadFileDataIntoTable(String tableName, String filePath, String fileName,
                                      Timestamp createdDate) {
        priceDataImportDao.loadFileDataIntoTable(tableName, filePath, fileName, createdDate);
    }

    public List<SparStockAndPriceDataImport> fetchAndUpdateRecords(SparStockPriceFetchReq req,
                                                                   String status) {
        List<SparStockAndPriceDataImport> dataImportList = priceDataImportDao.fetchRecords(req);
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
        return priceDataImportDao;
    }

}
