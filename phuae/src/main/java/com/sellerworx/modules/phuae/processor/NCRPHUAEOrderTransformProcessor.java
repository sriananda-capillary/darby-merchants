package com.sellerworx.modules.phuae.processor;

import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformUtil;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.transformer.order.implementations.*;
import com.sellerworx.modules.ncr.processor.NCROrderTransformProcessor;
import com.sellerworx.modules.phuae.transformation.implementation.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("NCRPHUAEOrderTransformProcessor")
@Documented(description = "Transforms PH UAE orders for NCR," +
                          "from this processor we are creating List of type: OrderTransformer interface ",
            inHeaders = {@KeyInfo(comment = "Tenant", name = ExchangeHeaderKeys.TENANT)},
            inBody = @KeyInfo(comment = "The order details"))
public class NCRPHUAEOrderTransformProcessor extends NCROrderTransformProcessor {

    @Autowired
    private OrderDefaultProductTransform orderDefaultProductTransform;

    @Autowired
    private OrderShipDateTransform orderShipDateTransform;

    @Autowired
    private ShippingAndDiscountDataTransform shippingAndDiscountDataTransform;

    @Autowired
    private NCRAddressDataTransform ncrAddressDataTransform;

    @Autowired
    private SwapCrustToPizzaAndRemovePizzaOL swapCrustToPizzaAndRemovePizzaOL;

    @Autowired
    private NCROrderTypeTransform ncrOrderTypeTransform;

    @Autowired
    private NCROrderPaymentTransform ncrOrderPaymentTransform;

    @Autowired
    private NCRHandleItemMappingTransformer ncrHandleItemMappingTransformer;

    @Autowired
    private NCRCustomDataTransform ncrCustomDataTransform;

    @Autowired
    private NCROrderItemDetailsTransform ncrOrderItemDetailsTransform;

    @Autowired
    private ConvertExtraStrengthTo2RegularStrengthTransformer convertExtraStrengthTo2RegularStrengthTransformer;

    @Autowired
    private DealToAlacarteTransformer dealToAlacarteTransformer;

    @Autowired
    private OrderTransformUtil orderTransformUtil;

    @Autowired
    private NCRHandleChildQuantityTransformer ncrHandleChildQuantityTransformer;

    @Override
    public void process(Exchange exchange) throws Exception {
        List<OrderTransformer> transformerList = new ArrayList<>();
        transformerList.add(orderDefaultProductTransform); //handle default products.
        transformerList.add(convertExtraStrengthTo2RegularStrengthTransformer);
        transformerList.add(orderShipDateTransform); // handle shipDate.
        transformerList.add(shippingAndDiscountDataTransform); // handle shipping and discount in the order.
        transformerList.add(ncrAddressDataTransform);
        transformerList.add(ncrOrderTypeTransform);
        transformerList.add(ncrOrderPaymentTransform);
        transformerList.add(swapCrustToPizzaAndRemovePizzaOL);
        transformerList.add(ncrHandleChildQuantityTransformer);
        transformerList.add(ncrHandleItemMappingTransformer);
        transformerList.add(dealToAlacarteTransformer);
        transformerList.add(ncrCustomDataTransform);
        transformerList.add(ncrOrderItemDetailsTransform);

        orderTransformUtil.transformations(transformerList, exchange);
    }
}
