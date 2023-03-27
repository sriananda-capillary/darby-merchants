package com.sellerworx.modules.phuae.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.mj.client.MjCatalogApiClient;
import com.mj.client.frontend.MJFrontEndCatalogApiClient;
import com.ncr.exception.BadRequestException;
import com.ncr.helpers.AddressHelper;
import com.ncr.helpers.CustomerHelper;
import com.ncr.util.NCRUtil;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.core.soap.SoapModel;
import com.sellerworx.darby.entity.*;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.frontend.request.GetProductVariantsAPIParams;
import com.sellerworx.darby.model.Resource;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.service.ResourceSvc;
import com.sellerworx.darby.util.*;
import com.sellerworx.modules.mapping.model.Item;
import com.sellerworx.modules.mapping.model.ItemAlias;
import com.sellerworx.modules.martjack.services.CustomerService;
import com.sellerworx.modules.martjack.services.OrderService;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import com.sellerworx.modules.ncr.processor.NCROrderPush;
import com.sellerworx.modules.ncr.service.ItemService;
import com.sellerworx.modules.ncr.service.SystemService;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import com.sellerworx.modules.ncr.util.NCRItemAliasKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.datacontract.schemas._2004._07.sdm_sdk.*;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.tempuri.UpdateOrder;
import org.tempuri.UpdateOrderResponse;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@Slf4j
//@RunWith(PowerMockRunner.class)
//@PowerMockRunnerDelegate(SpringRunner.class)
//@PowerMockIgnore("javax.management.*")
//@PrepareForTest(CompletePHUAEOrderPushTest.class)
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CompletePHUAEOrderPushTest extends BaseAPITest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ResourceSvc resourceSvc;

    @Autowired
    NCRPHUAEOrderTransformProcessor orderTransformProcessor;

    @Autowired
    NCROrderPush ncrOrderPushProcessor;

    @MockBean
    private CustomerService mjCustomerService;
    @MockBean
    private SystemService systemService;
    @MockBean
    private CustomerHelper ncrCustomerHelper;
    @MockBean
    private AddressHelper addressHelper;
    @MockBean
    private ItemService itemSvc;
    @MockBean
    private MjCatalogApiClient mjCatalogApiClient;
    @MockBean
    private MJFrontEndCatalogApiClient mjFrontEndCatalogApiClient;

    private final String MJ_OREDER_COMPLEX_DEAL = ensureFile("mj_order_complex_deal_single_item.json");
    private final String MJ_OREDER_COMPLEX_DEAL_WITH_SHIPPING_COST = ensureFile(
            "mj_order_complex_deal_single_item_with_shipping_cost.json");
    private final String EXPECTED_OUTPUT_STRING = ensureFile("expected_order.xml");
    private final String EXPECTED_OUTPUT_STRING_WITH_SHIPPING_COST = ensureFile("expected_xml_shipping_cost.xml");
    private final String MJ_CUSTOMER_JSON_FILE = ensureFile("mj_customer_uae.json");
    private final String NCR_CUSTOMER_JSON_FILE = ensureFile("ncr_customer_uae.json");
    private final String NCR_CUSTOMER_ADDRESS_JSON_FILE = ensureFile("ncr_customer_address_uae.json");
    private final String MJ_PRODUCTS_JSON_FILE_NAME = ensureFile("mj_products_uae.json");
    private final String MJ_FRONTEND_BUNDLE_PRODUCT_BY_SKU = ensureFile("mj_frontend_product_by_sku.json");
    private static final String[] ADD_REQUIRED_MAPPING_SKUS = {"NO-UAE-Vggs-MzrlChs"};
    private static final String[] ADD_REQUIRED_MAPPING_V_SKUS = {"UAE-ChknWngs-WngStrtBnOut"};

    private static final String ITEM_ALIAS_VALUE = "123";
    private Exchange exchange;
    private Map<String, String> tenantConfigMap;
    private Order order;
    private JSONObject ncrCustomer = new JSONObject();
    private JSONObject ncrCustomerAddr = new JSONObject();
    private CC_CUSTOMER ccCustomer = new CC_CUSTOMER();
    private static Customer mjCustomer = new Customer();
    Tenant tenant;

    private static final String NCR_CITY_INFO_ALIAS_NAME = "NCR_CITY_INFO";
    private static final String MJ_CITY_ITEM_PREFIX = "MJ_CITY-";
    private static final String AREA_ID = "1/2/3/4";
    private static final String FOUND_GUEST_WEB = "FOUND_GUEST_WEB";
    private static final String NCR_STORE_ID_PICKUP = "6";
    private static final String NCR_ORDER_ID = "123456789";


    private static final String MJ_HOST = "http://www.mj_test_host.com", MJ_MERCHANT_ID = "mj_test_merchant_id",
            MJ_PUBLIC_KEY = "mj_test_public_key", MJ_SHARED_SECRET = "mj_test_shared_secret", LICENCE_KEY = "abc",
            SERVICE_URL = "http://www.testNCR.com";

    private static final String STORE_MENU_TEMPLATE_ID = "12";

    @MockBean
    SOAPConnector soapConnector;

    @Before
    public void initial() {

        mjCustomer = parseFileToEnttity(MJ_CUSTOMER_JSON_FILE, Customer.class);
        ncrCustomer = parseFileToJsonObj(NCR_CUSTOMER_JSON_FILE);
        ncrCustomerAddr = parseFileToJsonObj(NCR_CUSTOMER_ADDRESS_JSON_FILE);

        tenant = getTenant();

        exchange = getExchange(camelContext);

        tenantConfigMap = getTenantConfigMap();
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, getTenant());
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);

        //PowerMockito.mockStatic(Math.class);
        //PowerMockito.when(Math.random()).thenReturn(0.0);

    }

    @Test
    public void complexDealOrderPush() throws Exception {
        order = parseFileToEnttity(MJ_OREDER_COMPLEX_DEAL, Order.class);
        Resource resource = new Resource();
        resource.setFromSystem("MARTJACK");
        resource.setFromSystemResourceId(order.getOrderId());
        resource.setFromSystemStatus(order.getStatus());
        resource.setType("Order");
        resource = resourceSvc.create(resource, getTenant());
        exchange.getIn().setHeader(ExchangeHeaderKeys.RESOURCE, resource);

        String excpectedOutputXMLString = getExpectedOutputXMLString(EXPECTED_OUTPUT_STRING);

        JSONObject products = getJSONObjectFromFile(MJ_PRODUCTS_JSON_FILE_NAME);
        setMockDefaultProduct(products);

        JSONObject frontEndProducts = getJSONObjectFromFile(MJ_FRONTEND_BUNDLE_PRODUCT_BY_SKU);

        order.setOrderDate(OrderService.parseOrderDate(order.getOrderDateStr()));
        exchange.getIn().setBody(order);

        setMJRegUserMockData();
        setNCRGuestUserMockData();
        mapMJSKUsToNCRCode(false);
        mappedLocationRefCode();
        Mockito.doNothing().when(mjCustomerService).customerLoginWithThirdParty(Mockito.any(),
                                                                                Mockito.anyMap());
        Mockito.doReturn(ncrCustomer).when(ncrCustomerHelper).updateCustomer(Mockito.anyMap(),
                                                                             Mockito.eq(ccCustomer),
                                                                             Mockito.eq(false));
        setMockForGetFrontEndProduct(frontEndProducts);

        Answer<?> answer = new Answer<UpdateOrderResponse>() {
            @Override
            public UpdateOrderResponse answer(InvocationOnMock invocation) throws Throwable {
                SoapModel soapModel = invocation.getArgumentAt(0, SoapModel.class);

                UpdateOrder request = (UpdateOrder) soapModel.getRequestObject();
                request.setRequestID("112233445566778899");

                CEntry[] cEntryArr = request.getOrder().getEntries().getCEntry();
                updateDealIdAsPerTestCases(cEntryArr);

                String actualXMLStr = AxisUtil.mapObjectToXML(request,
                                                              soapModel.getRequestQName());
                log.info("actual xml: {}", actualXMLStr);

                XMLUnit.setIgnoreWhitespace(true);
                XMLUnit.setIgnoreAttributeOrder(true);
                try {
                    XMLTestCase xmlTestCase = new XMLTestCase() {
                        @Override
                        public Diff compareXML(InputSource control, InputSource test)
                                throws SAXException, IOException {
                            return super.compareXML(control, test);
                        }
                    };
                    log.info("expected xml is {}", excpectedOutputXMLString);
                    log.info("actual xml is {} ", actualXMLStr);
                    xmlTestCase.assertXMLEqual("comparing xml", excpectedOutputXMLString, actualXMLStr);

                } catch (SAXException | IOException e) {
                    log.error("unable to parse xml error:" + e.getMessage(), e);
                    throw new DarbyException("unable to parese xml: " + e.getMessage(), e);
                }

                UpdateOrderResponse updateOrderResponse = new UpdateOrderResponse();
                updateOrderResponse.setUpdateOrderResult(new BigDecimal(NCR_ORDER_ID));
                ServiceResult sdkResult = new ServiceResult();
                updateOrderResponse.setSDKResult(sdkResult);
                return updateOrderResponse;
            }


            /**
             * TODO use powerMockito to mock Math.random for generating DealId.
             * removing first 3 char of dealId as it is generated by Math.random()
             * @param cEntries
             */
            private void updateDealIdAsPerTestCases(CEntry[] cEntries) {
                for (CEntry cEntry : cEntries) {
                    BigDecimal dealId = cEntry.getDealID();
                    if (null != dealId) {
                        String temp = dealId.toString();
                        temp = "00" + temp;
                        temp = temp.substring(temp.length() - 6);
                        cEntry.setDealID(new BigDecimal(temp));
                    }

                    if (null != cEntry.getEntries()) {
                        CEntry[] childCEntry = cEntry.getEntries().getCEntry();
                        updateDealIdAsPerTestCases(childCEntry);
                    }
                }
            }
        };

        when(soapConnector.callService(Mockito.any(SoapModel.class), Mockito.any())).then(answer);

        orderTransformProcessor.process(exchange);
        log.debug("order after transformation: {}", order);

//        order = (Order) exchange.getIn().getBody();
//
//        ArrayOfCEntry mainCEntries = (ArrayOfCEntry) order.getCustomField(NCRUtil.ORDER_CART_ITEMS);
//        log.debug("maine centries after transformation: {} ", mainCEntries.toString());

        ncrOrderPushProcessor.process(exchange);


    }

    @Test
    public void complexDealOrderPushWithShippingCost() throws Exception {

        Map<String, String> configMap =
                (Map<String, String>) exchange.getIn().getHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP);

        configMap.put(NCRConfigKeys.IS_NCR_PAYMENT_WITH_MULTIPLE_TENDER_ID, "true");
        configMap.put(NCRConfigKeys.IS_NCR_MENU_TEMPLATE_ID_STORE_BASED, "true");


        order = parseFileToEnttity(MJ_OREDER_COMPLEX_DEAL_WITH_SHIPPING_COST, Order.class);
        Resource resource = new Resource();
        resource.setFromSystem("MARTJACK");
        resource.setFromSystemResourceId(order.getOrderId());
        resource.setFromSystemStatus(order.getStatus());
        resource.setType("Order");
        resource = resourceSvc.create(resource, getTenant());
        exchange.getIn().setHeader(ExchangeHeaderKeys.RESOURCE, resource);

        String excpectedOutputXMLString = getExpectedOutputXMLString(EXPECTED_OUTPUT_STRING_WITH_SHIPPING_COST);

        JSONObject products = getJSONObjectFromFile(MJ_PRODUCTS_JSON_FILE_NAME);
        setMockDefaultProduct(products);
        JSONObject frontEndProducts = getJSONObjectFromFile(MJ_FRONTEND_BUNDLE_PRODUCT_BY_SKU);
        setMockForGetFrontEndProduct(frontEndProducts);

        order.setOrderDate(OrderService.parseOrderDate(order.getOrderDateStr()));
        exchange.getIn().setBody(order);

        setMJRegUserMockData();
        setNCRGuestUserMockData();
        mapMJSKUsToNCRCode(true);
        mappedLocationRefCode();
        Mockito.doNothing().when(mjCustomerService).customerLoginWithThirdParty(Mockito.any(),
                                                                                Mockito.anyMap());
        Mockito.doReturn(ncrCustomer).when(ncrCustomerHelper).updateCustomer(Mockito.anyMap(),
                                                                             Mockito.eq(ccCustomer),
                                                                             Mockito.eq(false));
        setMockForGetFrontEndProduct(frontEndProducts);
        setMockForGetStoreForMenuTemplate();

        orderTransformProcessor.process(exchange);

        order = (Order) exchange.getIn().getBody();
        log.info("order after transformation: {}", order);

        ArrayOfCEntry mainCEntries = (ArrayOfCEntry) order.getCustomField(NCRUtil.ORDER_CART_ITEMS);
        log.info(mainCEntries.toString());

        Answer<?> answer = new Answer<UpdateOrderResponse>() {
            @Override
            public UpdateOrderResponse answer(InvocationOnMock invocation) throws Throwable {
                SoapModel soapModel = invocation.getArgumentAt(0, SoapModel.class);

                UpdateOrder request = (UpdateOrder) soapModel.getRequestObject();
                request.setRequestID("112233445566778899");

                CEntry[] cEntryArr = request.getOrder().getEntries().getCEntry();
                updateDealIdAsPerTestCases(cEntryArr);

                String actualXMLStr = AxisUtil.mapObjectToXML(request,
                                                              soapModel.getRequestQName());
                log.info("actual xml: {}", actualXMLStr);
                XMLUnit.setIgnoreWhitespace(true);
                XMLUnit.setIgnoreAttributeOrder(true);
                try {
                    XMLTestCase xmlTestCase = new XMLTestCase() {
                        @Override
                        public Diff compareXML(InputSource control, InputSource test)
                                throws SAXException, IOException {
                            return super.compareXML(control, test);
                        }
                    };
                    log.info("expected xml is {}", excpectedOutputXMLString);
                    log.info("actual xml is {}", actualXMLStr);
                    xmlTestCase.assertXMLEqual("comparing xml", excpectedOutputXMLString.trim(),
                                               actualXMLStr.trim());


                } catch (SAXException | IOException e) {
                    log.error("unable to parse xml error:" + e.getMessage(), e);
                    throw new DarbyException("unable to parese xml: " + e.getMessage(), e);
                }

                UpdateOrderResponse updateOrderResponse = new UpdateOrderResponse();
                updateOrderResponse.setUpdateOrderResult(new BigDecimal(NCR_ORDER_ID));
                ServiceResult sdkResult = new ServiceResult();
                updateOrderResponse.setSDKResult(sdkResult);
                return updateOrderResponse;
            }

            /**
             * TODO use powerMockito to mock Math.random for generating DealId.
             * removing first 3 char of dealId as it is generated by Math.random()
             * @param cEntries
             */
            private void updateDealIdAsPerTestCases(CEntry[] cEntries) {
                for (CEntry cEntry : cEntries) {
                    BigDecimal dealId = cEntry.getDealID();
                    if (null != dealId) {
                        String temp = dealId.toString();
                        temp = "00" + temp;
                        temp = temp.substring(temp.length() - 6);
                        cEntry.setDealID(new BigDecimal(temp));
                    }

                    if (null != cEntry.getEntries()) {
                        CEntry[] childCEntry = cEntry.getEntries().getCEntry();
                        updateDealIdAsPerTestCases(childCEntry);
                    }
                }
            }
        };

        when(soapConnector.callService(Mockito.any(SoapModel.class), Mockito.any())).then(answer);
        ncrOrderPushProcessor.process(exchange);


    }

    private void setMockForGetStoreForMenuTemplate() {
        CC_STORE strRes = new CC_STORE();
        strRes.setSTR_MENU_ID(new BigDecimal(STORE_MENU_TEMPLATE_ID));
        Mockito.when(systemService.getStoreDetail(Mockito.anyString())).thenReturn(strRes);
    }


    private void mapMJSKUsToNCRCode(boolean isMenuTemplateRequired) {
        List dealCategoryIds = Arrays.asList(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.ECOM_DEAL_CATEGORY_IDS, tenantConfigMap)
                             .split(
                                     SymbolUtil.COMMA));

        for (OrderLine orderLine : order.getOrderLines()) {
            ITEM_TYPE type = ITEM_TYPE.MJ_PRODUCT_VARIANT;

            String orderedSku = orderLine.getVariantSku();
            if (StringUtils.isEmpty(orderedSku)) {
                orderedSku = orderLine.getSku();
                type = ITEM_TYPE.MJ_PRODUCT;
            }


            Item item = new Item();
            item.setTenant(tenant);
            item.setAccountId(tenant.getAccountId());
            item.setMasterId(orderedSku);
            item.setName(orderLine.getProductTitle());
            item.setType(type);

            ItemAlias itemAlias = new ItemAlias();
            itemAlias.setAccountId(tenant.getAccountId());
            itemAlias.setTenant(tenant);
            if (isMenuTemplateRequired) {
                itemAlias.setName(
                        NCRItemAliasKeys.NCR_ITEM_ALIAS_NAME + SymbolUtil.UNDERSCORE + STORE_MENU_TEMPLATE_ID);
                itemAlias.setValue(ITEM_ALIAS_VALUE+STORE_MENU_TEMPLATE_ID);
            } else {
                itemAlias.setName(NCRItemAliasKeys.NCR_ITEM_ALIAS_NAME);
                itemAlias.setValue(ITEM_ALIAS_VALUE);
            }


            mapItemsToItemAliasMock(tenant, item, itemAlias);


            if ("UAE-ChknWngs-14pc-WngStrtBnIn-GrlcLme".equalsIgnoreCase(orderedSku)) {
                ItemAlias itemAlias2 = new ItemAlias();
                itemAlias2.setAccountId(tenant.getAccountId());
                itemAlias2.setTenant(tenant);
                if (isMenuTemplateRequired) {
                    itemAlias2.setName(NCRItemAliasKeys.NCR_ITEM_MODIFIER_CODE + SymbolUtil.UNDERSCORE
                            + STORE_MENU_TEMPLATE_ID);
                } else {
                    itemAlias2.setName(NCRItemAliasKeys.NCR_ITEM_MODIFIER_CODE);
                }
                itemAlias2.setValue(ITEM_ALIAS_VALUE + "3");

                mapItemsToItemAliasMock(tenant, item, itemAlias2);

            }

            if (dealCategoryIds.contains(orderLine.getCategoryId())) {
                ItemAlias itemAlias1 = new ItemAlias();
                itemAlias1.setAccountId(tenant.getAccountId());
                itemAlias1.setTenant(tenant);

                if (isMenuTemplateRequired) {
                    itemAlias1.setName(NCRItemAliasKeys.NCR_DEAL_ITEM_ALIAS_NAME + SymbolUtil.UNDERSCORE +
                                       STORE_MENU_TEMPLATE_ID);
                } else {
                    itemAlias1.setName(NCRItemAliasKeys.NCR_DEAL_ITEM_ALIAS_NAME);
                }


                if ("BYANY1GET1FREE".equalsIgnoreCase(orderedSku)) {
                    itemAlias1.setValue("0");
                }
                else {
                    itemAlias1.setValue(ITEM_ALIAS_VALUE + "4");
                }

                mapItemsToItemAliasMock(tenant, item, itemAlias1);
            }
        }
        addRequiredItemMappingAsProduct();
        addRequiredItemMappingAsVariantProduct();
    }

    private void mappedLocationRefCode() {

        buildItemMappings("StoreName", order.getOrderLines().get(0).getLocationRefcode(), NCR_STORE_ID_PICKUP,
                          ITEM_TYPE.MJ_LOCATION_CODE, NCRItemAliasKeys.NCR_STORE_ID);

        Item item = new Item();
        item.setTenant(tenant);
        item.setAccountId(tenant.getAccountId());
        item.setMasterId(order.getPaymentDetails().get(0).getPaymentOption());
        item.setName(order.getPaymentDetails().get(0).getPaymentType());
        item.setType(ITEM_TYPE.MJ_PAYMENT_OPTION);

        ItemAlias itemAlias = new ItemAlias();
        itemAlias.setAccountId(tenant.getAccountId());
        itemAlias.setTenant(tenant);
        itemAlias.setName(NCRItemAliasKeys.NCR_PAYMENT_METHOD_ALIAS_NAME);
        itemAlias.setValue("Cash");

        mapItemsToItemAliasMock(tenant, item, itemAlias);

        ItemAlias itemAlias1 = new ItemAlias();
        itemAlias1.setAccountId(tenant.getAccountId());
        itemAlias1.setTenant(tenant);
        itemAlias1.setName(NCRItemAliasKeys.NCR_PAYMENT_PAY_STATUS_ALIAS);
        itemAlias1.setValue("2");

        mapItemsToItemAliasMock(tenant, item, itemAlias1);

        ItemAlias itemAlias2 = new ItemAlias();
        itemAlias2.setAccountId(tenant.getAccountId());
        itemAlias2.setTenant(tenant);
        itemAlias2.setName(NCRItemAliasKeys.NCR_PAYMENT_SUB_TYPE_ALIAS);
        itemAlias2.setValue("1");

        mapItemsToItemAliasMock(tenant, item, itemAlias2);

        ItemAlias itemAlias3 = new ItemAlias();
        itemAlias3.setAccountId(tenant.getAccountId());
        itemAlias3.setTenant(tenant);
        itemAlias3.setName(NCRItemAliasKeys.NCR_PAYMENT_TENDER_ID_ALIAS);
        itemAlias3.setValue("250");

        mapItemsToItemAliasMock(tenant, item, itemAlias3);

        ItemAlias itemAlias4 = new ItemAlias();
        itemAlias4.setAccountId(tenant.getAccountId());
        itemAlias4.setTenant(tenant);
        itemAlias4.setName(NCRItemAliasKeys.NCR_PAYMENT_TYPE_ALIAS_NAME);
        itemAlias4.setValue("1");

        mapItemsToItemAliasMock(tenant, item, itemAlias4);

    }

    private Map<String, String> getTenantConfigMap() {
        Map<String, String> tenantConfigMap = new HashMap<>();
        tenantConfigMap.put(MJTenantConfigKeys.MJ_TIMEZONE, "Asia/Dubai");
        tenantConfigMap.put(TenantConfigKeys.MJ_PIZZA_PARENT_BUNDLE_PRODUCT_SKU_PREFIX, "UAE-Pz");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_ID, MJ_MERCHANT_ID);
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, MJ_PUBLIC_KEY);
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, MJ_SHARED_SECRET);
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST, MJ_HOST);
        tenantConfigMap.put(MJTenantConfigKeys.MJ_COUNTRY, "United Arab Emirates");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST_POD, "eu");

        tenantConfigMap.put(TenantConfigKeys.MJ_CRUST_SKU_PREFIX, "UAE-Crt");
        tenantConfigMap.put(TenantConfigKeys.IS_DEFAULT_TOPPING_STRENGTH_RGLR, "true");
        tenantConfigMap.put(TenantConfigKeys.MJ_ORDER_PROMISED_DELIVERY_TIME_MINUTES, "30");
        tenantConfigMap.put(TenantConfigKeys.MJ_ORDER_PROMISED_TAKEAWAY_TIME_MINUTES, "15");

        tenantConfigMap.put(NCRConfigKeys.NCR_URL, SERVICE_URL);
        tenantConfigMap.put(NCRConfigKeys.NCR_LICENSE_CODE, LICENCE_KEY);
        tenantConfigMap.put(NCRConfigKeys.NCR_LANGUAGE_NAME, "en");
        tenantConfigMap.put(NCRConfigKeys.NCR_CONCEPT_ID, "5");
        tenantConfigMap.put(NCRConfigKeys.NCR_MENU_TEMPLATE_ID, "4");
        tenantConfigMap.put(NCRConfigKeys.NCR_PAY_STORE_TENANT_ID, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_PAY_SUB_TYPE, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_PAY_STATUS, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_NORMAL_ORDER_TIME_LIMIT, "60");
        tenantConfigMap.put(NCRConfigKeys.NCR_ITEM_ORDR_MODE, "OM_SAVED");
        tenantConfigMap.put(NCRConfigKeys.NCR_COUNTRY_PHONE_CODE, "971");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_ASK_DESC, "F");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_ASK_PRICE, "F");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_CHECK_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_CATEGORY, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_VOID_REASON, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_STEP_MODIFIER, "ncrStepModifier");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_SOURCE, "2");
        tenantConfigMap.put(NCRConfigKeys.NCR_ONLINE_PAYMENT_ID, "1");
        tenantConfigMap.put(NCRConfigKeys.NCR_COD_PAYMENT_ID, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_DEFAULT_COUNTRY_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_DEFAULT_PROVINCE_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_DEFAULT_CITY_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_DEFAULT_AREA_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDR_BUILDING_NAME_LENGTH, "50");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDR_FLOOR_NO_LENGTH, "50");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDR_FLAT_NO_LENGTH, "12");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDR_ROAD_NAME_LENGTH, "100");
        tenantConfigMap.put(NCRConfigKeys.NCR_PHONE_TYPE_LANDLINE, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_PHONE_TYPE_OFFICE, "1");
        tenantConfigMap.put(NCRConfigKeys.NCR_PHONE_TYPE_MOBILE, "2");
        tenantConfigMap.put(NCRConfigKeys.NCR_DEFAULT_DISTRICT_NAME, "Default");
        tenantConfigMap.put(NCRConfigKeys.NCR_PHONE_NUMBER_DEFAULT_VALUE, "84");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_TYPE_FUTURE_CODE, "1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_TYPE_NORMAL_CODE, "0");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_MODE_SHIP, "1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_MODE_PICKUP, "2");
        tenantConfigMap.put(NCRConfigKeys.NCR_UPGRADE_TYPE_CATEGORY_KEY, "upgrade");
        tenantConfigMap.put(NCRConfigKeys.NCR_UPGRADE_CATEGORY_NAME, "upgrade");
        tenantConfigMap.put(NCRConfigKeys.NCR_CUSTOMER_CLASSID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_WADDR_STATUS, "2");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDRESS_TYPE_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDRESS_CLASS_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ADDRESS_BUILDINGTYPE_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_X_LOCATION, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_Y_LOCATION, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_TAXROUNDOFF, "10");
        tenantConfigMap.put(NCRConfigKeys.IS_NCR_PH_INTEGRATION, "false");
        tenantConfigMap.put(NCRConfigKeys.NCR_CUSTOMER_CLASS_NAME, "home");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_CREATED_BY, "WEB");
        tenantConfigMap.put(NCRConfigKeys.ECOM_DEAL_CATEGORY_IDS, "CU00217788");
        tenantConfigMap.put(NCRConfigKeys.NCR_ITEM_LEVEL_CUST_ID, "-1");
        tenantConfigMap.put(NCRConfigKeys.NCR_OFFICE_PHONE_CODE, "971");
        tenantConfigMap.put(NCRConfigKeys.MJ_PRODUCT_ATTRIBUTE_NAME_MODIFIER.get(),"Modifier");
        tenantConfigMap.put("ncr_addr_phoneExtension", "");
        tenantConfigMap.put("ncr_building_number", "");
        tenantConfigMap.put("item_category_id_with_two_variant_prop", "CU00217779");
        tenantConfigMap.put("ncr_creditcard_payment_method", "creditcard_noon");
        tenantConfigMap.put(NCRConfigKeys.NCR_ORDER_MOBILE_NUMBER_SPLIT_LENGTH, "2");
        tenantConfigMap.put("mj_no_add_sku_prefix", "UAE-UAE-Dladon-noadd");
        tenantConfigMap.put("order_total_shipping_round_off_limit", "3");
        return tenantConfigMap;
    }

    private String getExpectedOutputXMLString(String expectedOutputXMLFileName) {
        ensureFile(expectedOutputXMLFileName);
        StringBuilder expectedXMLOrderPushXMLStr = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(expectedOutputXMLFileName)));

            String line;
            while ((line = br.readLine()) != null) {
                expectedXMLOrderPushXMLStr.append(line.trim());
            }
            br.close();
        } catch (FileNotFoundException e) {
            log.error("file note  found" + e.getMessage(), e);
            throw new DarbyException();
        } catch (IOException e) {
            log.error("unexpected error occurd" + e.getMessage(), e);
            throw new DarbyException("unexpected error occurd" + e.getMessage(), e);
        }
        return expectedXMLOrderPushXMLStr.toString();
    }

    private JSONObject getJSONObjectFromFile(String fileName)
            throws JsonParseException, JsonMappingException, IOException {
        File productsJsonFile = new File(ensureFile(fileName));

        FileReader page = new FileReader(productsJsonFile);

        BufferedReader br = new BufferedReader(page);
        String line = "";
        String allLines = "";
        while ((line = br.readLine()) != null) {
            allLines = allLines + line;
        }
        br.close();

        return new JSONObject(allLines);
    }

    private void addRequiredItemMappingAsProduct() {

        for (String removedDefaultItem : ADD_REQUIRED_MAPPING_SKUS) {

            Item item = new Item();
            item.setTenant(tenant);
            item.setAccountId(tenant.getAccountId());
            item.setMasterId(removedDefaultItem);
            item.setName(removedDefaultItem);
            item.setType(ITEM_TYPE.MJ_PRODUCT);

            ItemAlias itemAlias = new ItemAlias();
            itemAlias.setAccountId(tenant.getAccountId());
            itemAlias.setTenant(tenant);
            itemAlias.setName(NCRItemAliasKeys.NCR_ITEM_ALIAS_NAME);
            itemAlias.setValue(ITEM_ALIAS_VALUE + "5");

            mapItemsToItemAliasMock(tenant, item, itemAlias);
        }

    }

    private void addRequiredItemMappingAsVariantProduct() {

        for (String removedDefaultItem : ADD_REQUIRED_MAPPING_V_SKUS) {

            Item item = new Item();
            item.setTenant(tenant);
            item.setAccountId(tenant.getAccountId());
            item.setMasterId(removedDefaultItem);
            item.setName(removedDefaultItem);
            item.setType(ITEM_TYPE.MJ_PRODUCT_VARIANT);

            ItemAlias itemAlias = new ItemAlias();
            itemAlias.setAccountId(tenant.getAccountId());
            itemAlias.setTenant(tenant);
            itemAlias.setName(NCRItemAliasKeys.NCR_ITEM_ALIAS_NAME);
            itemAlias.setValue(ITEM_ALIAS_VALUE + "6");

            mapItemsToItemAliasMock(tenant, item, itemAlias);
        }

    }


    //Mocking..
    private void setMockMethods() throws RemoteException, JsonProcessingException, BadRequestException {

        JSONObject itemJson = new JSONObject();
        itemJson.put("adj1", BigDecimal.valueOf(1.0));
        itemJson.put("adj2", BigDecimal.valueOf(1.0));
        itemJson.put("adj3", BigDecimal.valueOf(1.0));
        itemJson.put("adj4", BigDecimal.valueOf(1.0));
        itemJson.put("adjGroup1", BigDecimal.valueOf(1.0));
        itemJson.put("adjGroup2", BigDecimal.valueOf(1.0));
        itemJson.put("adjGroup3", BigDecimal.valueOf(1.0));
        itemJson.put("adjGroup4", BigDecimal.valueOf(1.0));
        itemJson.put("noun", BigDecimal.valueOf(1.0));
        itemJson.put("subType", BigDecimal.valueOf(1.0));

        Mockito.doReturn(itemJson).when(itemSvc).getNCRItemDetailsJson(Mockito.anyMap(), Mockito.anyInt());
    }

    private void setMJRegUserMockData() throws IOException {

        mjCustomer.setEmail("test123@gmail.com");
        mjCustomer.setMobileNumber("65-801111111");
        mjCustomer.setGuest(false);
        Mockito.doReturn(mjCustomer).when(mjCustomerService).getCustomerInfo(Mockito.eq(order.getUserId()),
                                                                             Mockito.eq(tenantConfigMap));

    }

    private void setMockDefaultProduct(JSONObject products) {
        ResponseEntity<String> productList = new ResponseEntity<String>(products.toString(), HttpStatus.OK);
        Answer<?> answerProductList = new Answer<ResponseEntity<String>>() {

            @Override
            public ResponseEntity<String> answer(InvocationOnMock invocation) throws Throwable {
                return productList;
            }
        };
        when(mjCatalogApiClient
                     .getDefaultBundleItems(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                                            Mockito.anyString(), Mockito.anyString())).then(
                answerProductList);
    }

    private void setMockForGetFrontEndProduct(JSONObject products) {
        ResponseEntity<String> productList = new ResponseEntity<String>(products.toString(), HttpStatus.OK);
        Answer<?> answerProductList = new Answer<ResponseEntity<String>>() {

            @Override
            public ResponseEntity<String> answer(InvocationOnMock invocation) throws Throwable {
                return productList;
            }
        };
        when(mjFrontEndCatalogApiClient.getAllProducts(Mockito.any(MultiValueMap.class))).then(
                answerProductList);


        Answer<?> answerProductVariantList = new Answer<ResponseEntity<String>>() {
            @Override
            public ResponseEntity<String> answer(InvocationOnMock invocation) throws Throwable {
                String responseString = "{\n" +
                                        "  \"resource\": [\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 73047,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 3.5,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 3.5,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Rglr-Sml\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27787,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Small\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27767,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 73015,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 7,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 7,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Ext-Sml\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Extra\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27789,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Small\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27767,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 73039,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 4.5,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 4.5,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Rglr-Mdm\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27787,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Medium\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27769,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 73007,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 9,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 9,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Ext-Mdm\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Extra\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27789,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Medium\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27769,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 73031,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 5.5,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 5.5,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Rglr-Lrg\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27787,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Large\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 3,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27771,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 72999,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 11,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 11,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Ext-Lrg\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Extra\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27789,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Large\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 3,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27771,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 83563,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 4.5,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 4.5,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Rglr-Rglr\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 1,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27787,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 4,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 28307,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "      \"minimumOrderQuantity\": 1,\n" +
                                        "      \"maximumorderquantity\": 0,\n" +
                                        "      \"reservedQuantity\": 0,\n" +
                                        "      \"bulkQuantity\": 1,\n" +
                                        "      \"preOrder\": 0,\n" +
                                        "      \"backOrder\": 1,\n" +
                                        "      \"availability\": 1,\n" +
                                        "      \"variantproductid\": 83543,\n" +
                                        "      \"periodicityType\": \"\",\n" +
                                        "      \"periodicityRange\": \"\",\n" +
                                        "      \"stockAlertQuantity\": 0,\n" +
                                        "      \"stockAvailableDate\": \"\",\n" +
                                        "      \"reOrderStockLevel\": 0,\n" +
                                        "      \"discount\": \"0\",\n" +
                                        "      \"mrp\": 9,\n" +
                                        "      \"rrp\": 0,\n" +
                                        "      \"webPrice\": 9,\n" +
                                        "      \"image\": null,\n" +
                                        "      \"description\": \"\",\n" +
                                        "      \"variantSku\": \"UAE-Mts-GrldChkn-Ext-Rglr\",\n" +
                                        "      \"barcode\": null,\n" +
                                        "      \"showExpired\": false,\n" +
                                        "      \"variantProperties\": [\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Strength\",\n" +
                                        "          \"variantPropertyValue\": \"Extra\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 2,\n" +
                                        "          \"propertyRank\": 2,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 27789,\n" +
                                        "          \"variantPropertyId\": 2605,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        },\n" +
                                        "        {\n" +
                                        "          \"variantPropertyName\": \"Size\",\n" +
                                        "          \"variantPropertyValue\": \"Regular\",\n" +
                                        "          \"imageName\": \"\",\n" +
                                        "          \"referenceCode\": \"\",\n" +
                                        "          \"valueRank\": 4,\n" +
                                        "          \"propertyRank\": 3,\n" +
                                        "          \"isDisplaySwatch\": false,\n" +
                                        "          \"description\": \"\",\n" +
                                        "          \"variantPropertyValueId\": 28307,\n" +
                                        "          \"variantPropertyId\": 2599,\n" +
                                        "          \"variantImageType\": 0\n" +
                                        "        }\n" +
                                        "      ],\n" +
                                        "      \"isDefault\": null,\n" +
                                        "      \"isIncludeBundlePrice\": null,\n" +
                                        "      \"startDate\": \"1900-01-01T00:00:00+05:30\",\n" +
                                        "      \"endDate\": \"1900-01-01T00:00:00+05:30\"\n" +
                                        "    }\n" +
                                        "  ],\n" +
                                        "  \"requestMetadata\": {\n" +
                                        "    \"completedOn\": \"2019-10-01T09:46:07.036+0000\",\n" +
                                        "    \"httpStatus\": \"OK\",\n" +
                                        "    \"executionTimeInMs\": 11,\n" +
                                        "    \"languageCode\": \"en\",\n" +
                                        "    \"entityCount\": 0,\n" +
                                        "    \"currentPageSize\": 100,\n" +
                                        "    \"currentPageOffset\": 0,\n" +
                                        "    \"nextPageOffset\": 100\n" +
                                        "  }\n" +
                                        "}";
                return new ResponseEntity<>(responseString, HttpStatus.OK);
            }
        };
        when(mjFrontEndCatalogApiClient
                     .getProductVariantsByProductId(Mockito.any(GetProductVariantsAPIParams.class),
                                                    Mockito.anyString())).then(answerProductVariantList);
    }

    private void setNCRGuestUserMockData() throws IOException, RemoteException, JsonProcessingException,
                                                  BadRequestException, JAXBException, ClassNotFoundException {

        JSONObject custExistResp = new JSONObject();
        custExistResp.put("value", FOUND_GUEST_WEB);
        Mockito.doReturn(custExistResp).when(ncrCustomerHelper).customerExistsInNcr(Mockito.anyMap(),
                                                                                    Mockito.anyString());
        Mockito.doReturn(ncrCustomerAddr).when(addressHelper).register(Mockito.anyMap(), Mockito.any(),
                                                                       Mockito.any(),
                                                                       Mockito.anyBoolean());

        Mockito.doReturn(ncrCustomer).when(ncrCustomerHelper).create(Mockito.anyMap(), Mockito.anyObject(),
                                                                     Mockito.anyBoolean());

        setMockMethods();
        buildItemMappings("AREA", MJ_CITY_ITEM_PREFIX + order.getShipCityCode(),
                          AREA_ID,
                          ITEM_TYPE.MJ_SHIP_STATE, NCR_CITY_INFO_ALIAS_NAME);
        BigDecimal[] ncrCustIds = {new BigDecimal(1234567)};
        Mockito.doReturn(ncrCustIds).when(ncrCustomerHelper).getCustomerIdsByEmail(Mockito.anyMap(),
                                                                                   Mockito.anyString());
        Mockito.doReturn(ccCustomer).when(ncrCustomerHelper).getCustomerById(Mockito.anyMap(),
                                                                             Mockito.eq(ncrCustIds[0]));

    }
}