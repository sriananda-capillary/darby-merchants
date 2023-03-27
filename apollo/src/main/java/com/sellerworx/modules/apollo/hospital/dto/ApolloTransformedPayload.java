package com.sellerworx.modules.apollo.hospital.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloTransformedPayload {
    List<ApolloInvoiceReqPayload> invoicePayloadList;
    List<ApolloShipmentReqPayload> shipmentPayloadList;
}
