package com.sellerworx.modules.apollo.hospital.processor.transform;

import com.sellerworx.darby.core.context.TenantContext;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloBuyerPartyAddressReq;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * It needs {@link Order} object in exchange body.
 * It sets {@link ApolloAckOrderReqPayload} object in exchange body.
 */

@Slf4j
@Component("ApolloAckOrderTransformer")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloAckOrderTransformer extends DarbyBaseProcessor {

    static final String UOM_NOS = "NOS";

    @Override
    public void startProcess(Exchange exchange) {

        Order ecomOrder = ExchangeUtil.getBody(exchange, Order.class);

        ApolloAckOrderReqPayload.ApolloAckOrderReqPayloadBuilder
                ackOrderReqPayloadBuilder = ApolloAckOrderReqPayload.builder();

        String orderResponseIssueDate = getFormattedDateStr(ecomOrder.getOrderDate().toInstant(),
                                                            ZoneId.of(TenantContext.getTimeZone()));

        String orderRefDate = getFormattedDateStr(Instant.now(), ZoneId.of(TenantContext.getTimeZone()));

        List<ApolloAckOrderReqPayload.OrderDetails> orderDetailsList;
        ApolloBuyerPartyAddressReq.ApolloBuyerPartyAddressReqBuilder
                buyerPartyAddressBuilder = ApolloBuyerPartyAddressReq.builder();
        buyerPartyAddressBuilder.buyerPartName(ecomOrder.getShipFirstname() + " " + ecomOrder.getShipLastname())
                                .addressStreet(ecomOrder.getShipAddress1())
                                .addressLocation(ecomOrder.getShipAddress2())
                                .addressArea(getAddressArea(ecomOrder))
                                .city(ecomOrder.getShipCity())
                                .state(ecomOrder.getShipState());

        orderDetailsList = ecomOrder.getOrderLines().stream().map(
                orderLine -> {

                    String variantSkuOrSku = StringUtils.isBlank(orderLine.getVariantSku()) ?
                                             orderLine.getSku() :
                                             orderLine.getVariantSku();
                    ApolloAckOrderReqPayload.OrderDetails orderDetails = new ApolloAckOrderReqPayload.OrderDetails(
                            variantSkuOrSku);
                    orderDetails.setOrderedQty(orderLine.getQuantity().intValue());
                    Double cancelledQty = orderLine.getQuantity() - orderLine.getCancelQuantity();
                    orderDetails.setAcceptedQty(cancelledQty.intValue());
                    if (StringUtils.isNotBlank(orderLine.getProductTitle())) {
                        orderDetails.setItemDescription(orderLine.getProductTitle());
                    }
                    return orderDetails;

                }).collect(Collectors.toList());

        ackOrderReqPayloadBuilder.buyerOrderResponseNumber(ecomOrder.getReferenceNo())
                                 .sellerOrderResponseNumber(ecomOrder.getOrderId())
                                 .orderResponseIssueDate(orderResponseIssueDate)
                                 .orderReferenceDate(orderRefDate)
                                 .buyerPartyAddress(buyerPartyAddressBuilder.build())
                                 .orderDetails(orderDetailsList);

        ExchangeUtil.setBody(ackOrderReqPayloadBuilder.build(), exchange);
    }

    private String getFormattedDateStr(Instant now, ZoneId zoneId) {
        return TransformUtil.formatDateWithZone(now.toEpochMilli(), DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT,
                                                zoneId);
    }

    private String getAddressArea(Order ecomOrder) {
        List<String> details = new ArrayList<>();
        details.add(ecomOrder.getBillAddress1());
        details.add(ecomOrder.getBillAddress2());
        details.add(ecomOrder.getBillCity());
        details.add(ecomOrder.getBillingState());
        details.add(ecomOrder.getBillZIP());

        return StringUtils.join(details, SymbolUtil.PIPE);

    }

}
