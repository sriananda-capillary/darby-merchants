package com.sellerworx.modules.apollo.processor;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.UserProfileAttribute;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.util.ExchangeHeaderKeys;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApolloMemberAttributeTransformProcessorTest extends BaseAPITest {

    @Autowired
    ApolloMemberAttributeTransformProcessor apolloMemberAttributeTransformProcessor;

    @Autowired
    CamelContext context;

    private static Exchange camelExchange;
    private static String customer = "apollo_membership_get_customer.json";

    @Before
    public void buildExchange() {
        camelExchange = getExchange(context);
        tenant = getTenant();
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        RequestContext.getTenantInfo().setId(tenant.getId());
        JSONObject customerInputJson = prepareCustomerObject();
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.CUSTOMER, customerInputJson);

    }

    @Test
    public void checkIsUserProfileAttributesIsGenerated() throws Exception {
        buildApolloItemMappings();
        JSONObject customerJson = new JSONObject(parseFile(customer));
        ObjectMapper mapper = new ObjectMapper();
        Customer customerObj = mapper.readValue(customerJson.toString(), Customer.class);
        camelExchange.getIn().setBody(customerObj);
        apolloMemberAttributeTransformProcessor.process(camelExchange);
        List<UserProfileAttribute> actualUserProfileList = (List<UserProfileAttribute>) camelExchange.getIn().getBody();
        List<UserProfileAttribute> expectedUserProfileList = getExpectedUserProfileList();
        assertEquals(expectedUserProfileList, actualUserProfileList);
    }

    public JSONObject prepareCustomerObject() {
        String customerStr = "{\n"
                             + "    \"Customer\": [{\n"
                             + "        \"Store_ID\": \"80\",\n"
                             + "        \"Code\": \"BLRA786\",\n"
                             + "        \"Category\": \"Apollo HBP\",\n"
                             + "        \"Name\": \"APOLLO HOSPITALS STORE - BLR\",\n"
                             + "        \"City\": \"BENGALURU\",\n"
                             + "        \"State\": \"29-Karnataka\",\n"
                             + "        \"Address\": \"test address\",\n"
                             + "        \"Address1\": \"BALEPET\",\n"
                             + "        \"Address2\": \"NEXT TO DIGAMBAR JAIN TEMPLE\",\n"
                             + "        \"Mobile\": \"9844091070\",\n"
                             + "        \"Email\": \"apolloblrstore@gmail.com\",\n"
                             + "        \"DL_No\": \"KA/BNG/II/20-21/864\",\n"
                             + "        \"GST_no\": \"29BKJPS355921ZZ\",\n"
                             + "        \"Panel\": \"A\",\n"
                             + "        \"Pincode\": \"560053\",\n"
                             + "        \"Status\": \"Approved\",\n"
                             + "    }]\n"
                             + "}";
        JSONObject customerJson = new JSONObject(customerStr);
        return customerJson;
    }

    private List<UserProfileAttribute> getExpectedUserProfileList() {
        List<UserProfileAttribute> userProfileList = new ArrayList<UserProfileAttribute>();
        UserProfileAttribute userProfileAttribute = new UserProfileAttribute();
        userProfileAttribute.setProfileAttributeId(123);
        userProfileAttribute.setProfileAttributeValue("Apollo HBP");
        UserProfileAttribute userProfileAttribute1 = new UserProfileAttribute();
        userProfileAttribute1.setProfileAttributeId(234);
        userProfileAttribute1.setProfileAttributeValue("KA/BNG/II/20-21/864");
        UserProfileAttribute userProfileAttribute2 = new UserProfileAttribute();
        userProfileAttribute2.setProfileAttributeId(345);
        userProfileAttribute2.setProfileAttributeValue("29BKJPS355921ZZ");
        UserProfileAttribute userProfileAttribute3 = new UserProfileAttribute();
        userProfileAttribute3.setProfileAttributeId(456);
        userProfileAttribute3.setProfileAttributeValue("A");
        UserProfileAttribute userProfileAttribute4 = new UserProfileAttribute();
        userProfileAttribute4.setProfileAttributeId(567);
        userProfileAttribute4.setProfileAttributeValue("Approved");
        UserProfileAttribute userProfileAttribute5 = new UserProfileAttribute();
        userProfileAttribute5.setProfileAttributeId(678);
        userProfileAttribute5.setProfileAttributeValue("80");
        userProfileList.add(userProfileAttribute);
        userProfileList.add(userProfileAttribute1);
        userProfileList.add(userProfileAttribute2);
        userProfileList.add(userProfileAttribute3);
        userProfileList.add(userProfileAttribute4);
        userProfileList.add(userProfileAttribute5);
        return userProfileList;
    }

    private void buildApolloItemMappings() {
        buildItemMappings("Category", "Category", "123", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("DL_No", "DL_No", "234", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("GST_no", "GST_no", "345", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("Panel", "Panel", "456", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("Status", "Status", "567", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("Store_ID", "Store_ID", "678", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "CUSTOMER_ATTRIBUTES");
        buildItemMappings("", "RandonKey", "789", ITEM_TYPE.MJ_CUSTOMER_ATTRIBUTE, "customAttr");
        buildItemMappings("", "", "", null, "");
    }
}
