package com.capillary.spar.service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.capillary.spar.dao.SparDataImportDao;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;

public interface SparDataImportService {

    SparDataImportDao getDao();

    default void batchInsertForProductDetails(String tableName, List<List<String>> values) {
        String query = "insert into `" + tableName + "` (sku,product_details,account_id) values (?"
                       + StringUtils.repeat(",?", values.get(0).size() - 1) + ");";
        getDao().batchUpsert(query, values);
    }

    default void batchUpdateStatus(String tableName, List<List<String>> values) {
        String updatedDate = TransformUtil.formatDateWithZone(new Date(),
                                                              DatePatternUtil.YYYY_MM_DD_S_HH_MM_SS,
                                                              ZoneId.of(Util.TIME_ZONE_UTC));
        String query =
                "UPDATE `" + tableName + "` SET status = ?,updated_at='" + updatedDate + "' WHERE `id` = ?";
        getDao().batchUpsert(query, values);
    }

    default void createLookUpTableForProductDetails(String tableName) {
        getDao().createLookUpTableForProductDetails(tableName);
    }

    default String getLastCreatedTableName(String tableNamePrefix) {
        return getDao().getLastCreatedTableName(tableNamePrefix);
    }

}
