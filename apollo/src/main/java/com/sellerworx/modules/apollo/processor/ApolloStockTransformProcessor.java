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

@Component("ApolloStockTransformProcessor")
@Documented(description = "Transforms the stock entity from input file to mj specific entity",
        inBody = @KeyInfo(type = JSONObject.class, comment = "Input JsonObject"),
        inHeaders = { @KeyInfo(comment = "Input File Name", name = ExchangeHeaderKeys.FILENAME) },
        outBody = @KeyInfo(type = List.class,
                comment = "transformed mj specific list of products for locationwise stock update"))
public class ApolloStockTransformProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloStockTransformProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        JSONObject jsonObject = (JSONObject) exchange.getIn().getBody();
        if (jsonObject != null && ApolloUtil.validateMasterSchema(jsonObject, ApolloUtil.ITEM_STOCK_ROOT_TAG)) {
            List<Product> productList = transformStockData(jsonObject, exchange);
            exchange.getIn().setBody(productList);
        } else {
            String fileName =
                    (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
            String errorMessage = "input file : " + fileName + " is not in predefined format";
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.INVALID);
        }
    }

    private List<Product> transformStockData(JSONObject productsJson, Exchange exchange) {
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
        int totalRecords = 0, validationCount = 0;
        List<FieldErrorModel> fieldErrorModels = new ArrayList<>();
        List<Product> productList = new ArrayList<>();
        JSONArray productListJson = productsJson.getJSONArray(ApolloUtil.ITEM_STOCK_ROOT_TAG);
        if (productListJson != null && productListJson.length() > 0) {
            totalRecords = productListJson.length();

            for (int counter = 0; counter < totalRecords; counter++) {
                JSONObject productJson = productListJson.getJSONObject(counter);
                boolean isValid = validateManadatoryNodes(fieldErrorModels, productJson);
                if (isValid) {
                    productList.add(createProduct(productJson));
                } else {
                    logger.info("skipping node {} due to validation error", counter);
                    validationCount++;
                }
            }

        } else {
            String errorMessage = "not found any products for stock update in input file : " + fileName;
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.NOTFOUND);
        }

        BatchProcessDetails ftpFileDetails = new BatchProcessDetails();
        ftpFileDetails.setFileName(fileName);
        ftpFileDetails.setTotalCount(totalRecords);
        ftpFileDetails.setValidationCount(validationCount);
        ftpFileDetails.setFieldErrorModelList(fieldErrorModels);
        exchange.getIn().setHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, ftpFileDetails);
        return productList;

    }

    private Product createProduct(JSONObject productJson) {
        BigDecimal stockValue = new BigDecimal((String) productJson.get(ApolloUtil.CLOSING_STOCK)).setScale(0,
                BigDecimal.ROUND_HALF_UP);

        if (stockValue.compareTo(BigDecimal.ZERO) < 0) {
            stockValue = BigDecimal.ZERO;
        }
        Product product = new Product();
        product.setSku((String) productJson.get(ApolloUtil.ITEM_MLPL_CODE));
        product.setVariantSku(StringUtils.EMPTY);
        product.addCustomField(ApolloUtil.LOCATION_REF_CODE, (String) productJson.get(ApolloUtil.ITEM_LOCATION_CODE));
        product.addCustomField(ApolloUtil.STOCK, stockValue.toString());

        return product;
    }

    private boolean validateManadatoryNodes(List<FieldErrorModel> errorModelList, JSONObject productJson) {
        boolean isValid = true;
        if (Util.isJsonValueEmpty(errorModelList, productJson, ApolloUtil.ITEM_MLPL_CODE, StringUtils.EMPTY)) {
            isValid = false;
        } else {
            String sku = (String) productJson.get(ApolloUtil.ITEM_MLPL_CODE);
            if (Util.isJsonValueEmpty(errorModelList, productJson, ApolloUtil.ITEM_LOCATION_CODE, sku)) {
                isValid = false;
            } else if (!Util.isJsonValidDecimalValue(errorModelList, productJson, ApolloUtil.CLOSING_STOCK, sku)) {
                isValid = false;
            }
        }
        return isValid;
    }
}
