package com.sellerworx.modules.apollo.service.impl;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.rest.RestModel;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.RestService;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloInvoiceReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloApiResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloShipmentReqPayload;
import com.sellerworx.modules.apollo.service.ApolloApiService;
import com.sellerworx.modules.apollo.service.ApolloOrderService;
import com.sellerworx.modules.apollo.util.ApolloTenantConfigKeys;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Slf4j
@Service("ApolloOrderService")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloOrderServiceImpl extends ApolloApiService implements ApolloOrderService {

    static final String ORDER_ENTITY = "orders";


    @Override
    public ApolloAckOrderResponse acknowledge(ApolloAckOrderReqPayload apolloAckOrderReqPayload) {


        String apolloApiHost = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_HOST);

        String apolloApiAckOrderUri = joinHostAndEndPointEx(apolloApiHost,
                                                            ApolloApiService.ORDER_ACKNOWLEDGE_ENDPOINT);
        RestModel orderAckReqRestModel = getRestModel(apolloApiAckOrderUri, "acknowledge");

        orderAckReqRestModel.setRequestBody(TransformUtil.toJsonEx(apolloAckOrderReqPayload));

        RestService.HttpRespData acknowledgeResponseStr = postReq(orderAckReqRestModel);

        if (acknowledgeResponseStr.getStatusCode() == HttpStatus.OK.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    acknowledgeResponseStr.getBody(), ApolloAckOrderResponse.class);
        }
        else if (acknowledgeResponseStr.getStatusCode() == HttpStatus.BAD_REQUEST.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    acknowledgeResponseStr.getBody(), ApolloAckOrderResponse.class);
        }
        else {
            log.error("unknown error while acknowledging order - {}", acknowledgeResponseStr);
            throw new DarbyException("unknown error while acknowledging order " + acknowledgeResponseStr,
                                     ErrorCode.UNKNOWN);
        }


    }

    @Override
    public ApolloApiResponse createShipment(ApolloShipmentReqPayload apolloShipmentReqPayload) {
        String apolloApiHost = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_HOST);

        String apolloApiShipmentUri = joinHostAndEndPointEx(apolloApiHost, ApolloApiService.CREATE_SHIPMENT_ENDPOINT);

        RestModel createShipmentRestModel = getRestModel(apolloApiShipmentUri, "createShipment");

        createShipmentRestModel.setRequestBody(TransformUtil.toJsonEx(apolloShipmentReqPayload));
        log.info("calling apollo shipment api: {}", createShipmentRestModel);

        RestService.HttpRespData createShipmentResponseStr = postReq(createShipmentRestModel);

        log.info("response apollo shipment: {}", createShipmentResponseStr);

        if (createShipmentResponseStr.getStatusCode() == HttpStatus.OK.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    createShipmentResponseStr.getBody(), ApolloApiResponse.class);
        }
        else if (createShipmentResponseStr.getStatusCode() == HttpStatus.BAD_REQUEST.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    createShipmentResponseStr.getBody(), ApolloApiResponse.class);
        }
        else {
            log.error("unknown error while creating shipment - {}", createShipmentResponseStr);
            throw new DarbyException("unknown error while creating shipment " + createShipmentResponseStr,
                                     ErrorCode.UNKNOWN);
        }
    }

    @Override
    public ApolloApiResponse createInvoice(ApolloInvoiceReqPayload apolloInvoiceReqPayload) {
        String apolloApiHost = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_HOST);

        String apolloApiInvoiceUri = joinHostAndEndPointEx(apolloApiHost, ApolloApiService.CREATE_INVOICE_ENDPOINT);

        RestModel createInvoiceRestModel = getRestModel(apolloApiInvoiceUri, "createInvoice");

        createInvoiceRestModel.setRequestBody(TransformUtil.toJsonEx(apolloInvoiceReqPayload));
        log.info("calling apollo invoice api: {}", createInvoiceRestModel);

        RestService.HttpRespData createInvoiceResponseStr = postReq(createInvoiceRestModel);

        log.info("response apollo invoice: {}", createInvoiceResponseStr);

        if (createInvoiceResponseStr.getStatusCode() == HttpStatus.OK.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    createInvoiceResponseStr.getBody(), ApolloApiResponse.class);
        }
        else if (createInvoiceResponseStr.getStatusCode() == HttpStatus.BAD_REQUEST.value()) {
            return TransformUtil.toObjectFromJsonEx(
                    createInvoiceResponseStr.getBody(), ApolloApiResponse.class);
        }
        else {
            log.error("unknown error while creating invoice - {}", createInvoiceResponseStr);
            throw new DarbyException("unknown error while creating invoice " + createInvoiceResponseStr,
                                     ErrorCode.UNKNOWN);
        }

    }

    @Override
    protected String getEntityName() {
        return ORDER_ENTITY;
    }
}
