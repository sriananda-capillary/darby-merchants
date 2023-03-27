package com.sellerworx.modules.apollo.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.UserProfileAttribute;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.apollo.util.ApolloAliasKeys;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import com.sellerworx.modules.mapping.MappingService;

@Component("ApolloMemberAttributeTransformProcessor")
@Documented(description = "Transforms the member master entity from input file to mj specific entity",
        inBody = @KeyInfo(comment = "Input Customer Object", type = Customer.class),
        outBody = @KeyInfo(comment = "transformed List of UserProfileAttribute model", type = List.class),
        outHeaders = @KeyInfo(comment = "adding the customer userid ", type = String.class,
                name = ExchangeHeaderKeys.MJ_USER_ID))
public class ApolloMemberAttributeTransformProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloMemberAttributeTransformProcessor.class);

    @Autowired
    MappingService mappingService;

    @Override
    public void process(Exchange exchange) throws Exception {

        Customer customer = (Customer) exchange.getIn().getBody();
        if (customer == null || customer.getUserName() == null) {
            String errorMessage = "customer object is null in exchange body";
            logger.error(errorMessage, ErrorCode.INVALID);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }
        JSONObject memberInputJson =
                (JSONObject) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.CUSTOMER, exchange);
        JSONObject customerobj = getCustomerFromMemberInputJson(memberInputJson, customer.getUserName());
        if (customerobj == null) {
            String errorMessage =
                    "no customer found to process further as the exchange body doesnt match with the header data";
            logger.error(errorMessage, ErrorCode.INVALID);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }
        logger.info("processing the member json the body is : {} and customer json is {}", customer, memberInputJson);
        List<UserProfileAttribute> userProfileList = buildUserAttributesJson(customerobj);
        exchange.getIn().setHeader(ExchangeHeaderKeys.MJ_USER_ID, customer.getCustomerID());
        exchange.getIn().setBody(userProfileList);
    }

    private JSONObject getCustomerFromMemberInputJson(JSONObject memberInputJson, String userName) {
        JSONArray customerJsonArray = null;
        if (memberInputJson.has(ApolloUtil.MEMBER_CUSTOMER_KEY)) {
            customerJsonArray = memberInputJson.getJSONArray(ApolloUtil.MEMBER_CUSTOMER_KEY);
            if (null != customerJsonArray && customerJsonArray.length() > 0) {
                for (int i = 0; i < customerJsonArray.length(); i++) {
                    JSONObject customerobj = customerJsonArray.getJSONObject(i);
                    if (customerobj.has(ApolloUtil.MEMBER_CODE)
                        && ((String) customerobj.get(ApolloUtil.MEMBER_CODE)) != null
                        && customerobj.get(ApolloUtil.MEMBER_CODE).toString().equalsIgnoreCase(userName)) {
                        return customerobj;
                    }
                }
            }
        }
        return null;
    }

    private List<UserProfileAttribute> buildUserAttributesJson(JSONObject customerJson) {
        List<UserProfileAttribute> userProfileList = new ArrayList<>();
        createList(ApolloUtil.MEMBER_CATEGORY, userProfileList, customerJson);
        createList(ApolloUtil.MEMBER_DL_NO, userProfileList, customerJson);
        createList(ApolloUtil.MEMBER_GST_NO, userProfileList, customerJson);
        createList(ApolloUtil.MEMBER_PANEL, userProfileList, customerJson);
        createList(ApolloUtil.MEMBER_STATUS, userProfileList, customerJson);
        createList(ApolloUtil.MEMBER_STORE_ID, userProfileList, customerJson);
        return userProfileList;

    }

    private void createList(String keyName, List<UserProfileAttribute> userProfileList, JSONObject memberInputJson) {
        UserProfileAttribute userProfileAttribute;
        if (memberInputJson.has(keyName) && memberInputJson.get(keyName) != null) {
            userProfileAttribute = getUserProfileObjWithKeyName(keyName, (String) memberInputJson.get(keyName));
            logger.info("userprofileattribute object for key {} profileattribute is {}", keyName, userProfileAttribute);
            if (null != userProfileAttribute) {
                userProfileList.add(userProfileAttribute);
            }
        }
    }

    private UserProfileAttribute getUserProfileObjWithKeyName(String keyName, String keyValue) {
        UserProfileAttribute userProfileAttribute = new UserProfileAttribute();
        int attrID = getAttributeIDWithKeyName(keyName);
        if (attrID > 0) {
            userProfileAttribute.setProfileAttributeId(attrID);
            userProfileAttribute.setProfileAttributeValue(keyValue);
            return userProfileAttribute;
        } else {
            String errorMessage = "Not able to fetch attributeid for keyname: " + keyName;
            logger.error(errorMessage, ErrorCode.INVALID);
        }
        return null;
    }

    private int getAttributeIDWithKeyName(String keyname) {
        try {
            Long tenantID = RequestContext.getTenantInfo().getId();
            String attrIDStr = mappingService.map(tenantID, keyname, ApolloAliasKeys.CUSTOMER_ATTRIBUTES,
                    ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE);
            if (Util.isNumber(attrIDStr)) {
                int attrID = Integer.parseInt(attrIDStr);
                return attrID;
            }
        } catch (Exception ex) {
            String errorMessage = "profile attribute mapping not found for keyname" + keyname;
            logger.error(errorMessage);
        }
        return 0;
    }

}
