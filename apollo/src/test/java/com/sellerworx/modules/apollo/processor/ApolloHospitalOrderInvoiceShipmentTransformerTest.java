package com.sellerworx.modules.apollo.processor;

import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.apollo.hospital.dto.ApolloApiResponse;
import com.sellerworx.modules.apollo.hospital.dto.ApolloInvoiceReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloShipmentReqPayload;
import com.sellerworx.modules.apollo.hospital.processor.order.ApolloCreateInvoiceProcessor;
import com.sellerworx.modules.apollo.hospital.processor.order.ApolloCreateShipmentProcessor;
import com.sellerworx.modules.apollo.hospital.processor.transform.ApolloOrderInvoiceAndShipmentTransformer;
import com.sellerworx.modules.apollo.service.ApolloOrderService;
import com.sellerworx.modules.apollo.util.ApolloTenantConfigKeys;
import com.sellerworx.modules.invoice.model.OrderInvoiceInputFeed;
import com.sellerworx.modules.martjack.services.OrderService;
import com.sellerworx.modules.martjack.util.MJExchangeHeaderKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApolloHospitalOrderInvoiceShipmentTransformerTest extends BaseAPITest {
    private static Exchange camelExchange;
    @Autowired
    CamelContext context;
    @Autowired
    ApolloOrderInvoiceAndShipmentTransformer apolloHospitalOrderInvoiceAndShipmentTransformer;

    @Autowired
    ApolloCreateShipmentProcessor apolloCreateShipmentProcessor;

    @Autowired
    ApolloCreateInvoiceProcessor apolloCreateInvoiceProcessor;

    Map<String, String> tenantConfigMap = new HashMap<>();
    @MockBean
    OrderService mjOrderService;

    @MockBean
    ApolloOrderService apolloApiService;

    private String fileName = ensureFile("ApolloInvoiceFile.json");
    private String APOLLO_INVOICE_ECOM_FILE = ensureFile("apollo_invoice_test_order.json");

    private String EXPECTED_APOLLO_HOSPITAL_INVOICE_PAYLOAD = ensureFile("apollo_hospital_invoice_payload.json");
    private String EXPECTED_APOLLO_HOSPITAL_SHIPMENT_PAYLOAD = ensureFile("apollo_hospital_shipment_payload.json");

    @Before
    public void buildExchange() {
        camelExchange = getExchange(context);
        OrderInvoiceInputFeed[] invoiceFeed = parseFileToEnttity(fileName, OrderInvoiceInputFeed[].class);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, fileName);

        camelExchange.getIn().setHeader("apolloOrderSource", "APHOSP");

        ExchangeHeaderKeys.setInHeader(MJExchangeHeaderKeys.ORDER_INVOICE_FILE_FEED,
                                       invoiceFeed, camelExchange);

        tenantConfigMap.put(TenantConfigKeys.MJ_HOST, "https://sg.ecom.capillary.in/DeveloperAPI");
        tenantConfigMap.put("mjTimezone", "Asia/Kolkata");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_ID, "652fb962-8d31-4e10-8ce4-81a133ad566f");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "ARG4A4AZEVEJJIJ7QEX7QSYV");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "TDWKRKXY");
        tenantConfigMap.put(ApolloTenantConfigKeys.APOLLO_API_HOST.get(),
                            "https://service.apollo.net.in/CapillaryUAT");
        tenantConfigMap.put(ApolloTenantConfigKeys.APOLLO_API_USERNAME.get(), "CAPUSER");
        tenantConfigMap.put(ApolloTenantConfigKeys.APOLLO_API_PASSWORD.get(), "Test@123");

        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
    }

    @Test
    public void testCreateShipmentAndInvoiceTransformationWithProcessing() {

        Order ecomOrder = parseFileToEnttity(APOLLO_INVOICE_ECOM_FILE, Order.class);
        Mockito.when(mjOrderService.orderInfoV2(Mockito.anyString())).thenReturn(ecomOrder);

        apolloHospitalOrderInvoiceAndShipmentTransformer.startProcess(camelExchange);

        ApolloApiResponse apiRespose = new ApolloApiResponse();

        apiRespose.setStatus("Success");
        apiRespose.setResponseMessage("test");
        apiRespose.setErrors("No Errors Found");

        Mockito.when(apolloApiService.createShipment(Mockito.any())).thenReturn(apiRespose);
        Mockito.when(apolloApiService.createInvoice(Mockito.any())).thenReturn(apiRespose);

        apolloCreateShipmentProcessor.startProcess(camelExchange);
        apolloCreateInvoiceProcessor.startProcess(camelExchange);

        ArgumentCaptor<ApolloShipmentReqPayload> shipmentApiBody = ArgumentCaptor.forClass(
                ApolloShipmentReqPayload.class);
        ArgumentCaptor<ApolloInvoiceReqPayload> invoiceApiBody = ArgumentCaptor.forClass(
                ApolloInvoiceReqPayload.class);

        Mockito.verify(apolloApiService, Mockito.times(2)).createInvoice(invoiceApiBody.capture());
        Mockito.verify(apolloApiService, Mockito.times(2)).createShipment(shipmentApiBody.capture());

        String finalShipmentPayload = TransformUtil.toJsonEx(shipmentApiBody.getAllValues());
        String finaInvoicePayload = TransformUtil.toJsonEx(invoiceApiBody.getAllValues());

        log.info("shipment :{} , invoice : {}", finalShipmentPayload, finaInvoicePayload);

        JSONArray expectedInvoicePayload = parseFileToJsonArr(EXPECTED_APOLLO_HOSPITAL_INVOICE_PAYLOAD);
        JSONArray expectedShipmentPayload = parseFileToJsonArr(EXPECTED_APOLLO_HOSPITAL_SHIPMENT_PAYLOAD);

        JSONAssert.assertEquals(finaInvoicePayload, expectedInvoicePayload, false);
        JSONAssert.assertEquals(finalShipmentPayload, expectedShipmentPayload, false);

        log.info("apollo invoice and shipment test case is executed successfully.");

    }

    protected JSONArray parseFileToJsonArr(String fileName) {
        return new JSONArray(this.parseFile(fileName));
    }
}
