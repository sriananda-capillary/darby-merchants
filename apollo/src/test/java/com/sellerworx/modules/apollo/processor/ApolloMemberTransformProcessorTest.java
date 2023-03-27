package com.sellerworx.modules.apollo.processor;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.mj.client.MJStoreApiClient;
import com.mj.client.MJStoreApiClientImpl;
import com.mj.client.MjCustomerApiClient;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.modules.apollo.util.ApolloUtil;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApolloMemberTransformProcessorTest extends BaseAPITest {

    @Autowired
    private CamelContext camelContext;

    private Exchange camelExchange;

    @MockBean
    private MjCustomerApiClient mjCustomerApiClient;

    @Autowired
    ApolloMemberTransformProcessor apolloMemberTransformProcessor;

    @MockBean
    private MJStoreApiClient mjStoreAPIClient;

    private Map<String, String> tenantConfigMap = new HashMap<String, String>();

    private static final String JSON_CUSTOMER_SEARCH_RESPONSE = "apollo_membership_customer_search_response.json";
    private static final String JSON_CUSTOMER_CREATE_RESPONSE = "apollo_membership_customer_create_response.json";
    private static final String JSON_CUSTOMER_ADD_TO_USERGROUP_SUCCESS = "apollo_customer_usergroup_success.json";
    private static final String JSON_CUSTOMER_ADD_TO_USERGROUP_FAILED = "apollo_customer_usergroup_failed.json";
    private static final String JSON_GET_CITIES_SUCCESS = "apollo_cities_list.json";

    @Before
    public void buildExchange() {
        tenant = getTenant();
        ensureFile(JSON_CUSTOMER_SEARCH_RESPONSE);
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_ID, "merchantID");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "consumerkey");
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "secretkey");
        tenantConfigMap.put(TenantConfigKeys.MJ_HOST, "host");
        camelExchange = getExchange(camelContext);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, "medsmart_member.json");
        camelExchange.getIn().setHeader(ApolloUtil.DEFAULT_PASSWORD, "123456");
        JSONObject customer = prepareCustomerObject();
        camelExchange.getIn().setBody(customer);
        buildApolloCustomerUserGroupMappings();
        buildApolloStateCodeMappings();
    }

    public void mockSearchCustomerAPIResponseForNewMemberCreation() {
        ResponseEntity<String> response = new ResponseEntity<>(null, HttpStatus.OK);
        Mockito
                .when(mjCustomerApiClient.searchCustomer(Mockito.eq("host"), Mockito.eq("merchantID"),
                        Mockito.eq("secretkey"), Mockito.eq("consumerkey"), Mockito.any()))
                .thenReturn(response);
    }

    public void mockSearchCustomerAPIResponseForUpdateMemberCreation() {
        String responseJson = getCustomerSearchJsonResponse();
        ResponseEntity<String> response = new ResponseEntity<String>(responseJson, HttpStatus.OK);
        Mockito
                .when(mjCustomerApiClient.searchCustomer(Mockito.eq("host"), Mockito.eq("merchantID"),
                        Mockito.eq("secretkey"), Mockito.eq("consumerkey"), Mockito.any()))
                .thenReturn(response);
    }

    public void mockCreateCustomerAPIResponse() {
        String responseJson = getCustomerCreateJsonResponse();
        ResponseEntity<String> response = new ResponseEntity<String>(responseJson, HttpStatus.OK);
        Mockito.when(mjCustomerApiClient.createCustomer(Mockito.any())).thenReturn(response);

    }

    public void mockAddUserToUserGroupSuccessResponse() {
        String responseJson = getAddUserToUserGroupSuccessResponse();
        ResponseEntity<String> response = new ResponseEntity<String>(responseJson, HttpStatus.OK);
        Mockito.when(mjCustomerApiClient.addUserToUserGroup(Mockito.any(), Mockito.any())).thenReturn(response);
    }

    private String getAddUserToUserGroupSuccessResponse() {
        return parseFileToJsonObj(JSON_CUSTOMER_ADD_TO_USERGROUP_SUCCESS).toString();
    }

    public void mockAddUserToUserGroupFailedResponse() {
        String responseJson = getAddUserToUserGroupFailedResponse();
        ResponseEntity<String> response = new ResponseEntity<String>(responseJson, HttpStatus.OK);
        Mockito.when(mjCustomerApiClient.addUserToUserGroup(Mockito.any(), Mockito.any())).thenReturn(response);

    }

    private String getAddUserToUserGroupFailedResponse() {
        return parseFileToJsonObj(JSON_CUSTOMER_ADD_TO_USERGROUP_FAILED).toString();
    }

    public void mockGetCitiesWithStateSuccessResponse() throws IOException {
        String responseJson = getCitiesByStateCodeSuccessResponse();
        ResponseEntity<String> response = new ResponseEntity<String>(responseJson, HttpStatus.OK);
        Mockito.when(mjStoreAPIClient.cityInfoByStateCode(Mockito.any(), Mockito.any())).thenReturn(response);
    }

    private String getCitiesByStateCodeSuccessResponse() {
        return parseFileToJsonObj(JSON_GET_CITIES_SUCCESS).toString();
    }

    public JSONObject prepareCustomerObject() {
        String customerStr = "{\n"
                             + "    \"Customer\": [{\n"
                             + "        \"Store_ID\": \"80\",\n"
                             + "        \"Code\": \"BLRA786\",\n"
                             + "        \"Category\": \"Apollo HBP\",\n"
                             + "        \"Name\": \"APOLLO HOSPITALS STORE - BLR\",\n"
                             + "        \"City\": \"Hyderabad\",\n"
                             + "        \"State\": \"36-Karnataka\",\n"
                             + "        \"Address\": \"test address\",\n"
                             + "        \"Address1\": \"BALEPET\",\n"
                             + "        \"Address2\": \"NEXT TO DIGAMBAR JAIN TEMPLE\",\n"
                             + "        \"Mobile\": \"9844091070\",\n"
                             + "        \"Email\": \"apolloblrstore@gmail.com\",\n"
                             + "        \"DL_No\": \"KA/BNG/II/20-21/864\",\n"
                             + "        \"GST_no\": \"29BKJPS355921ZZ\",\n"
                             + "        \"Panel\": \"A\",\n"
                             + "        \"Pincode\": \"560053\"\n"
                             + "    }]\n"
                             + "}";
        JSONObject customerJson = new JSONObject(customerStr);
        return customerJson;
    }

    public JSONObject prepareCustomerObjectForValidation() {
        String customerStr = "{\n"
                             + "    \"Customer\": [{\n"
                             + "        \"Store_ID\": \"80\",\n"
                             + "        \"Code\": \"\",\n"
                             + "        \"Category\": \"Apollo HBP\",\n"
                             + "        \"Name\": \"APOLLO HOSPITALS STORE - BLR\",\n"
                             + "        \"City\": \"Hyderabad\",\n"
                             + "        \"State\": \"36-Karnataka\",\n"
                             + "        \"Address\": \"test address\",\n"
                             + "        \"Address1\": \"BALEPET\",\n"
                             + "        \"Address2\": \"NEXT TO DIGAMBAR JAIN TEMPLE\",\n"
                             + "        \"Mobile\": \"9844091070\",\n"
                             + "        \"Email\": \"apolloblrstore@gmail.com\",\n"
                             + "        \"DL_No\": \"KA/BNG/II/20-21/864\",\n"
                             + "        \"GST_no\": \"29BKJPS355921ZZ\",\n"
                             + "        \"Panel\": \"A\",\n"
                             + "        \"Pincode\": \"560053\"\n"
                             + "    }]\n"
                             + "}";
        JSONObject customerJson = new JSONObject(customerStr);
        return customerJson;
    }

    public JSONObject prepareCustomerObjectWithMobileNEmailAsNULL() {
        String customerStr = "{\n"
                             + "    \"Customer\": [{\n"
                             + "        \"Store_ID\": \"80\",\n"
                             + "        \"Code\": \"BLRA786\",\n"
                             + "        \"Category\": \"Apollo HBP\",\n"
                             + "        \"Name\": \"APOLLO HOSPITALS STORE - BLR\",\n"
                             + "        \"City\": \"Hyderabad\",\n"
                             + "        \"State\": \"36-Karnataka\",\n"
                             + "        \"Address\": \"test address\",\n"
                             + "        \"Address1\": \"BALEPET\",\n"
                             + "        \"Address2\": \"NEXT TO DIGAMBAR JAIN TEMPLE\",\n"
                             + "        \"Mobile\": \"\",\n"
                             + "        \"Email\": \"\",\n"
                             + "        \"DL_No\": \"KA/BNG/II/20-21/864\",\n"
                             + "        \"GST_no\": \"29BKJPS355921ZZ\",\n"
                             + "        \"Panel\": \"A\",\n"
                             + "        \"Pincode\": \"560053\"\n"
                             + "    }]\n"
                             + "}";
        JSONObject customerJson = new JSONObject(customerStr);
        return customerJson;
    }

    @Test
    public void validateCustomerInformationWithNullCheck() throws Exception {
        mockSearchCustomerAPIResponseForNewMemberCreation();
        mockCreateCustomerAPIResponse();
        mockAddUserToUserGroupFailedResponse();
        mockGetCitiesWithStateSuccessResponse();
        JSONObject customer = prepareCustomerObjectWithMobileNEmailAsNULL();
        camelExchange.getIn().setBody(customer);
        apolloMemberTransformProcessor.process(camelExchange);
        List<Customer> customerList = (List<Customer>) camelExchange.getIn().getBody();
        Customer actualCustomerInformation = customerList.get(0);
        assertEquals("", actualCustomerInformation.getMobileNumber());
        assertEquals("", actualCustomerInformation.getPhoneNumber());
        assertEquals("", actualCustomerInformation.getAlternateEmail());
        assertEquals("BLRA786", actualCustomerInformation.getUserName());
        assertCustomerDetails(actualCustomerInformation);
        Mockito.verify(mjCustomerApiClient).createCustomer(Mockito.any());

    }

    @Test
    public void validateCreateCustomerInformationDataSet() throws Exception {
        mockCreateCustomerAPIResponse();
        mockSearchCustomerAPIResponseForNewMemberCreation();
        mockAddUserToUserGroupSuccessResponse();
        mockGetCitiesWithStateSuccessResponse();
        JSONObject customer = prepareCustomerObject();
        camelExchange.getIn().setBody(customer);
        apolloMemberTransformProcessor.process(camelExchange);
        List<Customer> customerList = (List<Customer>) camelExchange.getIn().getBody();
        Customer actualCustomerInformation = customerList.get(0);
        assertEquals("9844091070", actualCustomerInformation.getMobileNumber());
        assertEquals("9844091070", actualCustomerInformation.getPhoneNumber());
        assertEquals("apolloblrstore@gmail.com", actualCustomerInformation.getAlternateEmail());
        assertEquals("BLRA786", actualCustomerInformation.getUserName());
        assertCustomerDetails(actualCustomerInformation);
        Mockito.verify(mjCustomerApiClient).createCustomer(Mockito.any());
        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(1, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(0, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(0, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    @Test
    public void validateCustomerInfoDataSetWithErrorValidation() throws Exception {

        mockCreateCustomerAPIResponse();
        mockSearchCustomerAPIResponseForNewMemberCreation();
        mockAddUserToUserGroupSuccessResponse();
        mockGetCitiesWithStateSuccessResponse();
        JSONObject customer = prepareCustomerObjectForValidation();
        camelExchange.getIn().setBody(customer);
        apolloMemberTransformProcessor.process(camelExchange);
        List<Customer> customerList = (List<Customer>) camelExchange.getIn().getBody();
        Assert.assertEquals(0, customerList.size());
        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(1, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    private void assertCustomerDetails(Customer actualCustomerInformation) {

        assertEquals("APOLLO HOSPITALS STORE - BLR", actualCustomerInformation.getFirstName());
        assertEquals("test address", actualCustomerInformation.getPostalAddress());
        assertEquals("560053", actualCustomerInformation.getPin());
        assertEquals("Karnataka", actualCustomerInformation.getStateName());
        assertEquals("BALEPET", actualCustomerInformation.getOtherArea());
        assertEquals("Hyderabad", actualCustomerInformation.getCityName());
        assertEquals("NEXT TO DIGAMBAR JAIN TEMPLE", actualCustomerInformation.getAreaName());
        assertEquals("", actualCustomerInformation.getLastName());
        assertEquals("TG", actualCustomerInformation.getState());
        assertEquals("195", actualCustomerInformation.getCity());
        assertEquals("", actualCustomerInformation.getGender());
        assertEquals("IN", actualCustomerInformation.getCountry());
        assertEquals("INDIA", actualCustomerInformation.getCountryName());
        assertEquals("", actualCustomerInformation.getOtherCity());
    }

    @Test
    public void validateUpdateCustomerCount() throws Exception {
        mockSearchCustomerAPIResponseForUpdateMemberCreation();
        mockAddUserToUserGroupFailedResponse();
        mockGetCitiesWithStateSuccessResponse();
        JSONObject customer = prepareCustomerObject();
        camelExchange.getIn().setBody(customer);
        apolloMemberTransformProcessor.process(camelExchange);
        List<Customer> customerList = (List<Customer>) camelExchange.getIn().getBody();
        Customer actualCustomerInformation = customerList.get(0);
        assertEquals("9844091070", actualCustomerInformation.getMobileNumber());
        assertEquals("9844091070", actualCustomerInformation.getPhoneNumber());
        assertEquals("apolloblrstore@gmail.com", actualCustomerInformation.getAlternateEmail());
        assertEquals("BLRA786", actualCustomerInformation.getUserName());
        assertCustomerDetails(actualCustomerInformation);
        Mockito.verify(mjCustomerApiClient, Mockito.times(0)).createCustomer(Mockito.eq(customer));
    }

    private String getCustomerSearchJsonResponse() {
        return parseFileToJsonObj(JSON_CUSTOMER_SEARCH_RESPONSE).toString();
    }

    private String getCustomerCreateJsonResponse() {
        return parseFileToJsonObj(JSON_CUSTOMER_CREATE_RESPONSE).toString();
    }

    private void buildApolloCustomerUserGroupMappings() {
        buildItemMappings("A", "A", "123", ITEM_TYPE.MJ_CUSTOMER_GROUP, "CUSTOMER_USER_GROUP");
        buildItemMappings("", "RandonKey", "678", ITEM_TYPE.MJ_CUSTOMER_GROUP, "customUserGrp");
        buildItemMappings("", "", "", null, "");
    }

    private void buildApolloStateCodeMappings() {
        buildItemMappings("36", "36", "TG", ITEM_TYPE.MJ_SHIP_STATE, "APOLLO_REGION_CODE");
        buildItemMappings("", "RandonKey", "678", ITEM_TYPE.MJ_SHIP_STATE, "shipState");
        buildItemMappings("", "", "", null, "");
    }
}
