package com.sellerworx.modules.apollo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sellerworx.darby.annotation.NoObfuscation;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoObfuscation
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloOrderStatusUpdateRequest {
    @JsonProperty("TenantId")
    private String tenantId;

    @JsonProperty(value = "OrderNumber")
    private String orderId;

    @JsonProperty(value = "CustCode")
    private String custCode;

    @JsonProperty(value = "StoreID")
    private String storeId;

    @JsonProperty(value = "OrderStatus")
    private String orderStatus;

    @JsonProperty(value = "OrderDetails")
    private List<ApolloOrderLine> orderLine;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApolloOrderLine {

        public ApolloOrderLine() {}

        @JsonProperty("Itemcode")
        private String itemcode;

        @JsonProperty("CancelledQty")
        private Double cancelledQty;

        @JsonProperty("OrderStatus")
        private String orderStatus;

        @JsonProperty("Qty")
        private Double qty;

        @JsonProperty("BatchNumber")
        private String batchNumber;

        @JsonProperty("InvoicedQty")
        private Double invoiceQty;
    }
}
