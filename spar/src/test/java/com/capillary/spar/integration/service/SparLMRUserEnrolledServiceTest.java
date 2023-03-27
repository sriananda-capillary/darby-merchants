package com.capillary.spar.integration.service;

import com.capillary.spar.integration.client.SparLMRUserEnrolledApiClient;
import com.capillary.spar.integration.model.GetMemberForLMRPayload;
import com.capillary.spar.integration.model.LMREnrollmentRequest;
import com.capillary.spar.integration.util.SparLMRTenantConfigKeys;
import com.sellerworx.Application;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.service.TenantConfigSvc;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SparLMRUserEnrolledServiceTest extends BaseAPITest {

    @Autowired
    TenantConfigSvc configSvc;
    @Autowired
    private CamelContext camelContext;
    private Tenant tenant;

    private Map<String, String> tenantConfigMap;

    @MockBean
    private SparLMRUserEnrolledApiClient sparUserApiClient;

    private Exchange exchange;

    @Autowired
    private SparLMRUserEnrolledService sparLMRUserEnrolledService;


    @Before
    public void preset() {
        tenant = getTenant();
        exchange = getExchange(camelContext);
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        tenantConfigMap = new HashMap<String, String>();
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_ID, "abd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "dfd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "1fd000");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST, "1000");
        tenantConfigMap.put(SparLMRTenantConfigKeys.SPAR_USER_ENROLL_AUTH_KEY, "acd123");
        tenantConfigMap.put(SparLMRTenantConfigKeys.SPAR_LMR_WEBSERVICE_ENDPOINT, "http://14.142.50.123:7001/");
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
    }

    @Test
    public void assertRequestBodyForLMREnrollAPI()
    {
        ArgumentCaptor<String> requestBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> host = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> authKey = ArgumentCaptor.forClass(String.class);
        Mockito.when(sparUserApiClient.enrollUserToLMR(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString())).thenReturn(getLMRAPIResponse());
        sparLMRUserEnrolledService.enrollNewUserToLandmark(getLMRRequestPayload());

        Mockito.verify(sparUserApiClient, Mockito.times(1))
                .enrollUserToLMR(host.capture(),
                        requestBody.capture(),
                        authKey.capture());
        Assert.assertEquals("http://14.142.50.123:7001/", host.getValue());
        Assert.assertEquals(getExpectedReqBodyStrForLmrAPI(), requestBody.getValue());
        Assert.assertEquals("acd123", authKey.getValue());

    }

    @Test
    public void assertRequestBodyForGetMemForLMR()
    {
        ArgumentCaptor<String> requestBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> host = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> authKey = ArgumentCaptor.forClass(String.class);
        Mockito.when(sparUserApiClient.getMemberForLMR(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString())).thenReturn(getMemberForLMRRespone());
        GetMemberForLMRPayload payload = new GetMemberForLMRPayload();
        payload.setMobileNumber("8787878987");
        sparLMRUserEnrolledService.getMemberForLMR(payload);

        Mockito.verify(sparUserApiClient, Mockito.times(1))
                .getMemberForLMR(host.capture(),
                        requestBody.capture(),
                        authKey.capture());
        Assert.assertEquals("http://14.142.50.123:7001/", host.getValue());
        Assert.assertEquals(getExpReqBodyForGetMemForLmr(), requestBody.getValue());
        Assert.assertEquals("acd123", authKey.getValue());

    }

    private String getExpReqBodyForGetMemForLmr()
    {
        return "{\"mobileNumber\":\"8787878987\"}";
    }
    private String getExpectedReqBodyStrForLmrAPI()
    {
        return "{\"firstName\":\"abc\",\"lastName\":null,\"country\":null,\"mobileNumber\":" +
                "\"8278778744\",\"emailID\":\"a@gmail.com\",\"dateOfBirth\":null,\"city\":null," +
                "\"nationality\":null,\"storeCode\":null,\"pinCode\":null}";
    }
    private LMREnrollmentRequest getLMRRequestPayload()
    {
        LMREnrollmentRequest lmrEnrollmentRequest = new LMREnrollmentRequest();
        lmrEnrollmentRequest.setFirstName("abc");
        lmrEnrollmentRequest.setEmailID("a@gmail.com");
        lmrEnrollmentRequest.setMobileNumber("8278778744");
        return lmrEnrollmentRequest;
    }

    private ResponseEntity<String> getLMRAPIResponse()
    {
        String body = "{     \"result\": true,\"resultCode\": \"1\", \"message\": \"SUCCESS\", \"cardNumber\": " +
                "\"80021123\", \"firstName\": \"Test\",\"lastName\": \"storeCode\" }";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        return response;

    }

    private ResponseEntity<String> getMemberForLMRRespone()
    {
        String body= "{\n" +
                "    \"result\": true,\n" +
                "    \"resultCode\": \"SUCCESS\",\n" +
                "    \"message\": \"SUCCESS\",\n" +
                "    \"mobileNumber\": \"8619510915\",\n" +
                "    \"cardNumber\": \"80021122\",\n" +
                "    \"displayName\": \"Test Storecode\",\n" +
                "    \"emailID\": \"dd@gmail.com\",\n" +
                "    \"pointsAvailable\": 0.0,\n" +
                "    \"tierStatus\": \"Gold\",\n" +
                "    \"okToAccrue\": false,\n" +
                "    \"okToRedeem\": false,\n" +
                "    \"active\": true,\n" +
                "    \"scbCard\": false\n" +
                "}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(body, HttpStatus.OK);
        return responseEntity;
    }
}
