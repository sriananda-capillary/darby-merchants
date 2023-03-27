package com.sellerworx.modules.apollo.enums;

public enum PRICELISTCODE {
    BASE_SALE_PRICE("BSP"), INSTITUTIONAL_SALE_PRICE("ISP"), TRADE_SALE_PRICE("TSP"), DISTRIBUTOR_SALE_PRICE("DSP"),
    DEFAULT_PRICE_LIST("INVALID");

    private String entityType;

    public String getEntityType() {
        return this.entityType;
    }

    PRICELISTCODE(String entityType) {
        this.entityType = entityType;
    }

    public static PRICELISTCODE getFromString(String priceListCode) {
        for (PRICELISTCODE code : PRICELISTCODE.values()) {
            if (priceListCode.equalsIgnoreCase(code.toString()))
                return code;
        }
        return PRICELISTCODE.DEFAULT_PRICE_LIST;
    }

}
