package com.sellerworx.modules.apollo.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sellerworx.modules.martjack.frontend.response.Product;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.modules.apollo.models.ApolloItemMaster;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import com.sellerworx.modules.mapping.MappingService;
import com.sellerworx.modules.martjack.entity.ProductAttribute;
import com.sellerworx.modules.martjack.entity.ProductAttributes;
import com.sellerworx.modules.martjack.entity.frontapi.Categories;
import com.sellerworx.modules.martjack.frontend.services.MJFrontEndCatalogService;

@Component("ApolloItemTransformProcessor")
@Documented(description = "Transforms the item master entity from input file to mj specific entity",
        inBody = @KeyInfo(comment = "Input JsonObject", type = JSONObject.class),
        outBody = @KeyInfo(comment = "transformed productAttributes model", type = ProductAttributes.class))
public class ApolloItemTransformProcessor implements Processor {

    @Autowired
    MJFrontEndCatalogService mjFrontEndCatlogService;

    @Autowired
    MappingService mappingService;
    private static final Logger logger = LoggerFactory.getLogger(ApolloItemTransformProcessor.class);
    private static final String CATEGORY_LEVEL_NO = "1";
    private static final boolean INCLUDE_UNAVAILABLE = true;
    private static final String LANGUAGE_CODE = "en";
    private static final String PRODUCT_ATTRIBUTES = "ProductAttributes";
    String skuList = StringUtils.EMPTY;

    @Override
    public void process(Exchange exchange) throws Exception {
        ApolloItemMaster itemMasterObj = (ApolloItemMaster) exchange.getIn().getBody();
        logger.debug("processing item master obj {} ", itemMasterObj);
        ProductAttributes productAttributes = transformItemMasterData(itemMasterObj, exchange);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(productAttributes);
        JSONObject productJson = new JSONObject(jsonString);
        JSONObject productFirstLevel = new JSONObject();
        productFirstLevel.put(PRODUCT_ATTRIBUTES, productJson);
        exchange.getIn().setHeader(ApolloUtil.MJ_PRODUCT_ATTRIBUTE_ITEM_KEY, skuList);
        exchange.getIn().setBody(productFirstLevel);
    }

    private ProductAttributes transformItemMasterData(ApolloItemMaster itemMasterObj, Exchange exchange)
            throws Exception {
        String sku = StringUtils.EMPTY;
        ProductAttributes productAttributes = new ProductAttributes();
        List<ProductAttribute> productAttributeList = new ArrayList<>();
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
        if (StringUtils.isNotBlank(itemMasterObj.getMpclCode())) {
            sku = itemMasterObj.getMpclCode();
            String categoryID = getCategoryIDBySKU(sku);
            if (categoryID != null) {
                skuList = skuList + SymbolUtil.COMMA + sku;
                setProductAttributeByKeyName(itemMasterObj.getCategory(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_CATEGORY, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getPack(), categoryID, ApolloUtil.ITEM_MASTER_KEY_PACK, sku,
                        fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getGeneric(), categoryID, ApolloUtil.ITEM_MASTER_KEY_GENERIC,
                        sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getManufacturer(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_MANUFACTURER, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getDivision(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_DIVISION, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getBox_qty(), categoryID, ApolloUtil.ITEM_MASTER_KEY_BOX_QTY,
                        sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getCase_qty(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_CASE_QTY, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getApollo_code(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_APOLLO_CODE, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getScheme(), categoryID, ApolloUtil.ITEM_MASTER_KEY_SCHEME,
                        sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getBatchNo(), categoryID, ApolloUtil.ITEM_MASTER_KEY_BATCHNO,
                        sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getExpiryDate(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_EXPIRYDT, sku, fileName, productAttributeList);
                setProductAttributeByKeyName(itemMasterObj.getPromoCode(), categoryID,
                        ApolloUtil.ITEM_MASTER_KEY_PROMOCODE, sku, fileName, productAttributeList);
                logger.debug("processing productAttributeList {} ", productAttributeList);
                productAttributes.setProductAttributeList(productAttributeList);

            } else {
                String errorMessage =
                        "not able to retrieve categoryid using frontAPI for SKU" + sku + "in filename " + fileName;
                logger.error(errorMessage);
            }

        } else {
            String errorMessage = "sku is empty for object " + itemMasterObj.toString() + "in file " + fileName;
            logger.error(errorMessage);
        }
        return productAttributes;
    }

    private void setProductAttributeByKeyName(String value, String categoryID, String keyname, String sku,
            String fileName, List<ProductAttribute> productAttributeList) {
        ProductAttribute attribute = new ProductAttribute();
        if (StringUtils.isNotBlank(value)) {
            String attrID = getAttributeIDByCategory(categoryID, keyname);
            if (StringUtils.isNotBlank(attrID)) {
                attribute.setAttributeid(attrID);
                attribute.setAttributevalue(value);
                attribute.setSku(sku);
                if (attribute != null) {
                    logger.debug("adding the profileattribute json {} to the list", attribute);
                    productAttributeList.add(attribute);
                }
            } else {
                String errorMessage = "mapping not found for sku,categoryID,keyname"
                                      + sku
                                      + SymbolUtil.COMMA
                                      + categoryID
                                      + SymbolUtil.COMMA
                                      + keyname;
                logger.error(errorMessage);
            }
        } else {
            String errorMessage = "keyname" + keyname + "is not present in the filename" + fileName + "for sku" + sku;
            logger.error(errorMessage);
        }
    }

    private String getAttributeIDByCategory(String categoryID, String attrName) {
        try {
            Long tenantID = RequestContext.getTenantInfo().getId();
            String attrID = mappingService.map(tenantID, categoryID, attrName, ITEM_TYPE.MJ_PRODUCT_ATTRIBUTE);
            return attrID;
        } catch (Exception ex) {
            String errorMessage =
                    "mapping not found for categoryID,attrName" + categoryID + SymbolUtil.COMMA + attrName;
            logger.error(errorMessage);
        }
        return null;
    }

    private String getCategoryIDBySKU(String sku) throws Exception {
        Product product =
                (Product) mjFrontEndCatlogService.getAllProductsUsingSku(sku, INCLUDE_UNAVAILABLE, LANGUAGE_CODE);
        if (product != null && CollectionUtils.isNotEmpty(product.getCategories())) {
            Optional<Categories> optionalProduct = product
                    .getCategories()
                    .stream()
                    .filter(p -> p.getLevel().equalsIgnoreCase(CATEGORY_LEVEL_NO))
                    .findFirst();
            Categories filteredProduct = optionalProduct.orElse(null);
            if (filteredProduct != null) {
                return filteredProduct.getCategoryCode();
            }
        } else {
            String errorMessage =
                    "product obj is null and not able to fetch category details using getallproductsusingsku api for sku"
                                  + sku;
            logger.error(errorMessage);
        }
        return null;
    }
}
