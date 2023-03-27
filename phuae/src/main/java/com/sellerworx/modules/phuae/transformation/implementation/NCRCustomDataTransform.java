package com.sellerworx.modules.phuae.transformation.implementation;

import com.ncr.util.NCRUtil;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.Promotions;
import com.sellerworx.darby.util.OrderCustomKeys;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

/**
 * Add custom fields at order level
 */
@Component("NCRCustomDataTransform")
public class NCRCustomDataTransform implements OrderTransformer {

    private static final String MJ_ORDER_ID_NAME = "MJ Order ID: ";
    private static final String CUSTOMER_NOTES_NAME = "Customer notes: ";

    @Override
    public void transform(Exchange exchange, Order order) {
        /*
         * customize order source value across different markets
         */
        order.addCustomField("orderSource",
                             Util.convertToInteger(RequestContext.configStringEx(NCRConfigKeys.NCR_ORDER_SOURCE)));

        order.addCustomField(NCRUtil.ORDER_DISCOUNT_TOTAL, order.getCustomField(OrderCustomKeys.TOTAL_DISCOUNT));
        order.addCustomField(NCRUtil.ORDER_PROMOTION_TOTAL, order.getCustomField(OrderCustomKeys.TOTAL_DISCOUNT));
        order.addCustomField(NCRUtil.ORDER_SHIPPING_CHARGES, order.getCustomField(OrderCustomKeys.TOTAL_SHIPPING_COST));

        /*
         * setting order notes
         */
        order.addCustomField("orderNotes1", CUSTOMER_NOTES_NAME + order.getGiftMessage());
        order.addCustomField("orderNotes2", MJ_ORDER_ID_NAME + order.getOrderId());

        /*
         * setting promotion discounts
         * currently handling only one promotion
         */
        Promotions[] promotions = order.getPromotions();
        if (promotions != null && promotions.length >= 1) {
            order.addCustomField(NCRUtil.ORDER_PROMOTION_ID, promotions[0].getPromotionRefCode());
            order.addCustomField(NCRUtil.ORDER_PROMOTION_NAME, promotions[0].getPromotionTitle());
        }
    }
}
