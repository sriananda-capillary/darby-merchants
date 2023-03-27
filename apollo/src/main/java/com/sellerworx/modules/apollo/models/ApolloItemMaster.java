package com.sellerworx.modules.apollo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sellerworx.darby.annotation.NoObfuscation;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoObfuscation
@ToString
public class ApolloItemMaster {
    @JsonProperty(value = "MLPL_Code")
    private String mpclCode;
    @JsonProperty(value = "Category")
    private String category;
    @JsonProperty(value = "Pack")
    private String pack;
    @JsonProperty(value = "Generic")
    private String generic;
    @JsonProperty(value = "Product")
    private String product;
    @JsonProperty(value = "Manufacturer")
    private String manufacturer;
    @JsonProperty(value = "Division")
    private String division;
    @JsonProperty(value = "HSN")
    private String hsn;
    @JsonProperty(value = "CGST_Per")
    private String cgst_per;
    @JsonProperty(value = "SGST_Per")
    private String sgst_per;
    @JsonProperty(value = "IGST_Per")
    private String igst_per;
    @JsonProperty(value = "MRP")
    private String mrp;
    @JsonProperty(value = "Apollo_Price")
    private String apollo_price;
    @JsonProperty(value = "Trade_Price")
    private String trade_price;
    @JsonProperty(value = "UOM")
    private String uom;
    @JsonProperty(value = "Box_Qty")
    private String box_qty;
    @JsonProperty(value = "Case_Qty")
    private String case_qty;
    @JsonProperty(value = "Apollo_Code")
    private String apollo_code;
    @JsonProperty(value = "Closing_Stock")
    private String closing_stock;
    @JsonProperty(value = "Store_ID")
    private String storeID;
    @JsonProperty(value = "BatchNo")
    private String batchNo;
    @JsonProperty(value = "ExpiryDt")
    private String expiryDate;
    @JsonProperty(value = "PromoCode")
    private String promoCode;
    @JsonProperty(value = "Scheme")
    private String scheme;

}
