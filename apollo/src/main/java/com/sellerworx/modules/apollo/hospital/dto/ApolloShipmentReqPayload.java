package com.sellerworx.modules.apollo.hospital.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloShipmentReqPayload {

    final String buyerOrderResponseNumber;
    final String sellerOrderResponseNumber;
    final String shipmentNumber;
    final String orderResponseIssueDate;
    final String orderShippedDate;
    final String dcNumber;
    final String dcDate;

    final ApolloBuyerPartyAddressReq buyerPartyAddress;

    final List<ApolloShipmentReqPayload.OrderDetails> orderDetails;

    @Builder
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderDetails {

        final String sku;
        final String itemDescription;
        final Integer orderedQty;
        final String uom;

        final List<ApolloShipmentReqPayload.BatchDetails> batchDetails;
    }

    @Builder
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class BatchDetails {
        final String batchLotNo;
        final Integer shippedQty;
        final String manufacturedDate;
        final String expiryDate;
        final BigDecimal unitPrice;
        final BigDecimal mrp;
    }
}