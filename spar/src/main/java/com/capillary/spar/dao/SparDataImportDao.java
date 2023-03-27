package com.capillary.spar.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

import com.broker.model.Value;
import com.broker.model.Value.VALUE_TYPE;
import com.capillary.spar.db.dto.SparStockPriceFetchReq;
import com.capillary.spar.enums.STOCK_PRICE_SYNC_STATUS;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.dao.JDBCHelpers;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;

public interface SparDataImportDao {
    public static final Logger log = LoggerFactory.getLogger(SparDataImportDao.class);

    JDBCHelpers getJdbcHelpers();

    default void createLookUpTableForProductDetails(String tableName) {

        String query = "CREATE TABLE `"
                       + tableName
                       + "` ("
                       + "  `id` int(11) NOT NULL AUTO_INCREMENT,"
                       + "  `sku` varchar(50) NOT NULL,"
                       + "  `product_details` longtext,"
                       + "  `account_id` varchar(255) NOT NULL,"
                       + "  PRIMARY KEY (`id`),"
                       + "  UNIQUE KEY (`sku`),"
                       + "  INDEX (`sku`)"
                       + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        log.info("creating look up  table for mj products: {} query : {}", tableName, query);
        getJdbcHelpers().executeDDLStatement(query);
    }

    default void batchUpsert(String query, List<List<String>> values) {
        log.info("upserting into table for query : {}", query);
        getJdbcHelpers().executeMultipleUpdateDMLStatement(query, values);
    }

    default String getLastCreatedTableName(String tableNamePrefix) {
        String tableName = StringUtils.EMPTY;
        String query = "SELECT Table_Name"
                       + " FROM INFORMATION_SCHEMA.TABLES"
                       + " WHERE Table_Type = 'BASE TABLE' AND Table_Name LIKE '"
                       + tableNamePrefix
                       + "%'"
                       + " ORDER BY create_time DESC LIMIT 1;";
        log.info("last created table with prefix {} query : {}", tableNamePrefix, query);
        RowMapper<String> rowMapper = new RowMapper<String>() {
            @Override
            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getString("Table_Name");
            }
        };

        ResultSetExtractor<List<String>> resultSetExtractor = new RowMapperResultSetExtractor(rowMapper);
        List<String> tableNameList = getJdbcHelpers().executeQueryDMLStatement(resultSetExtractor, query,
                                                                               null);
        if (CollectionUtils.isNotEmpty(tableNameList)) {
            tableName = tableNameList.get(0);
        }
        return tableName;

    }


    default void deleteOldRecordsOfStockAndPrice(String tableName, Timestamp createdAt) {
        List<List<String>> queryParamLists = new ArrayList<>();
        String query = new String("DELETE FROM `" + tableName + "` where (created_at < ?)"
                                  + " AND account_id =?;");
        String createdAtStr = TransformUtil.formatDateWithZone(createdAt.getTime(),
                                                               DatePatternUtil.YYYY_MM_DD_S_HH_MM_SS,
                                                               ZoneId.of(Util.TIME_ZONE_UTC));
        List<String> queryParam = new ArrayList<>();
        queryParam.add(createdAtStr);
        queryParam.add(RequestContext.getTenantInfo().getAccountId());
        queryParamLists.add(queryParam);
        getJdbcHelpers().executeMultipleUpdateDMLStatement(query, queryParamLists);

    }

    default Value[] buildQueryParamsForFetch(SparStockPriceFetchReq req) {
        Value[] queryParam = new Value[req.getLocationCodes().size() + req.getStatusIn().length + 2];
        int index = 0;
        for (String locCode : req.getLocationCodes()) {
            Value locationCode = new Value(index + 1, locCode, VALUE_TYPE.STRING);
            queryParam[index] = locationCode;
            index++;
        }
        String createdAtFromStr = TransformUtil.formatDateWithZone(req.getCreatedAtFrom().getTime(),
                                                                   DatePatternUtil.YYYY_MM_DD_S_HH_MM_SS,
                                                                   ZoneId.of(Util.TIME_ZONE_UTC));
        Value createdAtFromParam = new Value(index + 1, createdAtFromStr, VALUE_TYPE.STRING);
        queryParam[index] = createdAtFromParam;
        index++;
        String createdAtToStr = TransformUtil.formatDateWithZone(req.getCreatedAtTo().getTime(),
                                                                 DatePatternUtil.YYYY_MM_DD_S_HH_MM_SS,
                                                                 ZoneId.of(Util.TIME_ZONE_UTC));
        Value createdAtToParam = new Value(index + 1, createdAtToStr, VALUE_TYPE.STRING);
        queryParam[index] = createdAtToParam;
        index++;

        for (STOCK_PRICE_SYNC_STATUS status : req.getStatusIn()) {
            Value statusParam = new Value(index + 1, status.toString(), VALUE_TYPE.STRING);
            queryParam[index] = statusParam;
            index++;
        }
        log.info("built query params {} from stock and price fetch req", Arrays.toString(queryParam));
        return queryParam;
    }
}
