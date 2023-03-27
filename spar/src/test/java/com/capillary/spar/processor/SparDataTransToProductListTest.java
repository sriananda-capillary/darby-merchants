package com.capillary.spar.processor;
import com.sellerworx.Application;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SparDataTransToProductListTest extends BaseAPITest {

    private Exchange exchange;
    private Map<String, String> tenantConfigMap;
    @Autowired
    private CamelContext camelContext;

    @Autowired
    private SparDataTransToProductList sparDataTransToProductList;

    private final String productFileData = ensureFile("transformCSVToProductData.csv");

    @Before
    public void preset() {
        tenant = getTenant();
        exchange = getExchange(camelContext);
        tenant = getTenant();
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        tenantConfigMap = new HashMap<>();
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_ID, "abd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_KEY, "dfd");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_MERCHANT_CONSUMER_SECRET, "1fd000");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_HOST, "1000");
        tenantConfigMap.put(MJTenantConfigKeys.MJ_TIMEZONE,"Asia/Calcutta");
        exchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
        List<String> fileData = getFileData();
        exchange.getIn().setHeader(ExchangeHeaderKeys.CSV_HEADER.getKeyName(),getCSVHeader());
        ExchangeUtil.setBody(fileData, exchange);
    }

    @Test
    public void assertProductList()
    {
        sparDataTransToProductList.startProcess(exchange);
        List<Product> actualProductList = getActualProductList();
        List<Product> expectedProductList = ExchangeUtil.getBody(exchange, List.class);
        Assert.assertEquals(expectedProductList, actualProductList);
    }

    @Test
    public void assertBatchObject()
    {
        List<String> fileData = getFileData();
        String addData = "1112;3232";
        fileData.add(2, addData);
        ExchangeUtil.setBody(fileData, exchange);
        BatchProcessDetails batchData = new BatchProcessDetails();
        batchData.setTotalCount(3);
        ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAIL, batchData, exchange);
        sparDataTransToProductList.startProcess(exchange);
        BatchProcessDetails actualBatchProcessDetails = ExchangeHeaderKeys
                                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAIL, exchange);
        BatchProcessDetails expectedBatchProcessDetails = new BatchProcessDetails();
        expectedBatchProcessDetails.setValidationCount(1);
        List<FieldErrorModel> fieldErrorModelList = new ArrayList<>();
        fieldErrorModelList.add( new FieldErrorModel
                ("1112;3232","Value of the field {0} is invalid", ErrorCode.INVALID.toString()));
        expectedBatchProcessDetails.setFieldErrorModelList(fieldErrorModelList);
        expectedBatchProcessDetails.setFileName("SPAR_PRODUCT_FILE");
        expectedBatchProcessDetails.setTotalCount(2);
        Assert.assertEquals(expectedBatchProcessDetails.getFieldErrorModelList().get(0).getMessage(),
                actualBatchProcessDetails.getFieldErrorModelList().get(0).getMessage());
        Assert.assertEquals(expectedBatchProcessDetails.getFieldErrorModelList().get(0).getErrorCode(),
                actualBatchProcessDetails.getFieldErrorModelList().get(0).getErrorCode());
        Assert.assertEquals(expectedBatchProcessDetails.getFieldErrorModelList().get(0).getField(),
                actualBatchProcessDetails.getFieldErrorModelList().get(0).getField());
        Assert.assertEquals(expectedBatchProcessDetails.getTotalCount(), actualBatchProcessDetails.getTotalCount());
        Assert.assertEquals(expectedBatchProcessDetails.getFileName(), actualBatchProcessDetails.getFileName());
    }

    private List<Product> getActualProductList()
    {
        List<Product> productList = new ArrayList<>();
        Product product = new Product();
        product.setStdProductType("HSN");
        product.setProductType("P");
        product.setSku("100000019");
        product.setTaxCode("12");
        product.setStdProductCode(new BigDecimal("21069099"));
        productList.add(product);
        return productList;
    }

    private List<String> getFileData() {

        List<String> fileData = new ArrayList<>();
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(productFileData));
            String line = "";
            while ((line = br.readLine()) != null) {
                fileData.add(line);
            }
        }
        catch (IOException e)
        {
            log.error(e.getMessage());
        }

        return  fileData;
    }
    private String getCSVHeader()
    {
        return "ITEM;HSN_CODE;TAX_CODE";
    }
}
