package com.sellerworx.modules.apollo.processor;

import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.apollo.enums.PRICELISTCODE;
import com.sellerworx.modules.apollo.util.ApolloUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component("ApolloPriceTransformProcessor")
@Documented(description = "Transforms the price entity from input file to mj specific entity",
        inBody = @KeyInfo(type = JSONObject.class, comment = "Input JsonObject"),
        inHeaders = { @KeyInfo(comment = "Input File Name", name = ExchangeHeaderKeys.FILENAME) },
        outBody = @KeyInfo(type = List.class, comment = "transformed mj specific list of products"))
public class ApolloPriceTransformProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloPriceTransformProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        JSONObject jsonObject = (JSONObject) exchange.getIn().getBody();
        if (jsonObject != null && ApolloUtil.validateMasterSchema(jsonObject, ApolloUtil.ITEM_PRICE_ROOT_TAG)) {
            List<List<Product>> productList = transformPriceData(jsonObject, exchange); //each price list have different pricelist ref code, hence creating list for each group
            exchange.getIn().setBody(productList);
        } else {
            String fileName =
                    (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
            String errorMessage = "input file : " + fileName + " is not in predefined format";
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }
    }

    public List<List<Product>> transformPriceData(JSONObject productsJson, Exchange exchange) {
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
        int totalRecords = 0, validationCount = 0;
        List<FieldErrorModel> fieldErrorModels = new ArrayList<>();
        List<List<Product>> userGroupPriceList = new ArrayList<>();
        List<Product> bspProductList = new ArrayList<>();
        List<Product> ispProductList = new ArrayList<>();
        List<Product> tspProductList = new ArrayList<>();
        List<Product> dspProductList = new ArrayList<>();
        JSONArray productListJson = productsJson.getJSONArray(ApolloUtil.ITEM_PRICE_ROOT_TAG);
        boolean isValid = true;
        String sku = StringUtils.EMPTY;
        if (productListJson != null && productListJson.length() > 0) {
            totalRecords = productListJson.length();

            for (int counter = 0; counter < totalRecords; counter++) {
                JSONObject productJson = productListJson.getJSONObject(counter);

                isValid = validateManadatoryFields(fieldErrorModels, productJson);

                if (isValid) {
                    sku = (String) productJson.get(ApolloUtil.ITEM_MLPL_CODE);
                    if (Util.isJsonValidDecimalValue(fieldErrorModels, productJson, ApolloUtil.BASE_SALE_PRICE, sku)) {
                        bspProductList.add(createProduct(productJson, ApolloUtil.BASE_SALE_PRICE)); // creating product for apollo price user group
                    } else {
                        validationCount++;
                    }
                    if (Util.isJsonValidDecimalValue(fieldErrorModels, productJson, ApolloUtil.INSTITUTIONAL_SALE_PRICE,
                            sku)) {
                        ispProductList.add(createProduct(productJson, ApolloUtil.INSTITUTIONAL_SALE_PRICE)); // creating product for trade price user group
                    } else {
                        validationCount++;
                    }
                    if (Util.isJsonValidDecimalValue(fieldErrorModels, productJson, ApolloUtil.TRADE_SALE_PRICE, sku)) {
                        tspProductList.add(createProduct(productJson, ApolloUtil.TRADE_SALE_PRICE)); // creating product for retailer price user group
                    } else {
                        validationCount++;
                    }
                    if (Util.isJsonValidDecimalValue(fieldErrorModels, productJson, ApolloUtil.DISTRIBUTOR_SALE_PRICE,
                            sku)) {
                        dspProductList.add(createProduct(productJson, ApolloUtil.DISTRIBUTOR_SALE_PRICE)); // creating product for distributor price user group
                    } else {
                        validationCount++;
                    }
                } else {
                    logger.info("skipping node {} due to validation error", counter);
                    validationCount++;
                }

            }
        } else {
            String errorMessage = "not found any products for price update in input file : " + fileName;
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.NOTFOUND);
        }
        userGroupPriceList.add(bspProductList);
        userGroupPriceList.add(ispProductList);
        userGroupPriceList.add(tspProductList);
        userGroupPriceList.add(dspProductList);
        BatchProcessDetails ftpFileDetails = new BatchProcessDetails();
        ftpFileDetails.setFileName(fileName);
        ftpFileDetails.setTotalCount(totalRecords);
        ftpFileDetails.setValidationCount(validationCount);
        ftpFileDetails.setFieldErrorModelList(fieldErrorModels);
        exchange.getIn().setHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, ftpFileDetails);
        return userGroupPriceList;
    }

    private boolean validateManadatoryFields(List<FieldErrorModel> fieldErrorModels, JSONObject productJson) {
        boolean isValid = true;
        if (Util.isJsonValueEmpty(fieldErrorModels, productJson, ApolloUtil.ITEM_MLPL_CODE, StringUtils.EMPTY)) {
            isValid = false;
        } else {
            String sku = (String) productJson.get(ApolloUtil.ITEM_MLPL_CODE);
            if (!Util.isJsonValidDecimalValue(fieldErrorModels, productJson, ApolloUtil.ITEM_PRICE_MRP, sku)
                || Util.isJsonValueEmpty(fieldErrorModels, productJson, ApolloUtil.ITEM_LOCATION_CODE, sku)) {
                isValid = false;
            }
        }
        return isValid;
    }

    private Product createProduct(JSONObject productJson, String priceGroup) {
        Product product = new Product();

        product.setSku((String) productJson.get(ApolloUtil.ITEM_MLPL_CODE));
        product.setVariantSku(StringUtils.EMPTY);
        product.setMrp(new BigDecimal((String) productJson.get(ApolloUtil.ITEM_PRICE_MRP)));
        product.setPrice(new BigDecimal((String) productJson.get(priceGroup)));
        product.addCustomField(ApolloUtil.QUANTITY, ApolloUtil.ONE);
        product.addCustomField(ApolloUtil.LOCATION_REF_CODE,
                getPriceListRefCode((String) productJson.get(ApolloUtil.ITEM_LOCATION_CODE), priceGroup));

        return product;
    }

    private String getPriceListRefCode(String locationCode, String userGroup) {
        String priceListRefCode = locationCode;
        priceListRefCode += PRICELISTCODE.getFromString(userGroup).getEntityType();
        return priceListRefCode;
    }
}
