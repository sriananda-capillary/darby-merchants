package com.sellerworx.modules.apollo.hospital.processor.order;

import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloApiResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloShipmentReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloTransformedPayload;
import com.sellerworx.modules.apollo.service.ApolloOrderService;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("ApolloCreateShipmentProcessor")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloCreateShipmentProcessor extends DarbyBaseProcessor {

    @Autowired
    ApolloOrderService apolloOrderService;

    @Override
    public void startProcess(Exchange exchange) {

        int successCount = 0;
        List<FieldErrorModel> fieldErrorModelsList = new ArrayList<>();
        BatchProcessDetails batchProcessDetails =
                (BatchProcessDetails) ExchangeHeaderKeys.getValueFromExchangeHeader(
                        ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, exchange, new BatchProcessDetails());
        batchProcessDetails.setFileName(
                (String) ExchangeHeaderKeys.getValueFromExchangeHeader(Exchange.FILE_NAME_CONSUMED,
                                                                       exchange, StringUtils.EMPTY));


        ApolloTransformedPayload transformedPayload = ExchangeUtil.getBody(exchange,
                                                                           ApolloTransformedPayload.class);


        /**
         * creating shipment
         */
        List<ApolloShipmentReqPayload> shipmentsPayload = transformedPayload.getShipmentPayloadList();

        if (CollectionUtils.isNotEmpty(shipmentsPayload)) {

            for (ApolloShipmentReqPayload shipmentPayload : shipmentsPayload) {
                ApolloApiResponse createShipmentResponse = apolloOrderService.createShipment(
                        shipmentPayload);

                if (null == createShipmentResponse || StringUtils.isBlank(createShipmentResponse.getStatus())) {
                    FieldErrorModel fieldErrorModel = new FieldErrorModel(
                            shipmentPayload.getShipmentNumber(),
                            "unexpected error occurred as shipment response is null for request: " + shipmentPayload,
                            String.valueOf(ErrorCode.EMPTY.getCode()));
                    log.error("unexpected error occurred as shipment response is null for request: {}",
                              shipmentPayload);

                    fieldErrorModelsList.add(fieldErrorModel);
                }
                else if (createShipmentResponse.getStatus().equalsIgnoreCase("failed")) {
                    if (!createShipmentResponse.getResponseMessage().startsWith(
                            ApolloUtil.SHIPMENT_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX)) {

                    }
                    log.error("error while creating shipment - {}", createShipmentResponse);
                    FieldErrorModel fieldErrorModel = new FieldErrorModel(
                            shipmentPayload.getShipmentNumber(),
                            createShipmentResponse.getResponseMessage(),
                            createShipmentResponse.getErrors());
                    fieldErrorModelsList.add(fieldErrorModel);
                }

                batchProcessDetails.setSuccessCount(++successCount);
            }

            int totalCount = batchProcessDetails.getTotalCount();
            batchProcessDetails.setTotalCount(totalCount + shipmentsPayload.size());

            batchProcessDetails.setFieldErrorModelList(fieldErrorModelsList);

            ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, batchProcessDetails, exchange);
        } else {
            log.info("skipping shipment creation as payload is empty");
        }
    }
}
