package com.sellerworx.modules.apollo.processor.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.BaseTest;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.context.RequestInfo;
import com.sellerworx.darby.core.context.TenantContext;
import com.sellerworx.darby.core.context.TenantInfo;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.model.Config;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.util.DatePatternUtil;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.processor.transform.ApolloAckOrderTransformer;
import com.sellerworx.modules.martjack.services.OrderService;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloAckOrderTransformerTest extends BaseTest {


    private static final String ACK_ORDER_PAYLOAD = "apollo_ack_order_payload.json";
    private static final String ECOM_ORDER_FILE = "apollo_ecom_order.json";

    @Autowired
    CamelContext camelContext;

    @Autowired
    ApolloAckOrderTransformer ackOrderTransformer;

    Exchange exchange = getExchange(camelContext);

    @Before
    public void preSetup() {

        setUpTenantContext();
        setUpExchange();

    }

    private void setUpExchange() {

        Order ecomOrder = parseFileToEnttity(ECOM_ORDER_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        ecomOrder.setOrderDate(orderDate);
        ExchangeUtil.setBody(ecomOrder, exchange);

    }

    private void setUpTenantContext() {

        RequestInfo requestInfo = new RequestInfo();
        Tenant tenant = new Tenant();
        List<Config> tenantConfigList = new ArrayList<>();
        Config timeZoneConfig = new Config(MJTenantConfigKeys.MJ_TIMEZONE, "Asia/Kolkata");
        tenantConfigList.add(timeZoneConfig);
        tenant.setConfiguration(tenantConfigList);
        TenantInfo tenantInfo = TenantInfo.fromTenant(tenant);
        requestInfo.setTenantInfo(tenantInfo);
        RequestContext.setContext(requestInfo);

    }

    @Test
    public void shouldTransformCorrectly() throws Exception {

        ackOrderTransformer.startProcess(exchange);

        ApolloAckOrderReqPayload actualTransformation = ExchangeUtil.getBody(exchange,
                                                                             ApolloAckOrderReqPayload.class);
        ObjectMapper objectMapper = new ObjectMapper();
        String actualTransformedJson = objectMapper.writeValueAsString(actualTransformation);

        String expectedAckOrderPayload = parseFile(ACK_ORDER_PAYLOAD).replaceAll("YYYY-MM-DD", TransformUtil
                .formatDateWithZone(Instant.now().toEpochMilli(), DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT,
                                    ZoneId.of(TenantContext.getTimeZone())));
        JSONAssert.assertEquals(expectedAckOrderPayload, actualTransformedJson, true);

    }

    @Test
    public void shouldSetDefaultItemDescription() throws JsonProcessingException {

        Order ecomOrder = ExchangeUtil.getBody(exchange, Order.class);
        for (OrderLine orderLine : ecomOrder.getOrderLines()) {
            orderLine.setProductTitle(StringUtils.EMPTY);
        }

        ackOrderTransformer.startProcess(exchange);

        ApolloAckOrderReqPayload actualTransformation = ExchangeUtil.getBody(exchange,
                                                                             ApolloAckOrderReqPayload.class);
        ObjectMapper objectMapper = new ObjectMapper();
        String actualTransformedJson = objectMapper.writeValueAsString(actualTransformation);

        ApolloAckOrderReqPayload expectedReqPayloadSample = parseFileToEnttity(ACK_ORDER_PAYLOAD,
                                                                               ApolloAckOrderReqPayload.class);

        for (ApolloAckOrderReqPayload.OrderDetails orderDetails : expectedReqPayloadSample.getOrderDetails()) {
            orderDetails.setItemDescription("NA");
        }

        String expectedTransformedJson = objectMapper.writeValueAsString(expectedReqPayloadSample).replaceAll(
                "YYYY-MM-DD", TransformUtil
                        .formatDateWithZone(Instant.now().toEpochMilli(), DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT,
                                            ZoneId.of(TenantContext.getTimeZone())));

        JSONAssert.assertEquals(expectedTransformedJson, actualTransformedJson, true);

    }

}