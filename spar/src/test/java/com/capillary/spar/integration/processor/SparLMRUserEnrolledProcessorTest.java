package com.capillary.spar.integration.processor;
import com.capillary.spar.integration.model.LMREnrollmentResult;
import com.capillary.spar.integration.service.SparLMRUserEnrolledService;
import com.capillary.spar.integration.util.SparLMRTenantConfigKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.Application;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.Address;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.UserProfileAttribute;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.service.TenantConfigSvc;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.IOException;
import java.util.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SparLMRUserEnrolledProcessorTest extends BaseAPITest {

    @Autowired
    TenantConfigSvc configSvc;
    @Autowired
    private CamelContext camelContext;
    private Tenant tenant;

    private Map<String, String> tenantConfigMap;

    @MockBean
    private SparLMRUserEnrolledService sparLMRUserEnrolledService;

    private Exchange exchange;

    @Autowired
    private SparLMRUserEnrolledProcessor sparLMRUserEnrolledProcessor;
    private static Customer customer = new Customer();
    private static final String CUSTOMER_RESPONSE = "customer_response.json";


    @Before
    public void preset() {
        customer = parseFileToEnttity(CUSTOMER_RESPONSE, Customer.class);
        tenant = getTenant();
        exchange = getExchange(camelContext);
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        tenantConfigMap = new HashMap<String, String>();
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_ID, "abd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "dfd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "1fd000");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST, "1000");
        tenantConfigMap.put(SparLMRTenantConfigKeys.LMR_PROFILE_ATTRIBUTE_ID, "1619");
        tenantConfigMap.put(SparLMRTenantConfigKeys.SPAR_LMR_PIN_CODE, "20001");
        tenantConfigMap.put(SparLMRTenantConfigKeys.SPAR_LMR_STORE_CODE, "560061");
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
    }

    @Test
    public void assertUserProfileAttrForNewUser() throws IOException {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        ExchangeUtil.setBody(customerMap, exchange);
        LMREnrollmentResult lmrEnrollmentResult = getLMREnrollmentAPIResult();
        Mockito.when(sparLMRUserEnrolledService.enrollNewUserToLandmark(Mockito.any())).thenReturn(lmrEnrollmentResult);
        sparLMRUserEnrolledProcessor.startProcess(exchange);
        List<UserProfileAttribute> userProfileList = ExchangeUtil.getBody(exchange, List.class);
        Assert.assertEquals("80021123", userProfileList.get(0).getProfileAttributeValue());
        Assert.assertEquals(1619, userProfileList.get(0).getProfileAttributeId());
    }

    @Test
    public void assertUserProfileAttrForExisting() throws IOException {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        ExchangeUtil.setBody(customerMap, exchange);
        LMREnrollmentResult lmrEnrollmentUserAPIResult = getLMREnrollmentAPIResultForExistUser();
        Mockito.when(sparLMRUserEnrolledService.enrollNewUserToLandmark(Mockito.any())).thenReturn(lmrEnrollmentUserAPIResult);
        Mockito.when(sparLMRUserEnrolledService.getMemberForLMR(Mockito.any())).thenReturn(getMemberForLMRAPIResult());
        sparLMRUserEnrolledProcessor.startProcess(exchange);
        List<UserProfileAttribute> userProfileList = ExchangeUtil.getBody(exchange, List.class);
        Assert.assertEquals("80020342", userProfileList.get(0).getProfileAttributeValue());
        Assert.assertEquals(1619, userProfileList.get(0).getProfileAttributeId());
    }

    @Test
    public void callingGetMemberAPIWithInvalidPayload() throws IOException {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        ExchangeUtil.setBody(customerMap, exchange);
        LMREnrollmentResult lmrEnrollmentUserAPIResult = getLMREnrollmentAPIResultForExistUser();
        Mockito.when(sparLMRUserEnrolledService.enrollNewUserToLandmark(Mockito.any())).thenReturn(lmrEnrollmentUserAPIResult);
        Mockito.when(sparLMRUserEnrolledService.getMemberForLMR(Mockito.any())).thenReturn(getMemberForLMRAPIFailedResult());
        try
        {
            sparLMRUserEnrolledProcessor.startProcess(exchange);
        }catch(DarbyException e)
        {
            String actualErrorMsg = e.getMessage();
            String expectedErrorMsg = "an error occured while calling the get_member_for_lmr_api an error occured " +
                    "while calling the lmr_enrollment_api insufficient data.";
            Assert.assertEquals(expectedErrorMsg, actualErrorMsg);
        }
    }

    @Test
    public void assertifTheCustomeValidParamIsNull()
    {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customer.setEmail("");
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        ExchangeUtil.setBody(customerMap, exchange);
        try
        {
            sparLMRUserEnrolledProcessor.startProcess(exchange);
        }catch(DarbyException e)
        {
            String actualErrorMsg = e.getMessage();
            String expectedErrorMsg = "value of the field mobilenumber, emailid, name cannot be empty";
            Assert.assertEquals(expectedErrorMsg, actualErrorMsg);
        }

    }

    private LMREnrollmentResult getLMREnrollmentAPIResult() throws IOException {
        String body = "{     \"result\": true,\"resultCode\": \"1\", \"message\": \"SUCCESS\", \"cardNumber\": " +
                "\"80021123\", \"firstName\": \"Test\",\"lastName\": \"storeCode\" }";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        ObjectMapper objectMapper = new ObjectMapper();
        LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue(response.getBody().toString(), LMREnrollmentResult.class);
        return lmrEnrollmentResult;
    }

    private LMREnrollmentResult getLMREnrollmentAPIResultForExistUser() throws IOException
    {
        String body = "{\n" +
                "    \"result\": false,\n" +
                "    \"resultCode\": \"18\",\n" +
                "    \"message\": \"A member already exists with this Email ID.\",\n" +
                "    \"cardNumber\": null,\n" +
                "    \"firstName\": null,\n" +
                "    \"lastName\": null\n" +
                "}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        ObjectMapper objectMapper = new ObjectMapper();
        LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue(response.getBody().toString(), LMREnrollmentResult.class);
        return lmrEnrollmentResult;
    }

    private LMREnrollmentResult getMemberForLMRAPIResult() throws IOException
    {
        String body = "{\n" +
                "    \"result\": true,\n" +
                "    \"resultCode\": \"SUCCESS\",\n" +
                "    \"message\": \"SUCCESS\",\n" +
                "    \"mobileNumber\": \"9652933955\",\n" +
                "    \"cardNumber\": \"80020342\",\n" +
                "    \"displayName\": \"SPARCAP TWO \",\n" +
                "    \"emailID\": null,\n" +
                "    \"pointsAvailable\": 169.57,\n" +
                "    \"tierStatus\": \"Gold\",\n" +
                "    \"okToAccrue\": false,\n" +
                "    \"okToRedeem\": true,\n" +
                "    \"active\": true,\n" +
                "    \"scbCard\": false\n" +
                "}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        ObjectMapper objectMapper = new ObjectMapper();
        LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue(response.getBody().toString(), LMREnrollmentResult.class);
        return lmrEnrollmentResult;
    }


    private LMREnrollmentResult getMemberForLMRAPIFailedResult() throws IOException
    {
        String body = "{\n" +
                "    \"result\": false,\n" +
                "    \"resultCode\": \"2\",\n" +
                "    \"message\": \"Insufficient Data.\",\n" +
                "    \"mobileNumber\": null,\n" +
                "    \"cardNumber\": null,\n" +
                "    \"displayName\": null,\n" +
                "    \"emailID\": null,\n" +
                "    \"pointsAvailable\": null,\n" +
                "    \"tierStatus\": null,\n" +
                "    \"okToAccrue\": null,\n" +
                "    \"okToRedeem\": null,\n" +
                "    \"gender\": null,\n" +
                "    \"emailVerifiedAt\": null,\n" +
                "    \"mobileVerifiedAt\": null,\n" +
                "    \"passwordChangedAt\": null,\n" +
                "    \"lmsLinkedAt\": null,\n" +
                "    \"transactions\": [],\n" +
                "    \"active\": null,\n" +
                "    \"scbCard\": null\n" +
                "}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        ObjectMapper objectMapper = new ObjectMapper();
        LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue(response.getBody().toString(), LMREnrollmentResult.class);
        return lmrEnrollmentResult;
    }

}
