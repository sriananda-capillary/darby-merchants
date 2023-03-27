package com.sellerworx.modules.apollo.processor;

import com.mj.client.MJStoreApiClient;
import com.mj.client.MjCustomerApiClient;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.modules.apollo.util.helper.ApolloHelper;
import com.sellerworx.modules.martjack.services.MJService;
import com.sellerworx.modules.martjack.services.OrderService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApolloSalesOrderTransformProcessorTest extends BaseAPITest {

    private static final Logger logger = LoggerFactory.getLogger(ApolloSalesOrderTransformProcessorTest.class);

    @Autowired
    private static CamelContext camelContext;

    @Autowired
    private ApolloSalesOrderTransformProcessor apolloSalesOrderTransformProcessor;

    @Autowired
    private ApolloHelper apolloHelper;

    @MockBean
    private MjCustomerApiClient mjCustomerApiClient;

    @MockBean
    private MJStoreApiClient mjStoreApiClient;

    private static Exchange camelExchange;

    private static String ORDER_INFO_FILE = "apollo_orderinfo.json";
    private static String outputFileName = "apollo_salesorder_output.json";
    private static String customerJson = "apollo_mj_customer.json";
    private static String apolloDetailResponse = "apollo_vendordetail_response.json";
    private static String ciplaDetailResponse = "apollo_cipla_vendordetail_response.json";
    private static String singleVendorOrderDetail = "apollo_orderinfo_samevendor.json";
    private static String singleVendorOrderDetailResponse = "apollo_salesorder_singlevendor_output.json";
    private static String noVendorFoundResponse = "apollo_no_vendor_found_response.json";

    Map<String, String> tenantConfigMap = new HashMap<>();

    private Order order;

    @Before
    public void buildExchange() throws Exception {
        camelExchange = getExchange(camelContext);
        ORDER_INFO_FILE = ensureFile(ORDER_INFO_FILE, "src/test/resources");
        outputFileName = ensureFile(outputFileName, "src/test/resources");
        customerJson = ensureFile(customerJson, "src/test/resources");
        apolloDetailResponse = ensureFile(apolloDetailResponse, "src/test/resources");
        ciplaDetailResponse = ensureFile(ciplaDetailResponse, "src/test/resources");
        singleVendorOrderDetail = ensureFile(singleVendorOrderDetail, "src/test/resources");
        singleVendorOrderDetailResponse = ensureFile(singleVendorOrderDetailResponse, "src/test/resources");
        noVendorFoundResponse = ensureFile(noVendorFoundResponse, "src/test/resources");

        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, getTenant());
        tenantConfigMap.put(TenantConfigKeys.MJ_HOST, "Host");
        tenantConfigMap.put("mjTimezone", "Asia/Kolkata");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_ID, "MerchantId");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "consumerSecret");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "consumerKey");
        tenantConfigMap.put(TenantConfigKeys.DEFAULT_VENDOR_NAME, "Medsmart Logistics Private Limited");
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
    }

    @Test
    public void transformOrderEntity() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                        Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                        Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }

    }

    @Test
    public void shouldSplitTheOrderForDifferentVendors() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                                                       Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                                                       Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        Assert.assertEquals(2, actual.size());
    }

    @Test
    public void testForSameVendor() throws Exception {
        Order order = parseFileToEnttity(singleVendorOrderDetail, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                                                       Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                                                       Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(singleVendorOrderDetailResponse);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }

    }

    @Test
    public void ifVendorNotFound() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject noVendorFoundResponseMsg = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject noVendorFoundResponseJson = parseFileToJsonObj(noVendorFoundResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        noVendorFoundResponseMsg.put("messageCode", MJService.MJAPICODES.API_NO_DATA_CODE.getErrorCode());
        noVendorFoundResponseMsg.put("Message", noVendorFoundResponseJson.get("Message"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> noVendorFoundResponseEntity =
                new ResponseEntity<>(noVendorFoundResponseMsg.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                                                       Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                                                       Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.any()))
                .thenReturn(noVendorFoundResponseEntity);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();

        for(int i=0; i< actual.size(); i++) {
            Assert.assertEquals(actual.get(i).get("OrderFor"), "Medsmart Logistics Private Limited");
        }

    }

    @Test
    public void ifFirstVendorNotFound() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject noVendorFoundResponseMsg = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject noVendorFoundResponseJson = parseFileToJsonObj(noVendorFoundResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        noVendorFoundResponseMsg.put("messageCode", MJService.MJAPICODES.API_NO_DATA_CODE.getErrorCode());
        noVendorFoundResponseMsg.put("Message", noVendorFoundResponseJson.get("Message"));
        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> noVendorFoundResponseEntity =
                new ResponseEntity<>(noVendorFoundResponseMsg.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                                                       Mockito.eq("880e719f-9554-4716-8df6-19632261259d"),
                                                       Mockito.eq("consumerSecret"),
                                                       Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails("241a65ff-6765-4c26-9066-74c44e3b8c85"))
                .thenReturn(noVendorFoundResponseEntity);

        Mockito
                .when(mjStoreApiClient.getVendorDetails("62c35c36-a7af-44ad-9c0e-994c88765cd2"))
                .thenReturn(vendorDetailResponseEntityCipla);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();

        for(int i=0; i< actual.size(); i++) {
            Assert.assertEquals(actual.get(0).get("OrderFor"), "Medsmart Logistics Private Limited");
            Assert.assertEquals(actual.get(0).get("SupplierOrderNo"), "Medsm_2451422");
        }

    }

    @Test
    public void ifSecondVendorNotFound() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject noVendorFoundResponseMsg = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject noVendorFoundResponseJson = parseFileToJsonObj(noVendorFoundResponse);
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        noVendorFoundResponseMsg.put("messageCode", MJService.MJAPICODES.API_NO_DATA_CODE.getErrorCode());
        noVendorFoundResponseMsg.put("Message", noVendorFoundResponseJson.get("Message"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> noVendorFoundResponseEntity =
                new ResponseEntity<>(noVendorFoundResponseMsg.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                                                       Mockito.eq("880e719f-9554-4716-8df6-19632261259d"),
                                                       Mockito.eq("consumerSecret"),
                                                       Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails("241a65ff-6765-4c26-9066-74c44e3b8c85"))
                .thenReturn(vendorDetailResponseEntityApollo);

        Mockito
                .when(mjStoreApiClient.getVendorDetails("62c35c36-a7af-44ad-9c0e-994c88765cd2"))
                .thenReturn(noVendorFoundResponseEntity);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();

        for(int i=0; i< actual.size(); i++) {
            Assert.assertEquals(actual.get(1).get("OrderFor"), "Medsmart Logistics Private Limited");
            Assert.assertEquals(actual.get(1).get("SupplierOrderNo"), "Medsm_2451422");
        }
    }

    @Test
    public void checkNullPaymentOption() throws Exception {
        JSONObject outputJson = new JSONObject();
        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());
        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                        Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                        Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        order.setPaymentDetails(null);
        camelExchange.getIn().setBody(order);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            expected.getJSONObject(i).remove("Payment_Type");
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfOrderObjectIsNull() throws Exception {
        camelExchange.getIn().setBody(null);
        apolloSalesOrderTransformProcessor.process(camelExchange);
    }

    @Test
    public void passUserIdwhenCustomerIsNull() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);

        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                        Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                        Mockito.eq("consumerKey")))
                .thenReturn(null);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            expected.getJSONObject(i).put("CustCode", "880e719f-9554-4716-8df6-19632261259d");
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }
    }

    @Test
    public void passUserIdwhenUserNameIsBlank() throws Exception {
        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject outputJson = new JSONObject();
        JSONObject customerJsonObject = parseFileToJsonObj(customerJson);
        customerJsonObject.put("UserName", "");
        outputJson.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        outputJson.put("Customer", customerJsonObject.toString());

        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);

        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        ResponseEntity<String> customerInfoJson = new ResponseEntity<String>(outputJson.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                        Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                        Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);

        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            expected.getJSONObject(i).put("CustCode", "880e719f-9554-4716-8df6-19632261259d");
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }
    }

    @Test
    public void passUserIdwhenExceptionFromCustomerApi() throws Exception {

        Order order = parseFileToEnttity(ORDER_INFO_FILE, Order.class);
        Date orderDate = OrderService.parseOrderDate("/Date(1546598677000+0530)/");
        order.setOrderDate(orderDate);
        camelExchange.getIn().setBody(order);

        JSONObject vendorDetailJsonCipla = new JSONObject();
        JSONObject vendorDetailJsonApollo = new JSONObject();
        JSONObject apolloVendorDetail = parseFileToJsonObj(apolloDetailResponse);
        JSONObject ciplaVendorDetail = parseFileToJsonObj(ciplaDetailResponse);

        vendorDetailJsonCipla.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonCipla.put("VendorDetails", ciplaVendorDetail.get("VendorDetails"));
        vendorDetailJsonApollo.put("messageCode", MJService.MJAPICODES.API_SUCCESSFUL_MSG_CODE.getErrorCode());
        vendorDetailJsonApollo.put("VendorDetails", apolloVendorDetail.get("VendorDetails"));

        ResponseEntity<String> customerInfoJson = new ResponseEntity<>(parseFile(customerJson), HttpStatus.OK); // MessageCode is missing here hence it will throw an exception

        ResponseEntity<String> vendorDetailResponseEntityCipla =
                new ResponseEntity<>(vendorDetailJsonCipla.toString(), HttpStatus.OK);
        ResponseEntity<String> vendorDetailResponseEntityApollo =
                new ResponseEntity<>(vendorDetailJsonApollo.toString(), HttpStatus.OK);

        Mockito
                .when(mjCustomerApiClient.customerInfo(Mockito.eq("Host"), Mockito.eq("MerchantId"),
                        Mockito.eq("880e719f-9554-4716-8df6-19632261259d"), Mockito.eq("consumerSecret"),
                        Mockito.eq("consumerKey")))
                .thenReturn(customerInfoJson);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("241a65ff-6765-4c26-9066-74c44e3b8c85")))
                .thenReturn(vendorDetailResponseEntityCipla);

        Mockito
                .when(mjStoreApiClient.getVendorDetails(Mockito.eq("62c35c36-a7af-44ad-9c0e-994c88765cd2")))
                .thenReturn(vendorDetailResponseEntityApollo);


        apolloSalesOrderTransformProcessor.process(camelExchange);
        List<JSONObject> actual = (List<JSONObject>) camelExchange.getIn().getBody();
        String fileName = (String) camelExchange.getIn().getHeader(ExchangeHeaderKeys.FILENAME);

        Assert.assertEquals("Medsmart_SO_4797_", fileName.substring(0, 17));
        Assert.assertEquals(17, fileName.length());

        String expectedString = getExpectedJsonArray(outputFileName);
        JSONArray expected = new JSONArray(expectedString);
        for (int i=0; i < expected.length(); i++) {
            expected.getJSONObject(i).put("CustCode", "880e719f-9554-4716-8df6-19632261259d");
            JSONAssert.assertEquals(expected.getJSONObject(i), actual.get(i), JSONCompareMode.STRICT);
        }
    }

    private String getExpectedJsonArray(String filename) {
        return parseFile(filename);
    }
}
