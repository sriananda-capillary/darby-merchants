package com.sellerworx.modules.apollo.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.rest.model.OrderStatusSyncReqModel;
import com.sellerworx.modules.apollo.models.ApolloOrderStatusUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("ApolloOrderStatusUpdateTransformerProcessor")
@Documented(description = "transform order into order status sync modal",
            inBody = @KeyInfo(comment = "order details"))
public class ApolloOrderStatusUpdateTransformerProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        GenericFile orderFileData = (GenericFile) exchange.getIn().getBody();
        ObjectMapper mapper = new ObjectMapper();
        File storeJsonFile = new File(orderFileData.getAbsoluteFilePath());
        ApolloOrderStatusUpdateRequest[] orderFileDataList =
                mapper.readValue(storeJsonFile, ApolloOrderStatusUpdateRequest[].class);

        List<OrderStatusSyncReqModel> orderStatusList = new ArrayList<>();
        for (ApolloOrderStatusUpdateRequest orderStatusUpdateRequest : orderFileDataList) {
            String orderId = orderStatusUpdateRequest.getOrderId();

            if (StringUtils.isNotBlank(orderId)) {
                OrderStatusSyncReqModel reqBody = new OrderStatusSyncReqModel();
                reqBody.setOrderId(orderId);
                reqBody.setOrderStatus(orderStatusUpdateRequest.getOrderStatus());
                reqBody.setStoreId(StringUtils.EMPTY);
                reqBody.setStoreType(orderStatusUpdateRequest.getStoreId());

                if (orderStatusUpdateRequest.getOrderLine() != null) {
                    List<OrderStatusSyncReqModel.OrderLine> orderLineList = new ArrayList<>();
                    orderStatusUpdateRequest.getOrderLine().forEach(orderLine -> {

                        OrderStatusSyncReqModel.OrderLine orderLineItems =
                                reqBody.new OrderLine(null, orderLine.getItemcode());
                        orderLineItems.setShippedQty(orderLine.getInvoiceQty().intValue());
                        orderLineItems.setCancelledQty(orderLine.getCancelledQty().intValue());
                        orderLineList.add(orderLineItems);

                    });

                    reqBody.setOrderLinesStatusReq(orderLineList);
                }
                orderStatusList.add(reqBody);
            }
        }

        log.info("order status list {}", orderStatusList);
        exchange.getIn().setBody(orderStatusList);
    }
}
