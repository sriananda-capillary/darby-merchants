package com.sellerworx.modules.apollo.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mj.client.frontend.util.FrontEndKeys;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mj.client.frontend.MJFrontEndCatalogApiClient;
import com.sellerworx.BaseAPITest;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.modules.apollo.models.ApolloItemMaster;
import com.sellerworx.modules.martjack.entity.ProductAttribute;
import com.sellerworx.modules.martjack.entity.ProductAttributes;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApolloItemTransformProcessorTest extends BaseAPITest {

    @Autowired
    ApolloItemTransformProcessor apolloItemTransformProcessor;

    private static Exchange camelExchange;
    private static String fileName = "apollo_item_master.json";

    @Autowired
    CamelContext context;

    @MockBean
    MJFrontEndCatalogApiClient mjFrontEndCatalogApiClient;

    private static final String MJ_VALID_SKU = "MPL1627";
    private static final String MJ_INVALID_SKU = "MPL1627567";
    private static final boolean INCLUDE_UNAVAILABLE = true;
    private static final String LANGUAGE_CODE = "en";

    private Map<String, String> tenantConfigMap = new HashMap<String, String>();

    @Before
    public void buildExchange() {
        camelExchange = getExchange(context);
        tenant = getTenant();
        tenantConfigMap.put(TenantConfigKeys.MJ_MERCHANT_ID, "merchantID");
        tenantConfigMap.put(TenantConfigKeys.MJ_FRONT_API_HOST, "hostname");
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.FILENAME, fileName);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT, tenant);
        camelExchange.getIn().setHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP, tenantConfigMap);
        RequestContext.getTenantInfo().setId(tenant.getId());
        camelExchange.getIn().setBody(getInputData());
    }

    @Test
    public void checkForValidInput() throws Exception {
        buildApolloItemMappings();
        mockGetProductsByValidSKUAPIResponse();
        mockGetProductsByInValidSKUAPIResponse();
        apolloItemTransformProcessor.process(camelExchange);
        JSONObject actualProdAttrList = (JSONObject) camelExchange.getIn().getBody();
        JSONObject expectedProdAttrList = getExpectedProductAttributes();
        JSONAssert.assertEquals(actualProdAttrList, expectedProdAttrList, false);
    }

    private void mockGetProductsByValidSKUAPIResponse() {
        ResponseEntity<String> skuDetails =
                new ResponseEntity<>("{\n"
                                     + "    \"resource\": [\n"
                                     + "        {\n"
                                     + "            \"productId\": 14569753,\n"
                                     + "            \"title\": \"DC GAUZETHAN 90CM*10MTRS NON STERILE\",\n"
                                     + "            \"shortDescription\": \"<h3 style=\\\"color: rgb(255, 0, 0); font-weight: normal;\\\">MANUFACTURER BY</h3> <ul><li>PREMIER ENTERPRISES</li></ul>\",\n"
                                     + "            \"longDescription\": \"\",\n"
                                     + "            \"imageAltText\": \"\",\n"
                                     + "            \"sku\": \"MLP1627\",\n"
                                     + "            \"mrp\": 220,\n"
                                     + "            \"webPrice\": 122,\n"
                                     + "            \"catalogCode\": \"P\",\n"
                                     + "            \"catalogSequence\": 0,\n"
                                     + "            \"productType\": \"P\",\n"
                                     + "            \"locationBased\": 1,\n"
                                     + "            \"offerDescription\": \"\",\n"
                                     + "            \"barcode\": null,\n"
                                     + "            \"imagePaths\": {\n"
                                     + "                \"resizeImagePath\": \"/Images/ProductImages/Source/\",\n"
                                     + "                \"imageCDNPath\": null,\n"
                                     + "                \"imageContainerName\": null,\n"
                                     + "                \"largeImagePath\": \"/Images/ProductImages/Large/\",\n"
                                     + "                \"smallImagePath\": \"/Images/ProductImages/Small/\",\n"
                                     + "                \"swatchImagePath\": \"/Images/ProductImages/Swatch/Large_Icon/\",\n"
                                     + "                \"userImagePath\": \"/Images/userimages/\",\n"
                                     + "                \"imageAutoResize\": true\n"
                                     + "            },\n"
                                     + "            \"images\": {\n"
                                     + "                \"largeimage\": [\n"
                                     + "                    {\n"
                                     + "                        \"fileType\": \"AdditionalImage\",\n"
                                     + "                        \"fileName\": \"No_image.jpg\",\n"
                                     + "                        \"sequence\": 1,\n"
                                     + "                        \"variantProductId\": 0,\n"
                                     + "                        \"variantValueId\": null\n"
                                     + "                    }\n"
                                     + "                ],\n"
                                     + "                \"smallimage\": [\n"
                                     + "                    {\n"
                                     + "                        \"fileType\": \"AdditionalImage\",\n"
                                     + "                        \"fileName\": \"No_image.jpg\",\n"
                                     + "                        \"sequence\": 1,\n"
                                     + "                        \"variantProductId\": 0,\n"
                                     + "                        \"variantValueId\": null\n"
                                     + "                    }\n"
                                     + "                ]\n"
                                     + "            },\n"
                                     + "            \"availability\": true,\n"
                                     + "            \"archived\": 0,\n"
                                     + "            \"discount\": \"45.00\",\n"
                                     + "            \"hasVariant\": false,\n"
                                     + "            \"periodicityType\": \"\",\n"
                                     + "            \"periodicityRange\": \"\",\n"
                                     + "            \"isexpired\": false,\n"
                                     + "            \"startDate\": \"1900-01-01T00:00:00+05:30\",\n"
                                     + "            \"endDate\": \"1900-01-01T00:00:00+05:30\",\n"
                                     + "            \"productAttributes\": [],\n"
                                     + "            \"productVariants\": [],\n"
                                     + "            \"categories\": [\n"
                                     + "                {\n"
                                     + "                    \"level\": 2,\n"
                                     + "                    \"categoryName\": \"GAUZE ITEMS\",\n"
                                     + "                    \"categoryCode\": \"CU00379521\",\n"
                                     + "                    \"childCategoriesId\": \"NA\",\n"
                                     + "                    \"parentCategoryId\": \"CU00379507\",\n"
                                     + "                    \"lineage\": \"/CU00379507/CU00379521/\",\n"
                                     + "                    \"referenceCode\": \"CU00379521\",\n"
                                     + "                    \"minCommissionPerc\": null,\n"
                                     + "                    \"isProductType\": false,\n"
                                     + "                    \"isMainCategory\": 0\n"
                                     + "                },\n"
                                     + "                {\n"
                                     + "                    \"level\": 1,\n"
                                     + "                    \"categoryName\": \"CSSD Consumables\",\n"
                                     + "                    \"categoryCode\": \"CU00379507\",\n"
                                     + "                    \"childCategoriesId\": \"CU00379521\",\n"
                                     + "                    \"parentCategoryId\": \"NA\",\n"
                                     + "                    \"lineage\": \"/CU00379507/\",\n"
                                     + "                    \"referenceCode\": \"CU00379507\",\n"
                                     + "                    \"minCommissionPerc\": null,\n"
                                     + "                    \"isProductType\": false,\n"
                                     + "                    \"isMainCategory\": 1\n"
                                     + "                }\n"
                                     + "            ],\n"
                                     + "            \"tags\": [],\n"
                                     + "            \"seoInfo\": {\n"
                                     + "                \"description\": \"\",\n"
                                     + "                \"keywords\": \"\",\n"
                                     + "                \"title\": \"\",\n"
                                     + "                \"urlKey\": \"\"\n"
                                     + "            },\n"
                                     + "            \"brand\": {\n"
                                     + "                \"brandId\": 321969,\n"
                                     + "                \"brandName\": \"DOCTOR'S CHOICE\",\n"
                                     + "                \"rank\": 160289000,\n"
                                     + "                \"oldBrandId\": null\n"
                                     + "            }\n"
                                     + "        }\n"
                                     + "    ],\n"
                                     + "    \"requestMetadata\": {\n"
                                     + "        \"completedOn\": \"2019-02-12T17:59:03.848+0000\",\n"
                                     + "        \"httpStatus\": \"OK\",\n"
                                     + "        \"executionTimeInMs\": 33,\n"
                                     + "        \"languageCode\": \"en\",\n"
                                     + "        \"currentPageSize\": 100,\n"
                                     + "        \"currentPageOffset\": 0,\n"
                                     + "        \"nextPageOffset\": 100\n"
                                     + "    }\n"
                                     + "}",
                        HttpStatus.OK);
        MultiValueMap<String, String> filterParam = new LinkedMultiValueMap<>();
        filterParam.set(FrontEndKeys.SKUS, MJ_VALID_SKU);
        filterParam.set(FrontEndKeys.INCLUDE_UNAVAILABLE, Boolean.toString(INCLUDE_UNAVAILABLE));
        filterParam.set(FrontEndKeys.LANGUAGE_CODE, LANGUAGE_CODE);
        Mockito.when(mjFrontEndCatalogApiClient.getAllProducts(Mockito.eq(filterParam))).thenReturn(skuDetails);
    }

    private void mockGetProductsByInValidSKUAPIResponse() {
        ResponseEntity<String> skuDetails = new ResponseEntity<String>(new JSONObject().toString(), HttpStatus.OK);

        MultiValueMap<String, String> filterParam = new LinkedMultiValueMap<>();
        filterParam.set(FrontEndKeys.SKUS, MJ_INVALID_SKU);
        filterParam.set(FrontEndKeys.INCLUDE_UNAVAILABLE, Boolean.toString(INCLUDE_UNAVAILABLE));
        filterParam.set(FrontEndKeys.LANGUAGE_CODE, LANGUAGE_CODE);
        Mockito.when(mjFrontEndCatalogApiClient.getAllProducts(Mockito.eq(filterParam))).thenReturn(skuDetails);
    }

    private JSONObject getExpectedProductAttributes() throws JsonProcessingException {
        ProductAttributes attributes = new ProductAttributes();
        List<ProductAttribute> attrList = new ArrayList<>();
        attrList.add(getProductAttribute("CU00379506_01", "ELECTRODES"));
        attrList.add(getProductAttribute("CU00379506_02", "100"));
        attrList.add(getProductAttribute("CU00379506_03", "SURGICAL INSTRUMENTS"));
        attrList.add(getProductAttribute("CU00379506_04", "3M INDIA"));
        attrList.add(getProductAttribute("CU00379506_05", "HEART CARE"));
        attrList.add(getProductAttribute("CU00379506_06", "100"));
        attrList.add(getProductAttribute("CU00379506_07", "8000"));
        attrList.add(getProductAttribute("CU00379506_08", "ECG0008"));
        attrList.add(getProductAttribute("CU00379506_09", "testscheme"));
        attrList.add(getProductAttribute("CU00379506_10", "batch1"));
        attrList.add(getProductAttribute("CU00379506_11", "21/02/2019"));
        attrList.add(getProductAttribute("CU00379506_12", "testpromo"));
        attributes.setProductAttributeList(attrList);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(attributes);
        JSONObject attrJSON = new JSONObject(jsonString);
        JSONObject productFirstLevel = new JSONObject();
        productFirstLevel.put("ProductAttributes", attrJSON);
        return productFirstLevel;
    }

    private ProductAttribute getProductAttribute(String attributeID, String attributeValue) {
        ProductAttribute productAttribute = new ProductAttribute();
        productAttribute.setSku("MPL1627");
        productAttribute.setAttributeid(attributeID);
        productAttribute.setAttributevalue(attributeValue);
        return productAttribute;
    }

    private void buildApolloItemMappings() {
        buildItemMappings("CC_Category", "CU00379507", "CU00379506_01", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Category");
        buildItemMappings("CC_Pack", "CU00379507", "CU00379506_02", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Pack");
        buildItemMappings("CC_Generic", "CU00379507", "CU00379506_03", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Generic");
        buildItemMappings("CC_Manufacturer", "CU00379507", "CU00379506_04", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE,
                "Manufacturer");
        buildItemMappings("CC_ Division", "CU00379507", "CU00379506_05", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Division");
        buildItemMappings("CC_BoxQty", "CU00379507", "CU00379506_06", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Box_Qty");
        buildItemMappings("CC_CaseQty", "CU00379507", "CU00379506_07", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Case_Qty");
        buildItemMappings("CC_ApolloCode", "CU00379507", "CU00379506_08", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE,
                "Apollo_Code");
        buildItemMappings("CC_Scheme", "CU00379507", "CU00379506_09", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "Scheme");
        buildItemMappings("CC_BatchNo", "CU00379507", "CU00379506_10", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "BatchNo");
        buildItemMappings("CC_ExpiryDt", "CU00379507", "CU00379506_11", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "ExpiryDt");
        buildItemMappings("CC_PromoCode", "CU00379507", "CU00379506_12", ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE, "PromoCode");
    }

    private ApolloItemMaster getInputData() {
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
        return obj1;
    }

}
