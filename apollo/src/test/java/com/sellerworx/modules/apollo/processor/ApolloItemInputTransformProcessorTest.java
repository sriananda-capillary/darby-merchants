package com.sellerworx.modules.apollo.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.modules.apollo.models.ApolloItemMaster;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApolloItemInputTransformProcessorTest extends BaseAPITest {

    @Autowired
    ApolloItemInputTransformProcessor apolloItemInputTransformProcessor;

    private static Exchange camelExchange;
    private static String fileName = "apollo_item_master.json";

    @Autowired
    CamelContext context;

    @Before
    public void buildExchange() {
        camelExchange = getExchange(context);
        JSONObject responseJson = new JSONObject(parseFile(fileName));
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, fileName);
        camelExchange.getIn().setBody(responseJson);
    }

    @Test
    public void checkForValidInput() throws Exception {
        apolloItemInputTransformProcessor.process(camelExchange);
        List<ApolloItemMaster> actualItemMasterList = (List<ApolloItemMaster>) camelExchange.getIn().getBody();
        List<ApolloItemMaster> expectedItemMasterList = getExpectedItemMasterObjList();
        Assert.assertEquals(actualItemMasterList.size(), expectedItemMasterList.size() - 1);
        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    private List<ApolloItemMaster> getExpectedItemMasterObjList() {
        List<ApolloItemMaster> itemMasterList = new ArrayList<>();
        ApolloItemMaster obj1 = new ApolloItemMaster();
        obj1.setMpclCode("MPL1627");
        obj1.setCategory("ELECTRODES");
        obj1.setPack("100");
        obj1.setGeneric("SURGICAL INSTRUMENTS");
        obj1.setProduct("ECG ELECTRODES (3M)");
        obj1.setManufacturer("3M INDIA");
        obj1.setDivision("HEART CARE");
        obj1.setHsn("90189099");
        obj1.setCgst_per("6.00");
        obj1.setSgst_per("6.00");
        obj1.setIgst_per("12.00");
        obj1.setMrp("2310.00");
        obj1.setApollo_price("273.00");
        obj1.setTrade_price("181.60");
        obj1.setUom("100");
        obj1.setBox_qty("100");
        obj1.setCase_qty("8000");
        obj1.setApollo_code("ECG0008");
        obj1.setClosing_stock("1128");
        obj1.setStoreID("HYD");
        obj1.setBatchNo("batch1");
        obj1.setExpiryDate("21/02/2019");
        obj1.setPromoCode("testpromo");
        obj1.setScheme("testscheme");
        ApolloItemMaster obj2 = new ApolloItemMaster();
        obj2.setMpclCode("MPL1627567");
        obj2.setCategory("ELECTRODES");
        obj2.setPack("100");
        obj2.setGeneric("SURGICAL INSTRUMENTS");
        obj2.setProduct("ECG ELECTRODES (3M)");
        obj2.setManufacturer("3M INDIA");
        obj2.setDivision("HEART CARE");
        obj2.setHsn("90189099");
        obj2.setCgst_per("6.00");
        obj2.setSgst_per("6.00");
        obj2.setIgst_per("12.00");
        obj2.setMrp("2310.00");
        obj2.setApollo_price("273.00");
        obj2.setTrade_price("181.60");
        obj2.setUom("100");
        obj2.setBox_qty("100");
        obj2.setCase_qty("8000");
        obj2.setApollo_code("ECG0008");
        obj2.setClosing_stock("1128");
        obj2.setStoreID("HYD");
        obj2.setBatchNo("batch1");
        obj2.setExpiryDate("21/02/2019");
        obj2.setPromoCode("testpromo");
        obj2.setScheme("testscheme");
        ApolloItemMaster obj3 = new ApolloItemMaster();
        obj3.setMpclCode("");
        obj3.setCategory("ELECTRODES");
        obj3.setPack("100");
        obj3.setGeneric("SURGICAL INSTRUMENTS");
        obj3.setProduct("ECG ELECTRODES (3M)");
        obj3.setManufacturer("3M INDIA");
        obj3.setDivision("HEART CARE");
        obj3.setHsn("90189099");
        obj3.setCgst_per("6.00");
        obj3.setSgst_per("6.00");
        obj3.setIgst_per("12.00");
        obj3.setMrp("2310.00");
        obj3.setApollo_price("273.00");
        obj3.setTrade_price("181.60");
        obj3.setUom("100");
        obj3.setBox_qty("100");
        obj3.setCase_qty("8000");
        obj3.setApollo_code("ECG0008");
        obj3.setClosing_stock("1128");
        obj3.setStoreID("HYD");
        obj3.setBatchNo("batch1");
        obj3.setExpiryDate("21/02/2019");
        obj3.setPromoCode("testpromo");
        obj3.setScheme("testscheme");
        itemMasterList.add(obj1);
        itemMasterList.add(obj2);
        itemMasterList.add(obj3);
        return itemMasterList;
    }
}
