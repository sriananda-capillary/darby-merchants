package com.capillary.spar.db.dto;

import java.sql.Timestamp;
import java.util.Set;

import com.capillary.spar.enums.STOCK_PRICE_SYNC_STATUS;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SparStockPriceFetchReq {
    private final String tableName;
    private int offset;
    private final int limit;
    private final Set<String> locationCodes;
    private String productDetailsLookUpTable;
    private Timestamp createdAtFrom;
    private Timestamp createdAtTo;
    private STOCK_PRICE_SYNC_STATUS[] statusIn;
}
