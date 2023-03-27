package com.sellerworx.modules.apollo.hospital.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Builder
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloAckOrderReqPayload {

    final String buyerOrderResponseNumber;
    final String sellerOrderResponseNumber;
    final String orderResponseIssueDate;
    final String orderReferenceDate;

    final ApolloBuyerPartyAddressReq buyerPartyAddress;

    final List<OrderDetails> orderDetails;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApolloAckOrderReqPayload(@JsonProperty("buyerOrderResponseNumber") String buyerOrderResponseNumber,
                                    @JsonProperty("sellerOrderResponseNumber") String sellerOrderResponseNumber,
                                    @JsonProperty("orderResponseIssueDate") String orderResponseIssueDate,
                                    @JsonProperty("orderReferenceDate") String orderReferenceDate,
                                    @JsonProperty("buyerPartyAddress") ApolloBuyerPartyAddressReq buyerPartyAdrs,
                                    @JsonProperty("orderDetails") List<OrderDetails> orderDetails) {
        this.buyerOrderResponseNumber = buyerOrderResponseNumber;
        this.sellerOrderResponseNumber = sellerOrderResponseNumber;
        this.orderResponseIssueDate = orderResponseIssueDate;
        this.orderReferenceDate = orderReferenceDate;
        this.buyerPartyAddress = buyerPartyAdrs;
        this.orderDetails = orderDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderDetails {

        final String sku;
        String itemDescription = "NA";
        Integer orderedQty;
        Integer acceptedQty;
        String uom = StringUtils.EMPTY;

        public OrderDetails(String sku, String itemDescription) {
            this(sku);
            this.itemDescription = itemDescription;
        }

        @JsonCreator
        public OrderDetails(@JsonProperty("sku") String sku) {
            this.sku = sku;
            this.itemDescription = itemDescription;
        }
    }
}
