package com.sellerworx.modules.phuae.transformation.implementation;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.PaymentDetails;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.exception.NotFoundException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ItemAliasKeys;
import com.sellerworx.darby.util.OrderCustomKeys;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.mapping.MappingService;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.util.MJOnlinePaymentUtil;
import com.sellerworx.modules.martjack.util.MJUtil;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import com.sellerworx.modules.ncr.util.NCRItemAliasKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is responsible for handling payment related transformation.
 */
@Slf4j
@Component("NCROrderPaymentTransform")
public class NCROrderPaymentTransform implements OrderTransformer {

    @Autowired
    private MappingService mappingSvc;

    @Override
    public void transform(Exchange exchange, Order order) {

        boolean isPaymentWithMultiTenderId = Boolean.parseBoolean(RequestContext.configString(
                NCRConfigKeys.IS_NCR_PAYMENT_WITH_MULTIPLE_TENDER_ID, "false"));
        String paymentOption = StringUtils.EMPTY;
        String paymentTypeId = StringUtils.EMPTY;
        long tenantId = RequestContext.getTenantInfo().getId();

        List<PaymentDetails> orderPaymentDetailsList = order.getPaymentDetails();
        List<PaymentDetails> orderPaymentCCList = new ArrayList<PaymentDetails>();
        for (PaymentDetails paymentDetail : orderPaymentDetailsList) {
            if (!StringUtils.equalsIgnoreCase(paymentDetail.getPaymentStatus(),
                                              MJUtil.MJ_PAYMENT_STATUS_AUTHORIZED)) {
                log.debug("payment details not sent for {} ", paymentDetail);
                continue;
            }
            if (paymentDetail.getPaymentType().equalsIgnoreCase(MJUtil.MJ_PAYMENT_TYPE_COD)) {
                paymentOption = paymentDetail.getPaymentOption();
                paymentTypeId = RequestContext.configStringEx(NCRConfigKeys.NCR_COD_PAYMENT_ID);
                if (isPaymentWithMultiTenderId) {
                    /**
                     * even for COD we have to pass the tender id
                     * and payment object with empty or configured value
                     */
                    setPaymentFieldsAsPerNCR(paymentDetail, tenantId, paymentOption, true,
                                             isPaymentWithMultiTenderId);
                    orderPaymentCCList.add(paymentDetail);
                    log.info("payment details are changed, updated payment details {} ",
                             orderPaymentCCList);
                    order.setPaymentDetails(orderPaymentCCList);
                }
                else {
                    log.debug("cod setting payment to null");
                    order.setPaymentDetails(null);
                }
                break;
            }
            else {
                log.debug("Payment type: {} ", paymentDetail.getPaymentType());
                paymentOption = paymentDetail.getPaymentOption();
                paymentTypeId = RequestContext.configStringEx(NCRConfigKeys.NCR_ONLINE_PAYMENT_ID);

                setPaymentFieldsAsPerNCR(paymentDetail, tenantId, paymentOption, false,
                                         isPaymentWithMultiTenderId);
                orderPaymentCCList.add(paymentDetail);

                log.info("payment details are changed, updated payment details {} ", orderPaymentCCList);
                order.setPaymentDetails(orderPaymentCCList);
                break;
            }
        }

        String paymentMethod = getPaymentMethod(tenantId, paymentOption);
        order.addCustomField(OrderCustomKeys.NCR_PAYMENT_METHOD, paymentMethod);
        order.addCustomField(OrderCustomKeys.IS_NCR_PAYMENT_ONLINE, Util.convertToInteger(paymentTypeId));
    }

    /**
     * Gets ncr pay type.
     *
     * @param tenantId
     * @param paymentOption the payment option
     * @return the ncr pay type
     */
    protected String getNcrPayType(long tenantId, String paymentOption) {

        String payType = mappingSvc.map(tenantId, paymentOption, ItemAliasKeys.NCR_PAYMENT_TYPE_ALIAS_NAME,
                                        ITEM_TYPE.MJ_PAYMENT_OPTION);

        if (StringUtils.isBlank(payType)) {
            log.error("pay type not found for payment option " + paymentOption);
            throw new NotFoundException("pay type not found for payment option " + paymentOption);
        }

        return payType;
    }


