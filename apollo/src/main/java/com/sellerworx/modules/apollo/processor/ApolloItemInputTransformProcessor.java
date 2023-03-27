package com.sellerworx.modules.apollo.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.apollo.models.ApolloItemMaster;
import com.sellerworx.modules.apollo.util.ApolloUtil;

@Component("ApolloItemInputTransformProcessor")
@Documented(description = "Transforms the input json object from input file to apollo specific  entity",
        inBody = @KeyInfo(comment = "Input JsonObject", type = JSONObject.class),
        outBody = @KeyInfo(comment = "transformed Apollo ItemMaster model", type = ApolloItemMaster.class))
public class ApolloItemInputTransformProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloItemInputTransformProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILENAME, exchange);
        JSONObject jsonObject = (JSONObject) exchange.getIn().getBody();
        int totalRecords = 0, validationCount = 0;
        List<FieldErrorModel> fieldErrorModels = new ArrayList<>();
        if (jsonObject != null && ApolloUtil.validateMasterSchema(jsonObject, ApolloUtil.ITEM_MASTER_ROOT_TAG)) {
            JSONArray jsonObj = jsonObject.getJSONArray(ApolloUtil.ITEM_MASTER_ROOT_TAG);
            logger.debug("converting item master input json to object list for input {} ", jsonObj.toString());
            if (jsonObj != null && jsonObj.length() > 0) {
                List<ApolloItemMaster> inputDataList = new ArrayList();
                totalRecords = jsonObj.length();
                for (int counter = 0; counter < jsonObj.length(); counter++) {
                    JSONObject itemMasterJson = jsonObj.getJSONObject(counter);
                    if (!Util.isJsonValueEmpty(fieldErrorModels, itemMasterJson, ApolloUtil.ITEM_MLPL_CODE,
                            StringUtils.EMPTY)) {
                        ObjectMapper mapper = new ObjectMapper();
                        ApolloItemMaster itemMasterObj =
                                mapper.readValue(itemMasterJson.toString(), ApolloItemMaster.class);
                        inputDataList.add(itemMasterObj);
                    } else {
                        validationCount++;
                        logger.error("MPCL code is empty for json {} in input file {} : ", itemMasterJson, fileName);
                    }
                }
                logger.debug("after conversion the list object is {}", inputDataList);
                BatchProcessDetails ftpFileDetails = new BatchProcessDetails();
                ftpFileDetails.setFileName(fileName);
                ftpFileDetails.setTotalCount(totalRecords);
                ftpFileDetails.setValidationCount(validationCount);
                ftpFileDetails.setFieldErrorModelList(fieldErrorModels);
                exchange.getIn().setHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, ftpFileDetails);
                exchange.getIn().setBody(inputDataList);
            } else {
                String errorMessage =
                        "not found any product attributes for item master update in input file : " + fileName;
                logger.error(errorMessage);
                throw new DarbyException(errorMessage, ErrorCode.EMPTY);
            }
        } else {
            String errorMessage = "input file " + jsonObject.toString() + " is not as per the format";
            logger.error(errorMessage);
            throw new DarbyException(errorMessage, ErrorCode.EMPTY);
        }

    }

}
