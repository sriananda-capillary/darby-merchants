package com.sellerworx.modules.phuae.transformation.implementation;

import com.sellerworx.darby.entity.Order;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.util.MJUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;


/**
 * This class is responsible to add custom field based on order type.
 */
@Slf4j
@Component("NCROrderTypeTransform")
public class NCROrderTypeTransform implements OrderTransformer {

    private static final int NCR_ORDER_TYPE_NORMAL = 0;
    private static final int NCR_ORDER_TYPE_FUTURE = 1;

    @Override
    public void transform(Exchange exchange, Order order) {
        /**
         * Set Order Type Normal(0) or Future(1).
         *
        **/
        int orderType = NCR_ORDER_TYPE_FUTURE;
        if (MJUtil.isImmediateOrder(order)) {
            orderType = NCR_ORDER_TYPE_NORMAL;
        }
        log.info("order type is {} for order id: {}", orderType, order.getOrderId());
        order.addCustomField("orderType", orderType);
    }
}
