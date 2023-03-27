
package com.sellerworx.modules.phuae.transformation.implementation;

import com.ncr.util.NCRUtil;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.enums.ITEM_TYPE;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.exception.NotFoundException;
import com.sellerworx.darby.model.Tenant;
import com.sellerworx.darby.util.*;
import com.sellerworx.modules.mapping.MappingService;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.util.MJUtil;
import com.sellerworx.modules.ncr.service.SystemService;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import com.sellerworx.modules.ncr.util.NCRItemAliasKeys;
import com.sellerworx.modules.ncr.util.NcrOrderCustomKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.datacontract.schemas._2004._07.psorderingdom_enums.ENTRY_MOD_CODES;
import org.datacontract.schemas._2004._07.sdm_sdk.CC_STORE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is responsible for handling all 3 mapping of MJ SKU/VSKU to NCR ITEM
 * Mapping for
 * 1. NCR ITEM CODE.
 * 2. NCR ITEM MODIFIER CODE
 * 3. NCR DEAL ITEM CODE
 */
@Slf4j
@Component("NCRHandleItemMappingTransformer")
public class NCRHandleItemMappingTransformer implements OrderTransformer {

    @Autowired
    private MappingService mappingService;

    @Autowired
    private SystemService systemService;

    private static final String VARIANT_STRENGTH_REMOVE = "NO-";
    private static final String VARIANT_STRENGTH_RGLR = "Rglr";
    private static final String NCR_MOD_CODE_ALIAS = "ModCode";

    @Override
    public void transform(Exchange exchange, Order order) {
        Tenant tenant = (Tenant) exchange.getIn().getHeader(ExchangeHeaderKeys.TENANT);
        List<OrderLine> orderLines = order.getOrderLines();
        log.info("orderline size from mj: {} and orderlines: {}", orderLines.size(), orderLines);

        List dealCategoryIds = Arrays.asList(RequestContext.configStringEx(NCRConfigKeys.ECOM_DEAL_CATEGORY_IDS).split(
                SymbolUtil.COMMA));
        List<OrderLine> finalOrderLines = new ArrayList<>();
        for (OrderLine orderLine : orderLines) {

            if (isCrustOrderLine(orderLine)) {
                String size = orderLine.getVariantSku().substring(orderLine.getVariantSku().length() - 4,
                                                                  orderLine.getVariantSku().length());

                /**
                 * Handle No toppings
                 */
                List<OrderLine> childOrderLines = MJUtil.getOLsOfParentOLID(orderLine.getOrderLineId(), orderLines);

                for (OrderLine childOl : childOrderLines) {
                    if (childOl.getSku().startsWith(VARIANT_STRENGTH_REMOVE)) {
                        String variantSKU = childOl.getSku().substring(
                                3) + SymbolUtil.HYPHEN + VARIANT_STRENGTH_RGLR + size;
                        childOl.setVariantSku(variantSKU);
                        childOl.addCustomField(NCR_MOD_CODE_ALIAS, ENTRY_MOD_CODES.NO);
                    }
                }

            }

            /**
             * Below code is for mj SKU to ncr Item Id.
             */
            String ncrItemCode = getMappedItemAliasValue(orderLine, NCRItemAliasKeys.NCR_ITEM_ALIAS_NAME,
                                                         order);

            orderLine.addCustomField(NCRUtil.ITEM_ID, ncrItemCode);
            if (dealCategoryIds.contains(orderLine.getCategoryId())) {
                /**
                 * this block is responsible for deal mapping.
                 */
                orderLine.addCustomField(NCRUtil.NCR_DEAL_ID, getMappedItemAliasValue(
                        orderLine, NCRItemAliasKeys.NCR_DEAL_ITEM_ALIAS_NAME, order));
                orderLine.addCustomField(OrderLineCustomKeys.POS_DEAL_ID, getMappedItemAliasValue(
                        orderLine, NCRItemAliasKeys.NCR_DEAL_ITEM_ALIAS_NAME, order));
            }

            String modifierValue = getMappedItemModifierAliasValue(orderLine, order);
            if (StringUtils.isNotBlank(modifierValue)) {
                /**
                 * this block is responsible for tranform item with modifier.
                 * If chicken-wings-12pcs-and-peri-peri-sauce is placed in storefront as single item,
                 * we get one MJ order line but we have have to send two ncr ids(one for chicken-wings pcs and one for sauce).
                 * Hence, grand parent(parentOrderLine) will be chicken-wings pcs
                 * and parent(childOrderLine) will be sauce.
                 */
                log.info("modifier value is: {} for orderline: {}", modifierValue, orderLine);

                OrderLine childOrderLine = new OrderLine();

                childOrderLine = (OrderLine) Util.copy(orderLine);

                childOrderLine.setSku(orderLine.getVariantSku() + SymbolUtil.HYPHEN + modifierValue);
                childOrderLine.setVariantSku(StringUtils.EMPTY);
                childOrderLine.setParentOrderLineId(orderLine.getOrderLineId());
                childOrderLine.addCustomField(NCRUtil.ITEM_ID, modifierValue);

                childOrderLine.setOrderLineId(StringUtils.EMPTY);

                finalOrderLines.add(childOrderLine);
            }
            finalOrderLines.add(orderLine);
        }
        orderLines.clear();
        orderLines.addAll(finalOrderLines);
        log.info("orderline size after attribute changes: {} and orderlines: {}", orderLines.size(), orderLines);
    }

