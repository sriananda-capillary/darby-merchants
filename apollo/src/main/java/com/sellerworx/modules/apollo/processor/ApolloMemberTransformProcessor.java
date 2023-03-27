package com.sellerworx.modules.apollo.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.apollo.util.ApolloAliasKeys;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import com.sellerworx.modules.mapping.MappingService;
import com.sellerworx.modules.martjack.services.CustomerService;
import com.sellerworx.modules.martjack.services.MJStoreService;

@Component("ApolloMemberTransformProcessor")
@Documented(description = "Transforms the member master entity from input file to mj specific entity",
        inBody = @KeyInfo(comment = "Input JsonObject", type = JSONObject.class),
        outBody = @KeyInfo(comment = "transformed List of Customer model", type = List.class),
        outHeaders = @KeyInfo(
                comment = "adding the input customer json 'customer' to the header for further processing",
                type = JSONObject.class))
public class ApolloMemberTransformProcessor implements Processor {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private MJStoreService mjStoreService;

    @Autowired
    MappingService mappingService;

    private static final Logger logger = LoggerFactory.getLogger(ApolloMemberTransformProcessor.class);

    private static final String CUSTOMER_SEARCH_KEY = "customerSearch";

    @Override
    public void process(Exchange exchange) throws Exception {
        Tenant tenant = (Tenant) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.TENANT, exchange);
        Map<String, String> tenantConfigMap = (Map<String, String>) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, exchange);
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
        try {
            JSONObject customerInformation = (JSONObject) exchange.getIn().getBody();
            if (null != customerInformation) {
                logger.info("transforming membership file data {} for file : {}", customerInformation, fileName);
                JSONArray customerJson = null;
                if (customerInformation.has(ApolloUtil.MEMBER_CUSTOMER_KEY)) {
                    customerJson = customerInformation.getJSONArray(ApolloUtil.MEMBER_CUSTOMER_KEY);
                    createOrUpdateMember(exchange, tenantConfigMap, customerInformation, customerJson, fileName);
                } else {
                    String errorMessage = "Member details json doesnt have a customer array key : " + fileName;
                    logger.error(errorMessage, ErrorCode.INVALID);
                }

            } else {
                String errorMessage = "Member details json is empty or invalid format in : " + fileName;
                logger.error(errorMessage, ErrorCode.INVALID);
            }
        } catch (Exception ex) {
            String errorMessage =
                    "Error occured while transforming the file data : " + fileName + "<br/> Error : " + ex.getMessage();
            logger.error(errorMessage, ErrorCode.INVALID);
            throw new DarbyException(ex, ErrorCode.INVALID);

        }
    }

    private void createOrUpdateMember(Exchange exchange, Map<String, String> tenantConfigMap,
            JSONObject customerInformation, JSONArray customerJson, String fileName) {
        String defaultPassword =
                (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ApolloUtil.DEFAULT_PASSWORD, exchange);
        int totalRecords = 0;
        List<FieldErrorModel> fieldErrorModels = new ArrayList<>();
        BatchProcessDetails ftpFileDetails = new BatchProcessDetails();
        ftpFileDetails.setFieldErrorModelList(fieldErrorModels);
        if (null != customerJson && customerJson.length() > 0) {
            List<Customer> customersList = new ArrayList<>();
            totalRecords = customerJson.length();

            for (int i = 0; i < customerJson.length(); i++) {
                JSONObject customerobj = customerJson.getJSONObject(i);
                try {
                    logger.info("transforming membership data for customer : {}", customerobj);
                    Customer customer = getCustomerInformation(customerobj, tenantConfigMap, fileName, ftpFileDetails);
                    boolean isNewCustomer = false;
                    if (customer == null) {
                        isNewCustomer = true;
                        customer = new Customer();
                    }
                    Customer customerDetails = prepareCustomerInfoReq(customerobj, customer, fileName, tenantConfigMap);
                    if (customerDetails != null && isNewCustomer) {
                        customerDetails.setPassword(defaultPassword);
                        logger.info("creating new customer with username {} for file : {}",
                                customerDetails.getUserName(), fileName);
                        Customer newCustomerDetails = customerService.createCustomer(customerDetails);
                        customerDetails.setCustomerID(newCustomerDetails.getCustomerID());
                        customerDetails.setMartialStatus(ApolloUtil.MARTIAL_STATUS);
                        //checking if the usergroup id is not null from the input json.
                        if (newCustomerDetails.getCustomerID() != null
                            && customerobj.has(ApolloUtil.MEMBER_PANEL)
                            && ((String) customerobj.get(ApolloUtil.MEMBER_PANEL) != null)) {
                            addUserToUserGroup((String) newCustomerDetails.getCustomerID(),
                                    (String) customerobj.get(ApolloUtil.MEMBER_PANEL));
                        }
                    }
                    customersList.add(customerDetails);
                } catch (Exception ex) {
                    logger.error("Error occured while transforming the file data : {} for customer json {} Error : {} ",
                            fileName, customerobj, ex.getMessage());
                    continue;
                }
            }

            ftpFileDetails.setFileName(fileName);
            ftpFileDetails.setTotalCount(totalRecords);
            exchange.getIn().setHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, ftpFileDetails);
            exchange.getIn().setHeader(ExchangeHeaderKeys.CUSTOMER, customerInformation);
            exchange.getIn().setBody(customersList);
        } else {
            String errorMessage = "not able to retrieve customer json array from jsonobject : " + fileName;
            logger.error(errorMessage, ErrorCode.INVALID);
        }
    }

    /* This method is used to call an API to add an user to usergroup */
    private void addUserToUserGroup(String customerID, String groupName) {
        String groupId = getUserGroupIDFromGroupName(groupName);
        if (StringUtils.isBlank(groupId)) {
            String errorMessage = "mapping not found for groupName " + groupName;
            logger.error(errorMessage);
        } else {
            try {
                boolean responseJson = customerService.addUserToUserGroup(customerID, groupId);
                if (responseJson) {
                    String loggerMessage = "Added customer '" + customerID + " 'to group '" + groupName + " '";
                    logger.info(loggerMessage);
                } else {
                    String errorMessage = "Failed Adding customer '" + customerID + " 'to group '" + groupName + " '";
                    logger.error(errorMessage, ErrorCode.INVALID);
                }
            } catch (Exception ex) {
                String errorMessage = "Failed Adding customer '" + customerID + " 'to group '" + groupName + " '";
                logger.error(errorMessage, ErrorCode.INVALID);
            }
        }
    }

    private String getUserGroupIDFromGroupName(String groupName) {
        try {
            Long tenantID = RequestContext.getTenantInfo().getId();
            String attrID = mappingService.map(tenantID, groupName, ApolloAliasKeys.CUSTOMER_USER_GROUP,
                    ITEM_TYPE.MJ_CUSTOMER_GROUP);
            return attrID;
        } catch (Exception ex) {
            String errorMessage = "mapping not found for groupName " + groupName;
            logger.error(errorMessage);
        }
        return null;
    }

    private Customer getCustomerInformation(JSONObject customerobj, Map<String, String> tenantConfigMap,
            String fileName, BatchProcessDetails ftpFileDetails) {
        JSONObject reqBody = new JSONObject();
        JSONObject customerInfoJson = new JSONObject();
        String userID = StringUtils.EMPTY;
        int validationCount = ftpFileDetails.getValidationCount();
        if (customerobj != null
            && customerobj.has(ApolloUtil.MEMBER_CODE)
            && !Util.isJsonValueEmpty(ftpFileDetails.getFieldErrorModelList(), customerobj, ApolloUtil.MEMBER_CODE,
                    StringUtils.EMPTY)) {
            userID = customerobj.getString(ApolloUtil.MEMBER_CODE);
            logger.info("processing member with code {} and filename {} in method getCustomerInformation", userID,
                    customerobj);
            customerInfoJson.put(ApolloUtil.USERNAME, userID);
            reqBody.put(CUSTOMER_SEARCH_KEY, customerInfoJson);
            List<Customer> customers = customerService.searchCustomer(tenantConfigMap, reqBody);
            if (CollectionUtils.isNotEmpty(customers))
                return customers.get(0); //As per the functionality, the userID will be unique and there will be only one customer retrieved.
            else
                return null;
        } else {
            ftpFileDetails.setValidationCount(++validationCount);
            String errorMessage =
                    "error occured in getcustomerinformation method: membercode key is not available in the given customer json"
                                  + fileName;
            logger.error(errorMessage, ErrorCode.INVALID);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }

    }

    private Customer prepareCustomerInfoReq(JSONObject customerInformationList, Customer customer, String fileName,
            Map<String, String> tenantConfigMap) {
        if (null != customer) {
            if (customerInformationList.has(ApolloUtil.MEMBER_CODE)
                && customerInformationList.get(ApolloUtil.MEMBER_CODE) != null) {
                customer.setUserName((String) customerInformationList.get(ApolloUtil.MEMBER_CODE));
                if (customerInformationList.has(ApolloUtil.MEMBER_NAME))
                    customer.setFirstName((String) customerInformationList.get(ApolloUtil.MEMBER_NAME));
                if (customerInformationList.has(ApolloUtil.MEMBER_ADDRESS))
                    customer.setPostalAddress((String) customerInformationList.get(ApolloUtil.MEMBER_ADDRESS));
                if (customerInformationList.has(ApolloUtil.MEMBER_EMAIL))
                    customer.setAlternateEmail((String) customerInformationList.get(ApolloUtil.MEMBER_EMAIL));
                if (customerInformationList.has(ApolloUtil.MEMBER_PINCODE))
                    customer.setPin((String) customerInformationList.get(ApolloUtil.MEMBER_PINCODE));
                if (customerInformationList.has(ApolloUtil.MEMBER_MOBILE)) {
                    customer.setMobileNumber((String) customerInformationList.get(ApolloUtil.MEMBER_MOBILE));
                    customer.setPhoneNumber((String) customerInformationList.get(ApolloUtil.MEMBER_MOBILE));
                }
                if (customerInformationList.has(ApolloUtil.MEMBER_ADDRESS1))
                    customer.setOtherArea((String) customerInformationList.get(ApolloUtil.MEMBER_ADDRESS1));
                if (customerInformationList.has(ApolloUtil.MEMBER_STATE)) {

                    String[] memberState =
                            ((String) customerInformationList.get(ApolloUtil.MEMBER_STATE)).split(SymbolUtil.HYPHEN);
                    String mjStateCode = getMJStateCodeByApolloStateCode(memberState[0]);
                    if (StringUtils.isNotBlank(mjStateCode)) {
                        customer.setState(mjStateCode);
                        customer.setStateName(memberState[1]);
                        if (customerInformationList.has(ApolloUtil.MEMBER_CITY)) {
                            String cityId =
                                    getCityIdByStateCode((String) customerInformationList.get(ApolloUtil.MEMBER_CITY),
                                            mjStateCode, tenantConfigMap);
                            if (StringUtils.isNotEmpty(cityId)) {
                                customer.setCity(cityId);
                            }
                            customer.setCityName((String) customerInformationList.get(ApolloUtil.MEMBER_CITY));
                        }
                    }

                }
                if (customerInformationList.has(ApolloUtil.MEMBER_ADDRESS2))
                    customer.setAreaName((String) customerInformationList.get(ApolloUtil.MEMBER_ADDRESS2));
                customer.setLastName(StringUtils.EMPTY);
                customer.setGender(StringUtils.EMPTY);
                customer.setCountry(ApolloUtil.DEFAULT_COUNTRY_CODE);
                customer.setCountryName(ApolloUtil.DEFAULT_COUNTRY_NAME);
                customer.setOtherCity(StringUtils.EMPTY);
                logger.debug("update customer information request body : {}", customer.toString());
            } else {
                String errorMessage =
                        "Member Code is null  in the method prepareCustomerInfoReq for filename: " + fileName;
                logger.error(errorMessage, ErrorCode.INVALID);
                return null;
            }
        }
        return customer;
    }

    private String getMJStateCodeByApolloStateCode(String regionCode) {
        String stateCode = StringUtils.EMPTY;
        Long tenantID = RequestContext.getTenantInfo().getId();
        if (StringUtils.isNotEmpty(regionCode)) {
            stateCode = mappingService.map(tenantID, regionCode, ApolloAliasKeys.APOLLO_REGION_CODE,
                    ITEM_TYPE.MJ_SHIP_STATE);
            logger.info("mj state code for apollo regionCode {} is: {}", regionCode, stateCode);
            return stateCode;
        } else {
            logger.info("not able to retrieve mj statecode from apollostatecode {}", regionCode);
            return null;
        }

    }

    private String getCityIdByStateCode(String cityName, String stateCode, Map<String, String> tenantConfigMap) {
        String cityId = StringUtils.EMPTY;
        try {
            if (StringUtils.isNotEmpty(stateCode)) {
                Map<String, String> mjCityList = mjStoreService.cityInfoByStateCode(stateCode, tenantConfigMap);

                if (null != mjCityList && mjCityList.size() > 0) {
                    logger.info("address sync for city list size {}", mjCityList.size());
                    cityId = mjCityList
                            .entrySet()
                            .stream()
                            .filter(e -> e.getValue().equalsIgnoreCase(cityName))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(StringUtils.EMPTY);
                }
            }
            logger.info("city id for state {} is: {} ", stateCode, cityId);
            return cityId;
        } catch (IOException e) {
            logger.error("error getting cityInfoByStateCode api", ErrorCode.INVALID);
            return null;
        }

    }
}
