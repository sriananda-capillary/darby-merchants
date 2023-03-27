package com.capillary.spar.processor;


import com.capillary.spar.service.MaxEmpService;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.capillary.spar.util.SparTenantConfigkeys;
import com.sellerworx.Application;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.Address;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MaxGetEmployeeProcessorTest extends BaseAPITest
{
    @Autowired
    CamelContext camelContext;

    @MockBean
    private MaxEmpService maxEmpService;

    @Autowired
    private MaxGetEmployeeProcessor maxGetEmployeeProcessor;
    private static Customer customer = new Customer();
    private static final String CUSTOMER_RESPONSE = "customer_response.json";
    private Exchange exchange;
    private Map<String, String> tenantConfigMap = new HashMap<String, String>();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void buildExchange() {
        customer = parseFileToEnttity(CUSTOMER_RESPONSE, Customer.class);
        tenant = getTenant();
        exchange = getExchange(camelContext);
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        tenantConfigMap = new HashMap<String, String>();
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_ID, "abd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "dfd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "1fd000");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST, "1000");
        tenantConfigMap.put(SparTenantConfigkeys.EMPLOYEE_ID_PROFILE_ATTRIBUTE_ID, "1549");
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
    }

    @Test
    public void checkEmpExistToLandmarkGp()
    {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        exchange.getIn().setBody(customerMap);
        String employee = "cap1998";
        Mockito.when(maxEmpService.getEmployee(Mockito.any(), Mockito.any())).thenReturn(employee);
        maxGetEmployeeProcessor.startProcess(exchange);
        boolean isEmpExist = (boolean) ExchangeHeaderKeys.getValueFromExchangeHeader(SparExchangeHeaderKeys.IS_EMP_EXIST,
                                                                                     exchange);
        Assert.assertEquals(true,isEmpExist);

    }

    @Test
    public void checkEmpDoesnotExistToLandmarkGp()
    {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        exchange.getIn().setBody(customerMap);
        Mockito.when(maxEmpService.getEmployee(Mockito.any(), Mockito.any())).thenReturn(null);
        maxGetEmployeeProcessor.startProcess(exchange);
        boolean isEmpExist = (boolean) ExchangeHeaderKeys.getValueFromExchangeHeader(SparExchangeHeaderKeys.IS_EMP_EXIST,
                                                                                     exchange);
        Assert.assertEquals(false,isEmpExist);

    }

    @Test
    public void checkIfEmpAttributeIsNotPresent()
    {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customer.getUserProfiles().remove(0);
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        exchange.getIn().setBody(customerMap);
        Mockito.when(maxEmpService.getEmployee(Mockito.any(), Mockito.any())).thenReturn(null);
        maxGetEmployeeProcessor.startProcess(exchange);
        boolean isEmpExist = (boolean)ExchangeHeaderKeys.getValueFromExchangeHeader(SparExchangeHeaderKeys.IS_EMP_EXIST, exchange);
        Assert.assertFalse(isEmpExist);
    }

    @Test
    public void checkIfEmpIdIsNotPresent()
    {
        Map<String, Object> customerMap = new HashMap<>();
        Set<Address> addresses = new HashSet<>();
        customer.getUserProfiles().get(0).setProfileAttributeValue("");
        customerMap.put(ExchangeHeaderKeys.CUSTOMER, customer);
        customerMap.put(ExchangeHeaderKeys.ADDRESS, addresses);
        exchange.getIn().setBody(customerMap);
        maxGetEmployeeProcessor.startProcess(exchange);
        boolean isEmpExist = (boolean)ExchangeHeaderKeys.getValueFromExchangeHeader(SparExchangeHeaderKeys.IS_EMP_EXIST, exchange);
        Assert.assertFalse(isEmpExist);
    }

}
