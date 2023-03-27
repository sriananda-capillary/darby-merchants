package com.sellerworx.modules.apollo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApolloStockTransformProcessorTest extends BaseAPITest {

    private static final Logger logger = LoggerFactory.getLogger(ApolloStockTransformProcessorTest.class);

    @Autowired
    private static CamelContext camelContext;

    @Autowired
    private ApolloStockTransformProcessor apolloStockTransformProcessor;

    private static Exchange camelExchange;

    private static String fileName = "apollo_item_stock.json";

    @Before
    public void buildExchange() throws Exception {
        camelExchange = getExchange(camelContext);
        fileName = ensureFile(fileName, "src/test/resources");

        JSONObject responseJson = new JSONObject(parseFile(fileName));
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, fileName);
        camelExchange.getIn().setBody(responseJson);
    }

    @Test
    public void checkForValidInput() throws Exception {
        apolloStockTransformProcessor.process(camelExchange);
        List<Product> actual = (List<Product>) camelExchange.getIn().getBody();
        List<Product> expected = getExpectedProductDetails();

        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertEquals(expected, actual);

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(0, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(0, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    @Test
    public void skipNodeIfInvalidStockValue() throws Exception {
        JSONObject jsonObject = new JSONObject("{\n"
                                               + "  \"Product\": [\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1627\",\n"
                                               + "      \"Closing_Stock\": \"100\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1631\",\n"
                                               + "      \"Closing_Stock\": \"Invalid\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1632\",\n"
                                               + "      \"Closing_Stock\": \"300\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    }\n"
                                               + "  ]\n"
                                               + "}");
        camelExchange.getIn().setBody(jsonObject);
        apolloStockTransformProcessor.process(camelExchange);
        List<Product> actual = (List<Product>) camelExchange.getIn().getBody();
        List<Product> expected = getExpectedProductDetails();
        expected.remove(1);

        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertEquals(expected, actual);

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(1, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(1, actualFtpFileDetails.getFieldErrorModelList().size());
        Assert.assertEquals("Closing_Stock value is not a valid decimal with sku MLP1631",
                actualFtpFileDetails.getFieldErrorModelList().get(0).getMessage());
    }

    @Test
    public void roundIfStockIsDecimalAndLessThanZero() throws Exception {
        JSONObject jsonObject = new JSONObject("{\n"
                                               + "  \"Product\": [\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1627\",\n"
                                               + "      \"Closing_Stock\": \"100.00\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1631\",\n"
                                               + "      \"Closing_Stock\": \"-34\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1632\",\n"
                                               + "      \"Closing_Stock\": \"300\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    }\n"
                                               + "  ]\n"
                                               + "}");
        camelExchange.getIn().setBody(jsonObject);
        apolloStockTransformProcessor.process(camelExchange);
        List<Product> actual = (List<Product>) camelExchange.getIn().getBody();

        List<Product> expected = new ArrayList<>();
        Product product1 = new Product();
        Product product2 = new Product();
        Product product3 = new Product();
        product1.setSku("MLP1627");
        product1.setVariantSku("");
        product1.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product1.addCustomField(ApolloUtil.STOCK, 100);

        product2.setSku("MLP1631");
        product2.setVariantSku("");
        product2.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product2.addCustomField(ApolloUtil.STOCK, 0);

        product3.setSku("MLP1632");
        product3.setVariantSku("");
        product3.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product3.addCustomField(ApolloUtil.STOCK, 300);

        expected.add(product1);
        expected.add(product2);
        expected.add(product3);

        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertEquals(expected, actual);

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(0, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(0, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    @Test
    public void roundingUpIfStockValueIsDecimal() throws Exception {
        JSONObject jsonObject = new JSONObject("{\n"
                                               + "  \"Product\": [\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1627\",\n"
                                               + "      \"Closing_Stock\": \"100.5\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1631\",\n"
                                               + "      \"Closing_Stock\": \"200.7\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    },\n"
                                               + "    {\n"
                                               + "      \"MLPL_Code\": \"MLP1632\",\n"
                                               + "      \"Closing_Stock\": \"300.2\",\n"
                                               + "      \"Store_ID\": \"HYD\"\n"
                                               + "    }\n"
                                               + "  ]\n"
                                               + "}");
        camelExchange.getIn().setBody(jsonObject);
        apolloStockTransformProcessor.process(camelExchange);
        List<Product> actual = (List<Product>) camelExchange.getIn().getBody();

        List<Product> expected = new ArrayList<>();
        Product product1 = new Product();
        Product product2 = new Product();
        Product product3 = new Product();
        product1.setSku("MLP1627");
        product1.setVariantSku("");
        product1.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product1.addCustomField(ApolloUtil.STOCK, 101);

        product2.setSku("MLP1631");
        product2.setVariantSku("");
        product2.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product2.addCustomField(ApolloUtil.STOCK, 201);

        product3.setSku("MLP1632");
        product3.setVariantSku("");
        product3.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product3.addCustomField(ApolloUtil.STOCK, 300);

        expected.add(product1);
        expected.add(product2);
        expected.add(product3);

        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertEquals(expected, actual);

        BatchProcessDetails actualFtpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, camelExchange);
        Assert.assertEquals(3, actualFtpFileDetails.getTotalCount());
        Assert.assertEquals(0, actualFtpFileDetails.getValidationCount());
        Assert.assertEquals(0, actualFtpFileDetails.getFieldErrorModelList().size());
    }

    private List<Product> getExpectedProductDetails() {
        List<Product> productList = new ArrayList<>();

        Product product1 = new Product();
        product1.setSku("MLP1627");
        product1.setVariantSku("");
        product1.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product1.addCustomField(ApolloUtil.STOCK, 100);

        productList.add(product1);

        Product product2 = new Product();
        product2.setSku("MLP1631");
        product2.setVariantSku("");
        product2.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product2.addCustomField(ApolloUtil.STOCK, 200);

        productList.add(product2);

        Product product3 = new Product();
        product3.setSku("MLP1632");
        product3.setVariantSku("");
        product3.addCustomField(ApolloUtil.LOCATION_REF_CODE, "HYD");
        product3.addCustomField(ApolloUtil.STOCK, 300);

        productList.add(product3);
        return productList;
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfBodyIsNull() throws Exception {
        JsonNode jsonObject = null;
        camelExchange.getIn().setBody(jsonObject);
        apolloStockTransformProcessor.process(camelExchange);
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfProductNodeArrayIsEmpty() throws Exception {
        JSONObject inputJson = new JSONObject("{  \n" + "   \"Product\":[]\n" + "}");
        camelExchange.getIn().setBody(inputJson);
        apolloStockTransformProcessor.process(camelExchange);
    }

    @Test(expected = DarbyException.class)
    public void throwExceptionIfSchemaIsNotValid() throws Exception {
        JSONObject inputJson = new JSONObject("{  \n" + "   \"InvalidRoot\":[]\n" + "}");
        camelExchange.getIn().setBody(inputJson);
        apolloStockTransformProcessor.process(camelExchange);
    }
}
