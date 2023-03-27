package com.sellerworx.modules.apollo.service;

import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.context.RequestInfo;
import com.sellerworx.darby.core.context.TenantInfo;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.model.Config;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderResponse;
import com.sellerworx.modules.apollo.hospital.processor.transform.ApolloAckOrderTransformer;
import com.sellerworx.modules.apollo.util.ApolloTenantConfigKeys;
import com.sellerworx.modules.martjack.services.OrderService;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Slf4j
@EnableCaching
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloOrderServiceTest extends BaseAPITest {

    static final String orderFileName = "apollo_orderinfo.json";

    @Autowired
    CamelContext context;

    @Autowired
    ApolloOrderService apolloOrderService;

    @Autowired
    ApolloAckOrderTransformer apolloAckOrderTransformer;

    Exchange exchange;

    @Before
    public void preSetUp() {
//        ensureFile(orderFileName);
        exchange = getExchange(context);

        getTenant();
        buildTenantConfigsForTest();
        RequestInfo reqInfo = new RequestInfo();
        reqInfo.setTenantInfo(TenantInfo.fromTenant(getTenant()));
        RequestContext.setContext(reqInfo);
    }


    @Test
    public void shouldInvokeAckApiWithCorrectPayload() {
        Order ecomOrder = parseFileToEnttity(orderFileName, Order.class);
        int d3igitRandom = new Random().nextInt(899) + 100;
        ecomOrder.setReferenceNo("PO10000" + d3igitRandom);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        ecomOrder.setOrderDate(orderDate);
        ExchangeUtil.setBody(ecomOrder, exchange);
        apolloAckOrderTransformer.startProcess(exchange);

        ApolloAckOrderResponse ackResponse = apolloOrderService.acknowledge(
                ExchangeUtil.getBody(exchange, ApolloAckOrderReqPayload.class));

    }

    @Test
    public void shouldCacheAuth() {
        Order ecomOrder = parseFileToEnttity(orderFileName, Order.class);
        ecomOrder.setReferenceNo("PO10000" + (int) Math.random() * 100);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        ecomOrder.setOrderDate(orderDate);
        ExchangeUtil.setBody(ecomOrder, exchange);
        apolloAckOrderTransformer.startProcess(exchange);

        ApolloAckOrderResponse ackResponse = apolloOrderService.acknowledge(
                ExchangeUtil.getBody(exchange, ApolloAckOrderReqPayload.class));

    }

    private void buildTenantConfigsForTest() {

        List<Config> configList = new ArrayList<>();
        Config config1 = new Config(ApolloTenantConfigKeys.APOLLO_API_HOST.get(),
                                    "https://service.apollo.net.in/CapillaryUAT");
        Config config2 = new Config(ApolloTenantConfigKeys.APOLLO_API_USERNAME.get(), "CAPUSER");
        Config config3 = new Config(ApolloTenantConfigKeys.APOLLO_API_PASSWORD.get(), "Test@123");
        Config config4 = new Config(MJTenantConfigKeys.MJ_TIMEZONE, "Asia/Kolkata");

        configList.add(config4);
        configList.add(config3);
        configList.add(config2);
        configList.add(config1);
        tenant.setConfiguration(configList);
    }

}
