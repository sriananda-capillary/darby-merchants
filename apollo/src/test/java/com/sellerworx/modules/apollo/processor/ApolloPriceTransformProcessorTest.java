package com.sellerworx.modules.apollo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApolloPriceTransformProcessorTest extends BaseAPITest {

    private static final Logger logger = LoggerFactory.getLogger(ApolloPriceTransformProcessorTest.class);

    @Autowired
    private static CamelContext camelContext;

    @Autowired
    private ApolloPriceTransformProcessor apolloPriceTransformProcessor;

    private static Exchange camelExchange;

    private static String fileName = "apollo_item_price.json";

    @Before
    public void buildExchange() throws Exception {
        camelExchange = getExchange(camelContext);
        fileName = ensureFile(fileName, "src/test/resources");

        JSONObject responseJson = new JSONObject(parseFile(fileName));
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, fileName);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, getTenant());
        RequestContext.getTenantInfo().setId(Long.parseLong("1"));
        camelExchange.getIn().setBody(responseJson);
    }

    @Test
    public void checkForValidInput() throws Exception {
        apolloPriceTransformProcessor.process(camelExchange);
        List<List<Product>> actual = (List<List<Product>>) camelExchange.getIn().getBody();
        List<List<Product>> expected = getExpectedProductDetails();

        Assert.assertEquals(4, actual.size());
        Assert.assertEquals(3, actual.get(0).size());
        Assert.assertEquals(3, actual.get(1).size());
        Assert.assertEquals(3, actual.get(2).size());
        Assert.assertEquals(3, actual.get(3).size());

        Assert.assertEquals(expected.get(0), actual.get(0));
        Assert.assertEquals(expected.get(1), actual.get(1));
        Assert.assertEquals(expected.get(2), actual.get(2));
        Assert.assertEquals(expected.get(3), actual.get(3));

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(0, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(0, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    @Test
    public void checkWithInvalidNonNumericAndBlankInput() throws Exception {
        String jsonString = "{\n"
                            + "  \"Price\": [\n"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1631\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"invalidValue\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    },"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1632\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"100\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"200\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    }"
                            + "]}";
        JSONObject inputJson = new JSONObject(jsonString);
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
        List<List<Product>> actual = (List<List<Product>>) camelExchange.getIn().getBody();
        List<List<Product>> expected = new ArrayList<>();

        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();

        ispProductList.add(getProduct("MLP1631", "10.50", "Institutional_Sale_Price"));
        dspProductList.add(getProduct("MLP1631", "12.00", "Distributor_Sale_Price"));

        bspProductList.add(getProduct("MLP1632", "100", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1632", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1632", "200", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1632", "12.00", "Distributor_Sale_Price"));

        expected.add(bspProductList);
        expected.add(ispProductList);
        expected.add(tspProductList);
        expected.add(dspProductList);

        Assert.assertEquals(4, actual.size());
        Assert.assertEquals(1, actual.get(0).size());
        Assert.assertEquals(2, actual.get(1).size());
        Assert.assertEquals(1, actual.get(2).size());
        Assert.assertEquals(2, actual.get(3).size());

        Assert.assertEquals(expected.get(0), actual.get(0));
        Assert.assertEquals(expected.get(1), actual.get(1));
        Assert.assertEquals(expected.get(2), actual.get(2));
        Assert.assertEquals(expected.get(3), actual.get(3));

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(2, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(2, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(2, actualFtpFileDetails.getFieldErrorModelList().size());
        Assert.assertEquals("Base_Sale_Price value is not a valid decimal with sku MLP1631",
                actualFtpFileDetails.getFieldErrorModelList().get(0).getMessage());
        Assert.assertEquals("Trade_Sale_Price value is not a valid decimal with sku MLP1631",
                actualFtpFileDetails.getFieldErrorModelList().get(1).getMessage());
    }

    @Test
    public void skipCompleteNodeIfSkuIsInvalid() throws Exception {
        String jsonString = "{\n"
                            + "  \"Price\": [\n"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"invalidValue\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    },"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1632\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"100\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"200\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    }"
                            + "]}";
        JSONObject inputJson = new JSONObject(jsonString);
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
        List<List<Product>> actual = (List<List<Product>>) camelExchange.getIn().getBody();
        List<List<Product>> expected = new ArrayList<>();

        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();

        bspProductList.add(getProduct("MLP1632", "100", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1632", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1632", "200", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1632", "12.00", "Distributor_Sale_Price"));

        expected.add(bspProductList);
        expected.add(ispProductList);
        expected.add(tspProductList);
        expected.add(dspProductList);

        Assert.assertEquals(4, actual.size());
        Assert.assertEquals(1, actual.get(0).size());
        Assert.assertEquals(1, actual.get(1).size());
        Assert.assertEquals(1, actual.get(2).size());
        Assert.assertEquals(1, actual.get(3).size());

        Assert.assertEquals(expected.get(0), actual.get(0));
        Assert.assertEquals(expected.get(1), actual.get(1));
        Assert.assertEquals(expected.get(2), actual.get(2));
        Assert.assertEquals(expected.get(3), actual.get(3));

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(2, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
        Assert.assertEquals("MLPL_Code is manadatory/should not be blank/invalid for node with sku ",
                actualFtpFileDetails.getFieldErrorModelList().get(0).getMessage());
    }

    @Test
    public void skipCompleteNodeIfMrpIsInvalid() throws Exception {
        String jsonString = "{\n"
                            + "  \"Price\": [\n"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1631\",\n"
                            + "      \"MRP\": \"Invalid\",\n"
                            + "      \"Base_Sale_Price\": \"45\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"67\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    },"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1632\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"100\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"200\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    }"
                            + "]}";
        JSONObject inputJson = new JSONObject(jsonString);
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
        List<List<Product>> actual = (List<List<Product>>) camelExchange.getIn().getBody();
        List<List<Product>> expected = new ArrayList<>();

        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();

        bspProductList.add(getProduct("MLP1632", "100", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1632", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1632", "200", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1632", "12.00", "Distributor_Sale_Price"));

        expected.add(bspProductList);
        expected.add(ispProductList);
        expected.add(tspProductList);
        expected.add(dspProductList);

        Assert.assertEquals(4, actual.size());
        Assert.assertEquals(1, actual.get(0).size());
        Assert.assertEquals(1, actual.get(1).size());
        Assert.assertEquals(1, actual.get(2).size());
        Assert.assertEquals(1, actual.get(3).size());

        Assert.assertEquals(expected.get(0), actual.get(0));
        Assert.assertEquals(expected.get(1), actual.get(1));
        Assert.assertEquals(expected.get(2), actual.get(2));
        Assert.assertEquals(expected.get(3), actual.get(3));

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(2, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
        Assert.assertEquals("MRP value is not a valid decimal with sku MLP1631",
                actualFtpFileDetails.getFieldErrorModelList().get(0).getMessage());
    }

    @Test
    public void skipCompleteNodeIfLocationCodeIsInvalid() throws Exception {
        String jsonString = "{\n"
                            + "  \"Price\": [\n"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1631\",\n"
                            + "      \"MRP\": \"12.23\",\n"
                            + "      \"Base_Sale_Price\": \"34.45\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"13.34\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"\"\n"
                            + "    },"
                            + "    {\n"
                            + "      \"MLPL_Code\": \"MLP1632\",\n"
                            + "      \"MRP\": \"25.00\",\n"
                            + "      \"Base_Sale_Price\": \"100\",\n"
                            + "      \"Institutional_Sale_Price\": \"10.50\",\n"
                            + "      \"Trade_Sale_Price\": \"200\",\n"
                            + "      \"Distributor_Sale_Price\": \"12.00\",\n"
                            + "      \"Store_ID\": \"HYD\"\n"
                            + "    }"
                            + "]}";
        JSONObject inputJson = new JSONObject(jsonString);
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
        List<List<Product>> actual = (List<List<Product>>) camelExchange.getIn().getBody();
        List<List<Product>> expected = new ArrayList<>();

        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();

        bspProductList.add(getProduct("MLP1632", "100", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1632", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1632", "200", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1632", "12.00", "Distributor_Sale_Price"));

        expected.add(bspProductList);
        expected.add(ispProductList);
        expected.add(tspProductList);
        expected.add(dspProductList);

        Assert.assertEquals(4, actual.size());
        Assert.assertEquals(1, actual.get(0).size());
        Assert.assertEquals(1, actual.get(1).size());
        Assert.assertEquals(1, actual.get(2).size());
        Assert.assertEquals(1, actual.get(3).size());

        Assert.assertEquals(expected.get(0), actual.get(0));
        Assert.assertEquals(expected.get(1), actual.get(1));
        Assert.assertEquals(expected.get(2), actual.get(2));
        Assert.assertEquals(expected.get(3), actual.get(3));

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(2, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
        Assert.assertEquals("Store_ID is manadatory/should not be blank/invalid for node with sku MLP1631",
                actualFtpFileDetails.getFieldErrorModelList().get(0).getMessage());
    }

    private List<List<Product>> getExpectedProductDetails() {
        List<List<Product>> userGroupPriceList = new ArrayList<>();
        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();
        List<Product> productList = new ArrayList<>();
        bspProductList.add(getProduct("MLP1631", "10.00", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1631", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1631", "14.00", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1631", "12.00", "Distributor_Sale_Price"));

        bspProductList.add(getProduct("MLP1632", "10.00", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1632", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1632", "14.00", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1632", "12.00", "Distributor_Sale_Price"));

        bspProductList.add(getProduct("MLP1630", "10.00", "Base_Sale_Price"));
        ispProductList.add(getProduct("MLP1630", "10.50", "Institutional_Sale_Price"));
        tspProductList.add(getProduct("MLP1630", "14.00", "Trade_Sale_Price"));
        dspProductList.add(getProduct("MLP1630", "12.00", "Distributor_Sale_Price"));

        userGroupPriceList.add(bspProductList);
        userGroupPriceList.add(ispProductList);
        userGroupPriceList.add(tspProductList);
        userGroupPriceList.add(dspProductList);

        return userGroupPriceList;
    }

    private Product getProduct(String sku, String price, String field) {
        Product product = new Product();
        product.setSku(sku);
        product.setMrp(new BigDecimal("25.00"));
        product.setPrice(new BigDecimal(price));
        product.setVariantSku("");
        product.addCustomField("quantity", "1");
        product.addCustomField("locationReferenceCode", getPriceListRefCode(field));
        return product;
    }

    private String getPriceListRefCode(String userGroup) {
        String priceListRefCode = "HYD";
        switch (userGroup.toUpperCase()) {
            case "BASE_SALE_PRICE":
                priceListRefCode += "BSP";
                break;
            case "INSTITUTIONAL_SALE_PRICE":
                priceListRefCode += "ISP";
                break;
            case "TRADE_SALE_PRICE":
                priceListRefCode += "TSP";
                break;
            case "DISTRIBUTOR_SALE_PRICE":
                priceListRefCode += "DSP";
                break;
        }
        return priceListRefCode;
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfBodyIsNull() throws Exception {
        JsonNode jsonObject = null;
        camelExchange.getIn().setBody(jsonObject);
        apolloPriceTransformProcessor.process(camelExchange);
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfPriceNodeArrayIsEmpty() throws Exception {
        JSONObject inputJson = new JSONObject("{  \n" + "   \"Price\":[]\n" + "}");
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfSchemaIsNotValid() throws Exception {
        JSONObject inputJson = new JSONObject("{  \n" + "   \"InvalidRoot\":[]\n" + "}");
        camelExchange.getIn().setBody(inputJson);
        apolloPriceTransformProcessor.process(camelExchange);
    }
}
