package com.capillary.spar.processor;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.UserProfile;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.capillary.spar.service.MaxEmpService;
import com.capillary.spar.util.SparExchangeHeaderKeys;
import com.capillary.spar.util.SparTenantConfigkeys;
import lombok.extern.slf4j.Slf4j;
import max.retail.stores.ws.employee.GetEmployeeDetails;
import max.retail.stores.ws.employee.GetEmployeeDetailsE;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("MaxGetEmployeeProcessor")
@Documented(description = "the processor is used to call the spar landmark api which gives the details that whether" +
                          "the customer is belong to landmark group or not",
            inBody = @KeyInfo(comment = "contains the customer map, which has customer and address details",
                              type = HashMap.class),
            outHeaders =  { @KeyInfo(comment = "contains boolean value based on employee exist or not",
                                     name = "IS_EMP_EXIST")})
public class MaxGetEmployeeProcessor extends DarbyBaseProcessor {

    private static final String LANDMARK = "Landmark";

    @Autowired
    private MaxEmpService maxEmpService;

    @Override
    public void startProcess(Exchange exchange) {
        Map<String, Object> customerMap = ExchangeUtil.getBody(exchange, Map.class);
        Customer customer = (Customer) customerMap.get(ExchangeHeaderKeys.CUSTOMER);
        Map<String, String> configMap = RequestContext.getTenantInfo().getConfigMap();
        String profileAttributeId = configMap.get(SparTenantConfigkeys.EMPLOYEE_ID_PROFILE_ATTRIBUTE_ID);
        ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.IS_EMP_EXIST, false, exchange);
        Optional<UserProfile> userProfileEmployee = customer.getUserProfiles().stream().filter(
            userProfile -> userProfile.getProfileAttributeId().equals(profileAttributeId)).findAny();
        if(userProfileEmployee.isPresent())
        {
            String employeeId = userProfileEmployee.get().getProfileAttributeValue();
            if (StringUtils.isNotBlank(employeeId))
            {
                String serviceEndPoint = configMap.get(SparTenantConfigkeys.SPAR_WEBSERVICE_ENDPOINT);
                GetEmployeeDetailsE getEmployeeReq = new GetEmployeeDetailsE();
                GetEmployeeDetails  getEmployeeDetails = new GetEmployeeDetails();
                getEmployeeDetails.setArg1(LANDMARK);
                getEmployeeDetails.setArg0(employeeId);
                getEmployeeReq.setGetEmployeeDetails(getEmployeeDetails);

                String employee = maxEmpService.getEmployee(serviceEndPoint, getEmployeeReq);
                if (StringUtils.isNotBlank(employee)) {
                    log.info("employee {} belongs to the landmark employee group ", employeeId);
                    ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.IS_EMP_EXIST, true, exchange);
                }
                else {
                    log.info("employee {} does not belongs to the landmark employee group", employeeId);
                    ExchangeHeaderKeys.setInHeader(SparExchangeHeaderKeys.IS_EMP_EXIST, false, exchange);
                }
            }
            else
            {
                String msg = "employee id is blank in user profile attribute for customer "
                                + customer.getCustomerID() + ", hence skipping adding to the landmark group";
                log.info(msg);
            }
        }
        else
        {
            String msg = "user profile attribute for employeeid is not present in attribute for customer"
                            + customer.getCustomerID() + ", hence skipping adding to the landmark group";
            log.info(msg);
        }
    }
}
