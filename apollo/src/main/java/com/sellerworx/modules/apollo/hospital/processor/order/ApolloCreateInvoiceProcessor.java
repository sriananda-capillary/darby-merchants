package com.sellerworx.modules.apollo.hospital.processor.order;

import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloApiResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloInvoiceReqPayload;
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
@Component("ApolloCreateInvoiceProcessor")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloCreateInvoiceProcessor extends DarbyBaseProcessor {


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

        List<ApolloInvoiceReqPayload> invoicesPayload = transformedPayload.getInvoicePayloadList();

        if (CollectionUtils.isNotEmpty(invoicesPayload)) {
            for (ApolloInvoiceReqPayload invoicePayload : invoicesPayload) {
                ApolloApiResponse createInvoiceResponse = apolloOrderService.createInvoice(invoicePayload);

                if (null == createInvoiceResponse || StringUtils.isBlank(createInvoiceResponse.getStatus())) {
                    FieldErrorModel fieldErrorModel = new FieldErrorModel(
                            invoicePayload.getInvoiceNumber(),
                            "unexpected error occurred as invoice response is null for request" + invoicePayload,
                            String.valueOf(ErrorCode.EMPTY.getCode()));
                    log.error("unexpected error occurred as invoice response is null for request: {}",
                              invoicePayload);

                    fieldErrorModelsList.add(fieldErrorModel);
                }
                else if (createInvoiceResponse.getStatus().equalsIgnoreCase("failed")) {
                    if (!createInvoiceResponse.getResponseMessage().startsWith(
                            ApolloUtil.INVOICE_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX)) {
                        log.error("error while creating invoice - {}", createInvoiceResponse);
                        FieldErrorModel fieldErrorModel = new FieldErrorModel(
                                invoicePayload.getInvoiceNumber(),
                                createInvoiceResponse.getResponseMessage(),
                                createInvoiceResponse.getErrors());
                        fieldErrorModelsList.add(fieldErrorModel);
                    }
                }
                batchProcessDetails.setSuccessCount(++successCount);
            }

            int totalCount = batchProcessDetails.getTotalCount();
            batchProcessDetails.setTotalCount(totalCount + invoicesPayload.size());

            batchProcessDetails.setFieldErrorModelList(fieldErrorModelsList);

            ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, batchProcessDetails, exchange);
        } else {
            log.info("skipping invoice creation as payload is empty");
        }
    }
}
