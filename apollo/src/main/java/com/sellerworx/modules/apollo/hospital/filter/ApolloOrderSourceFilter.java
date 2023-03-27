package com.sellerworx.modules.apollo.hospital.filter;

import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.util.ApolloExchangeHeaderKeys;
import com.sellerworx.modules.martjack.util.MJUtil;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component("ApolloOrderSourceFilter")
public class ApolloOrderSourceFilter {

    public boolean isOrderSource(Exchange exchange) {

        Order ecomOrder = ExchangeUtil.getBody(exchange, Order.class);

        String orderSourceFilterValue = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(
                ApolloExchangeHeaderKeys.APOLLO_ORDER_SOURCE, exchange);

        return MJUtil.isOrderAttributeSameAsValue(ecomOrder, "orderSource", orderSourceFilterValue);
    }

}