    /**
     * Gets mapped item alias value for NCR_ITEM_MODIFIER_CODE.
     *
     * @param orderLine the order line
     * @return
     */
    private String getMappedItemModifierAliasValue(OrderLine orderLine, Order order) {
        String orderedSku = orderLine.getVariantSku();
        ITEM_TYPE itemType = ITEM_TYPE.MJ_PRODUCT_VARIANT;

        if (StringUtils.isEmpty(orderedSku)) {
            orderedSku = orderLine.getSku();
            itemType = ITEM_TYPE.MJ_PRODUCT;
        }

        try {
            return getMappedAliasValueBasedOnStoreMenuTemplate(orderedSku,
                                                               NCRItemAliasKeys.NCR_ITEM_MODIFIER_CODE,
                                                               itemType, order);
        } catch (NotFoundException nfe) {
            log.debug("no modifier found from master id: {}", orderedSku);
        }
        return StringUtils.EMPTY;
    }


    /**
     * Gets mapped item.
     *
     * @param orderLine the order line
     * @param aliasName item-alias key name
     * @param order
     * @return
     */
    private String getMappedItemAliasValue(OrderLine orderLine, String aliasName,
                                           Order order) {
        String orderedSku = orderLine.getVariantSku();
        ITEM_TYPE itemType = ITEM_TYPE.MJ_PRODUCT_VARIANT;

        if (StringUtils.isEmpty(orderedSku)) {
            orderedSku = orderLine.getSku();
            itemType = ITEM_TYPE.MJ_PRODUCT;
        }

        return getMappedAliasValueBasedOnStoreMenuTemplate(orderedSku, aliasName, itemType, order);
    }

    private String getMappedAliasValueBasedOnStoreMenuTemplate(String masterId, String aliasName,
                                                               ITEM_TYPE itemType,
                                                               Order order) {

        Boolean isNcrMenuTemplateStoreBase = Boolean.valueOf(
                RequestContext.configString(NCRConfigKeys.IS_NCR_MENU_TEMPLATE_ID_STORE_BASED, "false"));

        if (isNcrMenuTemplateStoreBase) {
            String menuTemplateId = getMenuTemplateIdStoreBased(order.getSourceLocationCode());
            order.addCustomField(NcrOrderCustomKeys.MENU_TEMPLATE_ID, menuTemplateId);
            aliasName = aliasName + SymbolUtil.UNDERSCORE + menuTemplateId;
        }
        log.info("getting alias: {} for masterid: {} with type: {}", aliasName, masterId, itemType);

        return mappingService.map(RequestContext.getTenantInfo().getId(), masterId, aliasName, itemType);
    }


    private boolean isCrustOrderLine(OrderLine orderLine) {
        return orderLine.getSku().startsWith(RequestContext.configStringEx(TenantConfigKeys.MJ_CRUST_SKU_PREFIX));
    }

    private String getMenuTemplateIdStoreBased(String sourceLocationCode) {

        String ncrMenuTemplateId = StringUtils.EMPTY;
        String ncrStoreId = StringUtils.EMPTY;

        try {
            ncrStoreId = mappingService.map(RequestContext.getTenantInfo().getId(), sourceLocationCode,
                                            NCRItemAliasKeys.NCR_STORE_ID, ITEM_TYPE.MJ_LOCATION_CODE);
        } catch (NotFoundException e) {
            log.error("ncr store mapping alias is missing for masterid: {}, error: {}", sourceLocationCode,
                      e.getMessage(), e);
            throw new DarbyException(
                    "ncr store mapping alias is missing for masterid " + sourceLocationCode + ", error: " + e
                            .getMessage(), e, ErrorCode.NOTFOUND);
        }

        CC_STORE response = systemService.getStoreDetail(ncrStoreId);
        log.info("get store response : {}", response);
        return response.getSTR_MENU_ID().toString();
    }
}
