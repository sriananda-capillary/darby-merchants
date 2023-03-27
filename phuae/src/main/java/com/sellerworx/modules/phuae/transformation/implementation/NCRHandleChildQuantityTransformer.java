package com.sellerworx.modules.phuae.transformation.implementation;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.util.MJUtil;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import com.sellerworx.modules.ncr.util.NCROrderUtil;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * If Deal/Pizza quantity is more than one then update all children,
 * As child ordered quantity divide by Deal/Pizza parent quantity.
 */
@Component("NCRHandleChildQuantityTransformer")
public class NCRHandleChildQuantityTransformer implements OrderTransformer {
    @Override
    public void transform(Exchange exchange, Order order) {
        /**
         * Get all root level order lines.
         */
        List<OrderLine> grandParentOls = MJUtil.getAllParentOrderLines(order);
        Map<String, String> ncrConfigMap = NCROrderUtil.getNcrConfigMap(RequestContext.getConfigs(), order);
        List dealCategoryIds = Arrays.asList(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.ECOM_DEAL_CATEGORY_IDS, ncrConfigMap).split(
                        SymbolUtil.COMMA));

        grandParentOls.forEach(grandParentOl -> {
            List<OrderLine> parentOls = MJUtil.getOLsOfParentOLID(grandParentOl.getOrderLineId(),
                                                                  order.getOrderLines());

            Double quantity = grandParentOl.getQuantity();

            /**
             * If deal OL having qunatity more than one.
             */
            if (quantity > 1) {

                if (isCrustOrderLine(grandParentOl)) {
                    parentOls.forEach(parentOl -> {
                        parentOl.setQuantity(parentOl.getQuantity() / quantity);
                    });
                }
                else if (dealCategoryIds.contains(grandParentOl.getCategoryId())) {
                    parentOls.forEach(parentOL -> {
                        /*update deal child orderLine quantity*/
                        parentOL.setQuantity(parentOL.getQuantity() / quantity);

                        List<OrderLine> childOLs = MJUtil.getOLsOfParentOLID(parentOL.getOrderLineId(),
                                                                             order.getOrderLines());
                        childOLs.forEach( childOL -> {
                            /*update deal child orderLine quantity*/
                            childOL.setQuantity(childOL.getQuantity() / quantity);
                        });
                    });
                }

            }
        });
    }

    private boolean isCrustOrderLine(OrderLine orderLine) {
        return orderLine.getSku().startsWith(RequestContext.configStringEx(TenantConfigKeys.MJ_CRUST_SKU_PREFIX));
    }
}