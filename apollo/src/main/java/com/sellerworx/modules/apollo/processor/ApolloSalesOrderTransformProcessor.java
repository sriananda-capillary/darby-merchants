package com.sellerworx.modules.apollo.processor;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.entity.VendorDetail;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.*;
import com.sellerworx.modules.apollo.util.helper.ApolloHelper;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import com.sellerworx.modules.martjack.services.CustomerService;
import com.sellerworx.modules.martjack.services.MJStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.*;

@Component("ApolloSalesOrderTransformProcessor")
@Documented(description = "Transforms the order object to apollo specific json format",
        inBody = @KeyInfo(type = Order.class, comment = "Order object"),
        outHeaders = @KeyInfo(type = String.class, name = ExchangeHeaderKeys.FILENAME, comment = "output file name"),
        outBody = @KeyInfo(type = JSONArray.class, comment = "transformed apollo specific order data"))
public class ApolloSalesOrderTransformProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloSalesOrderTransformProcessor.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private MJStoreService mjStoreService;

    @Autowired
    private ApolloHelper apolloHelper;

    @Override
    public void process(Exchange exchange) throws Exception {
        Order order = (Order) exchange.getIn().getBody();
        if (order != null) {
            Map<String, List<OrderLine>> maps =
                    Grouper.groupMap(order.getOrderLines(), orderline -> orderline.getVendorId());
            List<JSONObject> orderJsonArray = transformOrderEntity(maps, order, exchange);
            if (CollectionUtils.isNotEmpty(orderJsonArray)) {
                exchange.getIn().setBody(orderJsonArray);
                exchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME,
                        ApolloHelper.getSalesOrderFileName(order.getSourceLocationCode()));
            } else {
                exchange.getIn().setBody(new ArrayList<>());
            }

        } else {
            String errorMessage = "found empty order object from body";
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }

    }

    private List<JSONObject> transformOrderEntity(Map<String, List<OrderLine>> map, Order order, Exchange exchange) {

        List<JSONObject> orderJsonArray = new ArrayList<>();

        Iterator<Map.Entry<String, List<OrderLine>>> itr = map.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, List<OrderLine>> entry= itr.next();

            JSONObject vendorDetail = getVendorDetails(entry.getKey());

            if (vendorDetail != null) {

                JSONObject orderJsonObject = new JSONObject();

                orderJsonObject.put(ApolloUtil.ORDER_NO, order.getOrderId());
                orderJsonObject.put(ApolloUtil.ORDER_TOTAL,
                                    order.getAmountPayable().setScale(2, RoundingMode.HALF_UP).toString());
                if (CollectionUtils.isNotEmpty(order.getPaymentDetails())) {
                    /* multiple payment is not there in Apollo */
                    orderJsonObject.put(ApolloUtil.PAYMENT_TYPE, order.getPaymentDetails().get(0).getPaymentType());
                }

                orderJsonObject.put(ApolloUtil.CUST_CODE, getUserName(order));
                orderJsonObject.put(ApolloUtil.ORDERDATE,
                                    TransformUtil.formatDateWithZone(order.getOrderDate(),
                                                                     DatePatternUtil.MM_DD_YYYY_SLASH_FORMAT,
                                                                     ZoneId.of(RequestContext.configStringEx(
                                                                             TenantConfigKeys.MJ_TIMEZONE))));
                orderJsonObject.put(ApolloUtil.STORE_ID, order.getSourceLocationCode());
                orderJsonObject.put(ApolloUtil.REF_NO, order.getReferenceNo());
                orderJsonObject.put(ApolloUtil.SKU_COUNT, order.getGiftMessage());
                orderJsonObject.put(ApolloUtil.ORDER_FOR, vendorDetail.get(ApolloUtil.VENDOR_NAME));
                orderJsonObject.put(ApolloUtil.SUPPLIER_ORDER_NO,
                                    vendorDetail.get(ApolloUtil.VENDOR_CODE) + "_" + order.getOrderId());

                JSONArray orderDetailsArray = new JSONArray();
                for (OrderLine orderLine : entry.getValue()) {
                    JSONObject orderDetailsJson = new JSONObject();
                    orderDetailsJson.put(ApolloUtil.ITEM, orderLine.getProductTitle());
                    orderDetailsJson.put(ApolloUtil.ITEM_CODE, orderLine.getSku());
                    orderDetailsJson.put(ApolloUtil.SO_MRP,
                                         new BigDecimal(orderLine.getMrp()).setScale(2,
                                                                                     RoundingMode.HALF_UP).toString());
                    orderDetailsJson.put(ApolloUtil.SO_PRICE,
                                         orderLine.getProductPrice().setScale(2, RoundingMode.HALF_UP).toString());
                    orderDetailsJson.put(ApolloUtil.SO_QUANTITY, String.valueOf(Math.round(orderLine.getQuantity())));
                    orderDetailsArray.put(orderDetailsJson);
                }

                orderJsonObject.put(ApolloUtil.ORDER_DETAILS, orderDetailsArray);
                orderJsonArray.add(orderJsonObject);

            } else {
                logger.error("invalid vendors : {}", vendorDetail.get(ApolloUtil.INVALID_VENDORS));
            }
        }

        logger.info("transformed json {} for order {}", orderJsonArray, order.getOrderId());
        return orderJsonArray;
    }

    private JSONObject getVendorDetails(String vendorId) {
        JSONObject vendorDetails = new JSONObject();
        try {
            VendorDetail vendorDetailResponse = mjStoreService.getVendorDetails(vendorId);

            if (StringUtils.isBlank(vendorDetailResponse.getVendorId())) {
                String defaultVendorName =
                        TenantConfigKeys.getValueFromTenantConfig(TenantConfigKeys.DEFAULT_VENDOR_NAME,
                                                                  RequestContext.getConfigs());
                String errMsg =
                        "vendor details not found for vendor id : "
                        + vendorId + ". setting default name : "
                        + defaultVendorName;
                logger.error(errMsg);
                vendorDetails.put(ApolloUtil.VENDOR_NAME, defaultVendorName);
                vendorDetails.put(ApolloUtil.VENDOR_CODE, apolloHelper.getVendorCode(defaultVendorName));
            } else {
                vendorDetails.put(ApolloUtil.VENDOR_NAME, vendorDetailResponse.getVendorName());
                vendorDetails.put(ApolloUtil.VENDOR_CODE, apolloHelper.getVendorCode(vendorDetailResponse.getVendorName()));
            }
        } catch (IOException e) {
            logger.error("error while fetching vendor details : " + e.getMessage(), e);
            return null;
        }
        return vendorDetails;
    }

    private String getUserName(Order order) {
        String userName = order.getUserId();
        try {
            Customer customer = customerService.getCustomerInfo(order.getUserId(), RequestContext.getConfigs());
            if (customer != null && StringUtils.isNotEmpty(customer.getUserName())) {
                userName = customer.getUserName();
            }
        } catch (Exception ex) {
            String errorMessage = "error occured whiile getting customer information for order : "
                                  + order.getOrderId()
                                  + " hence sending userid instead of username : "
                                  + order.getUserId();
            logger.error(errorMessage, ErrorCode.NOTFOUND);
        }
        return userName;
    }
}
