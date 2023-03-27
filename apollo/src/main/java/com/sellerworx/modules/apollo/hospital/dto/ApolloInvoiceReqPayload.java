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
public class ApolloInvoiceReqPayload {

    final String buyerOrderResponseNumber;
    final String sellerOrderResponseNumber;
    final String invoiceNumber;
    final String deliveryNoteNumber;
    final String orderResponseIssueDate;
    final String shipmentNo;
    final String orderShippedDate;
    final String invoiceDate;
    final BigDecimal netAmount;
    final BigDecimal totalTax;
    final BigDecimal grossAmount;
    final BigDecimal totalDiscountValue;

    final ApolloBuyerPartyAddressReq buyerPartyAddress;

    final List<OrderDetails> orderDetails;

    @Builder
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderDetails {

        final String sku;
        final String itemDescription;
        final Integer quantityInvoiced;
        final String uom;
        final BigDecimal unitPriceValue;
        final BigDecimal taxPercentage;
        final BigDecimal taxAmount;
        final BigDecimal totalTaxAmount;
        final BigDecimal discountPercentage;
        final BigDecimal discountValue;
        final BigDecimal totalAmount;
        final BigDecimal mrp;
    }
}