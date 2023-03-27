package com.sellerworx.modules.apollo.hospital.processor.order;

import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderResponse;
import com.sellerworx.modules.apollo.service.ApolloOrderService;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ApolloAckOrderProcessor")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloAckOrderProcessor extends DarbyBaseProcessor {

    @Autowired
    ApolloOrderService apolloOrderService;

    @Override
    public void startProcess(Exchange exchange) {

        ApolloAckOrderReqPayload ackOrderReqPayload = ExchangeUtil.getBody(exchange,
                                                                           ApolloAckOrderReqPayload.class);

        ApolloAckOrderResponse ackResponse = apolloOrderService.acknowledge(ackOrderReqPayload);

        if (ackResponse.getStatus().equalsIgnoreCase("failed")) {
            if (!ackResponse.getResponseMessage().startsWith(ApolloUtil.ACK_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX)) {
                log.error("error while acknowledging the order - {}", ackResponse);
                throw new DarbyException("error while acknowledging the order" + ackResponse.getErrors(),
                                         ErrorCode.BAD_REQUEST);
            }
        }
    }
}
