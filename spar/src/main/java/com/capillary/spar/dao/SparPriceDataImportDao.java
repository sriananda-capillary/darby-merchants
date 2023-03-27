package com.capillary.spar.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.stereotype.Component;

import com.broker.model.Value;
import com.capillary.spar.db.dto.SparStockPriceFetchReq;
import com.capillary.spar.model.SparStockAndPriceDataImport;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.dao.JDBCHelpers;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;

import lombok.extern.slf4j.Slf4j;

@Component("SparPriceDataImportDao")
@Slf4j
public class SparPriceDataImportDao implements SparDataImportDao {
    @Autowired
    protected JDBCHelpers jdbcHelpers;

    public int loadFileDataIntoTable(String tableName, String filePath, String fileName,
                                     Timestamp createdAt) {
        int noOfRowsInserted = 0;
        String createdDateStr = TransformUtil.formatDateWithZone(createdAt.getTime(),
                                                                 DatePatternUtil.YYYY_MM_DD_S_HH_MM_SS,
                                                                 ZoneId.of(Util.TIME_ZONE_UTC));
        String query = "LOAD DATA LOCAL INFILE '" + filePath + "'" + " INTO TABLE `" + tableName + "`"
                       + " FIELDS TERMINATED BY ';'" + " ENCLOSED BY '\"'" + " LINES TERMINATED BY '\\n'"
                       + " IGNORE 1 ROWS" + " (sku,@dummy,@dummy,@dummy,locationrefcode,webprice,@dummy,mrp)"
                       + " SET account_id = '" + RequestContext.getTenantInfo().getAccountId() + "',"
                       + " file_name ='" + fileName + "',created_at='" + createdDateStr + "';";
        log.info("loading stock file dump into table for file : {} query : {}", filePath, query);
        noOfRowsInserted = jdbcHelpers.executeUpdateDMLStatement(query);
        log.info("no of rows inserted for file : {} is : {}", filePath, noOfRowsInserted);

        if (noOfRowsInserted < 1) {
            String errorMessage =
                    "error occured while loading file : " + filePath + "  into table: " + tableName;
            log.error(errorMessage);
        }
        return noOfRowsInserted;
    }

    public List<SparStockAndPriceDataImport> fetchRecords(SparStockPriceFetchReq req) {

        String query = "select l.id as id,l.sku as sku,l.locationrefcode as locationrefcode,l.mrp as mrp,"
                       + "l.webprice as webprice,r.product_details,l.file_name as file_name from `" + req
                               .getTableName() + "` l "
                       + "left outer join `" + req.getProductDetailsLookUpTable() + "` r "
                       + "on l.sku=r.sku "
                       + "where (l.mrp is NOT NULL or l.webprice is NOT NULL) And "
                       + "l.locationrefcode in (?" + StringUtils.repeat(",?", req.getLocationCodes()
                                                                                 .size() - 1) + ")  And"
                       + " (created_at between ? and ? ) "
                       + "And (status is NULL OR status in (?" + StringUtils.repeat(",?",
                                                                                    req.getStatusIn().length - 1) + ")) "
                       + " order by l.locationrefcode,l.created_at desc limit " + req.getLimit()
                       + " offset " + req.getOffset() + ";";

        log.info("fetching data for table :{} query : {}", req.getTableName(), query);
        RowMapper<SparStockAndPriceDataImport> rowMapper = new RowMapper<SparStockAndPriceDataImport>() {
            @Override
            public SparStockAndPriceDataImport mapRow(ResultSet rs, int rowNum) throws SQLException {
                SparStockAndPriceDataImport dataImport = new SparStockAndPriceDataImport();
                dataImport.setSku(rs.getString("sku"));
                dataImport.setMrp(rs.getString("mrp"));
                dataImport.setWebPrice(rs.getString("webprice"));
                dataImport.setLocationCode(rs.getString("locationrefcode"));
                dataImport.setProductDetails(rs.getString("product_details"));
                dataImport.setId(rs.getString("id"));
                dataImport.setFileName(rs.getString("file_name"));
                return dataImport;
            }
        };
        Value[] queryParam = buildQueryParamsForFetch(req);

        ResultSetExtractor<List<SparStockAndPriceDataImport>> resultSetExtractor =
                new RowMapperResultSetExtractor(rowMapper);
        return jdbcHelpers.executeQueryDMLStatement(resultSetExtractor, query,
                                                    queryParam);
    }

    @Override
    public JDBCHelpers getJdbcHelpers() {
        return jdbcHelpers;
    }
}
