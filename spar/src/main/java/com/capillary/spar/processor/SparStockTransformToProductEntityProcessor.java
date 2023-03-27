package com.capillary.spar.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sellerworx.darby.enums.ENTITY_FIELD_TYPE;
import com.sellerworx.darby.processor.TransformToProductEntityProcessor;
import com.sellerworx.darby.transform.ColumnFieldMapping;
import com.sellerworx.darby.transform.FieldMapping;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.validator.RawDataValidationManager;
import com.sellerworx.darby.validator.RawDataValidator;

@Component("SparStockTransformToProductEntityProcessor")
public class SparStockTransformToProductEntityProcessor extends TransformToProductEntityProcessor {

    @Override
    protected List<FieldMapping> getFieldMapping() {
        List<FieldMapping> columnMapping = new ArrayList<>();
        columnMapping.add(new ColumnFieldMapping(0, PRODUCT_SKU_KEY, ENTITY_FIELD_TYPE.MEMBER_FIELD));
        columnMapping.add(new ColumnFieldMapping(0, PRODUCT_VARIANT_SKU_KEY, ENTITY_FIELD_TYPE.MEMBER_FIELD));
        columnMapping.add(
                new ColumnFieldMapping(1, PRODUCT_LOC_REF_CODE_KEY, ENTITY_FIELD_TYPE.CUSTOM_FIELD));
        columnMapping.add(new ColumnFieldMapping(2, PRODUCT_STOCK_KEY, ENTITY_FIELD_TYPE.CUSTOM_FIELD));
        return columnMapping;
    }

    @Override
    protected Map<Integer, List<RawDataValidator>> getValidationMapPerTuple() {
        Map<Integer, List<RawDataValidator>> validatorMap = new HashMap<>();
        List<RawDataValidator> validatorsList = Arrays.asList(
                RawDataValidationManager.getEmptyCheckValidator());
        validatorMap.put(0, validatorsList);
        validatorMap.put(1, validatorsList);
        validatorMap.put(2, validatorsList);
        return validatorMap;
    }

    @Override
    protected String getLineDelimeter() {
        return SymbolUtil.SEMICOLON;
    }

}