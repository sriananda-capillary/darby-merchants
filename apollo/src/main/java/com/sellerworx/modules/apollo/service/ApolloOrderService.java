package com.sellerworx.modules.apollo.service;

import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloInvoiceReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloApiResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloShipmentReqPayload;

public interface ApolloOrderService {

    ApolloAckOrderResponse acknowledge(ApolloAckOrderReqPayload apolloAckOrderReqPayload);

    ApolloApiResponse createShipment(ApolloShipmentReqPayload apolloShipmentReqPayload);

    ApolloApiResponse createInvoice(ApolloInvoiceReqPayload apolloInvoiceReqPayload);

}
