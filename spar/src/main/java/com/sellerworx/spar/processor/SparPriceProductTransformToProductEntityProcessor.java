package com.sellerworx.spar.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.enums.ENTITY_FIELD_TYPE;
import com.sellerworx.darby.processor.TransformToProductEntityProcessor;
import com.sellerworx.darby.transform.ColumnFieldMapping;
import com.sellerworx.darby.transform.FieldMapping;
import com.sellerworx.darby.transform.FixedValueMapping;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.validator.RawDataValidationManager;
import com.sellerworx.darby.validator.RawDataValidator;

@Component("SparPriceProductTransformToProductEntityProcessor")
@Documented(description = "this class provides info regarding mapping the file column and validation rule " +
                          "for them ")
public class SparPriceProductTransformToProductEntityProcessor extends TransformToProductEntityProcessor {

    private static final String QUANTITY = "1";

    @Override
    protected List<FieldMapping> getFieldMapping() {
        List<FieldMapping> columnMapping = new ArrayList<>();
        columnMapping.add(new ColumnFieldMapping(0, PRODUCT_SKU_KEY, ENTITY_FIELD_TYPE.MEMBER_FIELD));
        columnMapping.add(new ColumnFieldMapping(5, PRODUCT_WEB_PRICE_KEY, ENTITY_FIELD_TYPE.MEMBER_FIELD));
        columnMapping.add(new ColumnFieldMapping(7, PRODUCT_MRP_KEY, ENTITY_FIELD_TYPE.MEMBER_FIELD));
        columnMapping.add(
                new ColumnFieldMapping(4, PRODUCT_LOC_REF_CODE_KEY, ENTITY_FIELD_TYPE.CUSTOM_FIELD));
        columnMapping.add(new FixedValueMapping(QUANTITY, PRODUCT_QUANTITY, ENTITY_FIELD_TYPE.CUSTOM_FIELD));
        return columnMapping;
    }

    @Override
    protected Map<Integer, List<RawDataValidator>> getValidationMapPerTuple() {
        Map<Integer, List<RawDataValidator>> validatorMap = new HashMap<>();
        List<RawDataValidator> validatorsList = Arrays.asList(
                RawDataValidationManager.getEmptyCheckValidator());
        validatorMap.put(0, validatorsList);
        validatorMap.put(4, validatorsList);
        validatorMap.put(5, validatorsList);
        validatorMap.put(7, validatorsList);
        return validatorMap;
    }

    @Override
    protected String getLineDelimeter() {
        return SymbolUtil.SEMICOLON;
    }

}