    /**
     * Gets payment method.
     *
     * @param tenantId
     * @param paymentOption the payment option
     * @return the payment method
     */
    protected String getPaymentMethod(long tenantId, String paymentOption) {

        String paymentMethod = mappingSvc.map(tenantId, paymentOption,
                                              ItemAliasKeys.NCR_PAYMENT_METHOD_ALIAS_NAME,
                                              ITEM_TYPE.MJ_PAYMENT_OPTION);

        if (StringUtils.isBlank(paymentMethod)) {
            log.error("payment method not found for payment option " + paymentOption);
            throw new NotFoundException("payment method not found for payment option " + paymentOption);
        }

        return paymentMethod;

    }

    private void setPaymentFieldsAsPerNCR(PaymentDetails paymentDetail, long tenantId, String paymentOption,
                                          boolean isCOD, boolean isPaymentWithMultiTenderId) {

        String payType = getNcrPayType(tenantId, paymentOption);
        paymentDetail.addCustomField(OrderCustomKeys.NCR_PAY_TYPE, Util.convertToInteger(payType));
        paymentDetail.addCustomField("paySubType",
                                     getNcrPaySubType(tenantId, paymentOption, isPaymentWithMultiTenderId));
        paymentDetail.addCustomField("payStatus",
                                     getNcrPayStatus(tenantId, paymentOption, isPaymentWithMultiTenderId));
        paymentDetail.addCustomField("payStoreTenderId", getNcrPayStoreTenderId(tenantId, paymentOption,
                                                                                isPaymentWithMultiTenderId));
        paymentDetail.addCustomField("payReferenceNo", isCOD ?
                                                       StringUtils.EMPTY :
                                                       MJOnlinePaymentUtil.getPaymentReference(paymentDetail));
    }

    /**
     * Gets ncr pay store tender id.
     *
     * @param tenantId
     * @param paymentOption
     * @param isPaymentWithMultiTenderId
     * @return the ncr pay store tender id
     */
    protected int getNcrPayStoreTenderId(long tenantId, String paymentOption,
                                         boolean isPaymentWithMultiTenderId) {

        String payemtTenderId = StringUtils.EMPTY;
        if (isPaymentWithMultiTenderId) {
            payemtTenderId = mappingSvc.map(tenantId, paymentOption,
                                            NCRItemAliasKeys.NCR_PAYMENT_TENDER_ID_ALIAS,
                                            ITEM_TYPE.MJ_PAYMENT_OPTION);
        }
        else {
            payemtTenderId = RequestContext.configStringEx(NCRConfigKeys.NCR_PAY_STORE_TENANT_ID);
        }
        return Util.convertToInteger(payemtTenderId);
    }

    /**
     * Gets ncr pay status.
     *
     * @param tenantId
     * @param paymentOption
     * @param isPaymentDetailsVaries
     * @return
     */
    protected int getNcrPayStatus(long tenantId, String paymentOption,
                                  boolean isPaymentDetailsVaries) {

        String payemtPayStatus = StringUtils.EMPTY;
        if (isPaymentDetailsVaries) {
            payemtPayStatus = mappingSvc.map(tenantId, paymentOption,
                                             NCRItemAliasKeys.NCR_PAYMENT_PAY_STATUS_ALIAS,
                                             ITEM_TYPE.MJ_PAYMENT_OPTION);
        }
        else {
            payemtPayStatus = RequestContext.configStringEx(NCRConfigKeys.NCR_PAY_STATUS);
        }
        return Util.convertToInteger(payemtPayStatus);
    }

    /**
     * Gets ncr pay sub type.
     *
     * @param tenantId
     * @param paymentOption
     * @param isPaymentDetailsVaries
     * @return the ncr pay sub type
     */
    protected int getNcrPaySubType(long tenantId, String paymentOption,
                                   boolean isPaymentDetailsVaries) {
        String payemtSubType = StringUtils.EMPTY;
        if (isPaymentDetailsVaries) {
            payemtSubType = mappingSvc.map(tenantId, paymentOption,
                                           NCRItemAliasKeys.NCR_PAYMENT_SUB_TYPE_ALIAS,
                                           ITEM_TYPE.MJ_PAYMENT_OPTION);
        }
        else {
            payemtSubType = RequestContext.configStringEx(NCRConfigKeys.NCR_PAY_SUB_TYPE);
        }
        return Util.convertToInteger(payemtSubType);
    }


}