package com.sellerworx.spar.processor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.ExchangePropertyKeys;
import com.sellerworx.darby.util.ProductCustomKeys;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SparPriceProductTransformToProductEntityProcessorTest extends BaseAPITest {
    @Autowired
    private CamelContext camelContext;
    private Exchange camelExchange = getExchange(camelContext);
    private Tenant tenant;
    private Map<String, String> tenantConfigMap;
    @Autowired
    private SparPriceProductTransformToProductEntityProcessor transformToProductEntityProcessor;

    private static final String PRICE_FILE = "src/test/resources/spar_price_sample.csv";

    @Before
    public void setPreTest() {
        tenant = getTenant();
        tenantConfigMap = getTenantConfigMap();
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.CSV_HEADER.getKeyName(),
                                        "ITEM;CURRENCY;EFFECTIVE_DATE;END_DATE;LOCATION;SELLING_RETAIL;" +
                                        "SELLING_UOM;UNIT_MRP;BARCODE;PROMO_CHECK;VAT%;TAX_VALUE;" +
                                        "BENEFIT_TYPE;PRODUCT_ON_BOGO;PROMO_MESSAGE;ASSOCIATED_FREE_PRODUCT;" +
                                        "BEST_DEAL;REGULAR_OFFER;COMBI_OFFER;PERCENTAGE_DISCOUNT;RANKING");
    }

    private List<String> getListOfString() {
        return readCSVFile(PRICE_FILE);
    }

    @Test
    public void assertValidAndInvalidProducts() {
        camelExchange.getIn().setBody(getListOfString());
        camelExchange.setProperty(ExchangePropertyKeys.EXCHANGE_CAMELSPLITINDEX, 0);
        camelExchange.getIn().setHeader(Exchange.FILE_NAME_CONSUMED, "filename");
        BatchProcessDetails batchDetails = new BatchProcessDetails();
        batchDetails.setTotalCount(5);
        ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAIL, batchDetails, camelExchange);
        transformToProductEntityProcessor.startProcess(camelExchange);
        List<Product> productList = (List<Product>) camelExchange.getIn().getBody();


        Assert.assertEquals("sku1", productList.get(0).getSku());
        Assert.assertEquals(null, productList.get(0).getVariantSku());
        Assert.assertEquals(BigDecimal.valueOf(24.0), productList.get(0).getPrice());
        Assert.assertEquals(BigDecimal.valueOf(34.0), productList.get(0).getMrp());
        Assert.assertEquals("loc1",
                            productList.get(0).getCustomField("locationReferenceCode"));
        Assert.assertEquals("1",
                            productList.get(0).getCustomField(ProductCustomKeys.QUANTITY));


        BatchProcessDetails details = (BatchProcessDetails) camelExchange
                .getIn()
                .getHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS);
        Assert.assertTrue(details.getTotalCount() == 4);
        Assert.assertTrue(details.getFieldErrorModelList().size() == 3);
        Assert.assertEquals(
                "row number: 1 data is blank column no.5 ,validating on: [sku2, INR, 03-Mar-20, 05-Mar-20," +
                " loc1, , EA, , 8900000000000, N, 18, 403.074, , FALSE, , , FALSE, FALSE, FALSE, , 2005]",
                details.getFieldErrorModelList().get(0).getMessage());
        Assert.assertEquals(
                "row number: 2 data is blank column no.7 ,validating on: [sku3, INR, 06-Mar-20, 31-Dec-30," +
                " loc1, 10, EA, , 700000000000, N, 18, 233.82, , FALSE, , , FALSE, FALSE, FALSE, , 2010]",
                details.getFieldErrorModelList().get(1).getMessage());
        Assert.assertEquals(
                "row number: 3 data is blank column no.5 ,validating on: [sku4, INR, 06-Mar-20, 31-Dec-30," +
                " loc1, , EA, 34, 700000000000, N, 18, 233.82, , FALSE, , , FALSE, FALSE, FALSE, , 2010]",
                details.getFieldErrorModelList().get(2).getMessage());

    }

    private Map<String, String> getTenantConfigMap() {
        Map<String, String> configMap = new HashMap<>();
        return configMap;
    }
}
