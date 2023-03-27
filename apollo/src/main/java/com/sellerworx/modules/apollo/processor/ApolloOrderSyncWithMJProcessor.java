package com.sellerworx.modules.apollo.processor;

import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.modules.martjack.enums.ORDER_STATUS;
import com.sellerworx.modules.martjack.processor.MJOrderStatusSyncProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("ApolloOrderSyncWithMJProcessor")
@Documented(description = "get order status and check if order status update is required",
            inHeaders = {@KeyInfo(comment = "Order", name = ExchangeHeaderKeys.ORDER)})
public class ApolloOrderSyncWithMJProcessor extends MJOrderStatusSyncProcessor {

    @Override
    protected String getOrderSubstatusConfigKey() {
        return TenantConfigKeys.POS_TO_MJ_ORDER_SUBSTATUS_JSON;
    }

    @Override
    protected ORDER_STATUS getCorrespondingMJOrderStatus(String posOrderStatus, Map<String, String> tenantConfigMap) {
        ORDER_STATUS correspondingStatusInMJ = null;

        switch (posOrderStatus) {
            case "S":
                correspondingStatusInMJ = ORDER_STATUS.DELIVERED;
                break;

            default:
                break;
        }
        log.info("order status mapped : {}", correspondingStatusInMJ);
        return correspondingStatusInMJ;
    }

    @Override
    protected boolean isOrderStatusUpdateRequired(String status, String mjOrderStatus, String[] statusArray) {
        /* if statusArray is null, it will add substatus or it will pass order status */
        if (statusArray == null) {
            statusArray = new String[] {"SE", "RC", "IP"};
        }

        List<String> ignoreStatus = Arrays.asList("C", "F", "X");

        if (mjOrderStatus.equalsIgnoreCase(status)) {
            return false;
        }

        if(ignoreStatus.contains(mjOrderStatus)) {
            return true;
        }

        int position = 0;
        for (int i = 0; (i < statusArray.length); i++) {
            if (mjOrderStatus.equalsIgnoreCase(statusArray[i])) {
                position = i;
                break;
            }
        }

        for (int i=0; i <= position; i++) {
            if (status.equalsIgnoreCase(statusArray[i])) {
                return false;
            }
        }

        return true;
    }
}
