package com.sellerworx.modules.phuae.transformation.implementation;

import com.microsoft.azure.servicebus.primitives.StringUtil;
import com.ncr.util.NCRUtil;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.frontend.request.GetAllProductsAPIParams;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.TenantConfigKeys;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.modules.martjack.entity.frontapi.BundleProductGroups;
import com.sellerworx.modules.martjack.entity.frontapi.BundleProductItems;
import com.sellerworx.modules.martjack.frontend.response.Product;
import com.sellerworx.modules.martjack.frontend.services.MJFrontEndCatalogService;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.martjack.util.MJTenantConfigKeys;
import com.sellerworx.modules.martjack.util.MJUtil;
import com.sellerworx.modules.ncr.service.ItemService;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import com.sellerworx.modules.ncr.util.NCROrderUtil;
import com.sellerworx.modules.phuae.util.NCROrderCustumKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.datacontract.schemas._2004._07.psorderingdom_enums.ENTRY_MOD_CODES;
import org.datacontract.schemas._2004._07.psorderingdom_enums.ENTRY_TYPE;
import org.datacontract.schemas._2004._07.sdm_sdk.ArrayOfCEntry;
import org.datacontract.schemas._2004._07.sdm_sdk.CEntry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

/**
 * This class is responsible for creating cEntries by fetching structure for Ecom frontend API.
 * cEntries array is added in order custom field {keyName- items}
 */
@Slf4j
@Component("NCROrderItemDetailsTransform")
public class NCROrderItemDetailsTransform implements OrderTransformer {

    @Autowired
    private ItemService ncrItemSvc;

    @Autowired
    private MJFrontEndCatalogService mjFrontEndCatalogService;

    private static final String NCR_ITEM_LEVEL_DOB_FALSE = "false";
    private static final String LANGUAGE_CODE_EN = "en";
    private static final boolean INCLUDE_UNAVAILABLE_PRODUCT_FROM_MJ_FRONT_API = true;
    private static final double DISCOUNT_PRICE = 0;
    private static final String NCR_MOD_CODE_ALIAS = "ModCode";
    private static final String IS_PRICE_INCLUDED_IN_ECOM_DEAL = "false";
    private static final int NCR_ITEM_LEVEL_CONCEPT_ID = -1;
    private static final int NCR_ITEM_LEVEL_CUST_ID = -1;
    private static final String NCR_ITEM_ORDR_MODE_ALIAS = "OM_SAVED";


    @Override
    public void transform(Exchange exchange, Order order) {
        String modifierBundleItemIds= StringUtil.EMPTY;

        List<OrderLine> mjOrderLines = order.getOrderLines();
        JSONArray finalItemsArray = new JSONArray();
        Map<String, String> ncrConfigMap = NCROrderUtil.getNcrConfigMap(RequestContext.getConfigs(), order);

        List dealCategoryIds = Arrays.asList(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.ECOM_DEAL_CATEGORY_IDS, ncrConfigMap).split(
                        SymbolUtil.COMMA));

        Calendar orderCreatedDate = null;
        try {
            orderCreatedDate = TransformUtil
                    .convertDateToCalendarInstance(order.getShipdate(), TenantConfigKeys
                            .getValueFromTenantConfig(MJTenantConfigKeys.MJ_TIMEZONE, ncrConfigMap));
        } catch (ParseException e) {
            log.error("unable to parse date: {}, error: {}", order.getShipdate(), e.getMessage(), e);
        }

        /*get all root order lines - Grand Parents OLs*/
        List<OrderLine> grandParentOls = MJUtil.getAllParentOrderLines(order);
        for (OrderLine grandParentOL : grandParentOls) {
            JSONArray finalGrandParentItemsArray = new JSONArray();
            int bookmark = (int) Math.floor(Math.random() * 1000); //custom logic for PH UAE DEALS.
            String dealItemIdentifier = StringUtils.EMPTY;
            List<Product> frontEndDealProductResponse = new ArrayList<>();
            int dealId = 0;

            if (dealCategoryIds.contains(grandParentOL.getCategoryId())) {
                dealItemIdentifier = "00";
                dealId = Integer.parseInt((String) grandParentOL.getCustomField(NCRUtil.NCR_DEAL_ID));
                grandParentOL.addCustomField(NCROrderCustumKeys.IS_DEAL_ORDERR_LINE, true);

                String attributeName = TenantConfigKeys.getValueFromTenantConfig(
                        TenantConfigKeys.MJ_PRODUCT_ATTRIBUTE_NAME_MODIFIER, RequestContext.getConfigs(),
                        true);
                try {
                    modifierBundleItemIds = mjFrontEndCatalogService.getProductAttributeValue(
                            grandParentOL.getSku(), attributeName);
                    log.info("response of getproductattributevalue {} for sku {}", modifierBundleItemIds,
                             grandParentOL.getSku());
                } catch (DarbyException ne) {
                    log.error(
                            "not found any modifiers for deal with sku:{} from getproductattributevalue api." +
                            "hence, considering this deal has no modifiers. error message is {}",
                            grandParentOL.getSku(), ne.getMessage(), ne);
                }

                GetAllProductsAPIParams apiParams = new GetAllProductsAPIParams();
                apiParams.setIncludeUnavailable(INCLUDE_UNAVAILABLE_PRODUCT_FROM_MJ_FRONT_API);
                apiParams.setLanguageCode(GetAllProductsAPIParams.LANGUAGE_CODE.EN);

                String[] skus = new String[1];
                skus[0] = grandParentOL.getSku();
                apiParams.setSkus(skus);
                frontEndDealProductResponse = mjFrontEndCatalogService.getAllProducts(apiParams);
                log.info("response of getAllProducts api param: {}, response: {}", apiParams,
                         frontEndDealProductResponse);
            }
            /*grand parent objects.*/
            JSONObject grandParentObject = createBaseCEntryJson(grandParentOL, "99", bookmark, dealId);
            log.debug("grand parent before adding parent array: {}", grandParentObject);

            JSONArray parentItemArray = new JSONArray();
            /*get all children by grand Parents orderline id - Parents OLs*/
            List<OrderLine> parentOLs = MJUtil.getOLsOfParentOLID(grandParentOL.getOrderLineId(), mjOrderLines);
            for (OrderLine parentOL : parentOLs) {
                JSONObject parentObject = createBaseCEntryJson(parentOL, dealItemIdentifier, bookmark, dealId);
                JSONArray childrenItemArray = new JSONArray();
                List<OrderLine> childrenOls = MJUtil.getOLsOfParentOLID(parentOL.getOrderLineId(), mjOrderLines);

                if (CollectionUtils.isNotEmpty(childrenOls)) {
                    for (OrderLine childOL : childrenOls) {
                        JSONObject childObject = createBaseCEntryJson(childOL, StringUtils.EMPTY, bookmark, dealId);
                        handleQuantity(childOL.getQuantity(), childObject, childrenItemArray);
                    }
                    parentObject.put(NCRUtil.ORDER_CART_ITEMS, childrenItemArray);
                }
                //adding parent object in parentItemArray or finalDealItemArray or ignore(if default).
                addItemToFinalList(parentObject, parentOL, frontEndDealProductResponse, parentItemArray,
                                   finalGrandParentItemsArray,modifierBundleItemIds);

                if (StringUtils.isNotEmpty(dealItemIdentifier)) {
                    dealItemIdentifier = String.valueOf(Integer.parseInt(dealItemIdentifier) + 1);
                }
            }

            grandParentObject.put(NCRUtil.ORDER_CART_ITEMS, parentItemArray);
            finalGrandParentItemsArray.put(grandParentObject);
            log.info("final grand parent object: {}", finalGrandParentItemsArray);

            handleQuantity(grandParentOL.getQuantity(), finalGrandParentItemsArray, finalItemsArray);
        }

        log.info("final items array size: {} and array: {}", finalItemsArray.length(),
                 finalItemsArray.toString());
        order.addCustomField(NCRUtil.ORDER_CART_ITEMS,
                             getEntries(order, finalItemsArray, 0, ncrConfigMap, orderCreatedDate));
    }

    private void addItemToFinalList(JSONObject parentObject, OrderLine parentOL,
                                    List<Product> frontEndDealProductResponse,
                                    JSONArray parentItemArray,
                                    JSONArray finalGrParentItemsArray,String modifierBundleItemIds) {

        if (parentOL.getSku().contains(MJUtil.MJ_CRUST_PREFIX)) {
            handleQuantity(parentOL.getQuantity(), parentObject, finalGrParentItemsArray);
        }
        else {
            /*we calling frontend API only in case of Deal. so value is empty in other scenarios.*/
            if (CollectionUtils.isNotEmpty(frontEndDealProductResponse)) {
                for (Product product : frontEndDealProductResponse) {
                    List<BundleProductGroups> bundleGroups = product.getBundleProductGroups();
                    for (BundleProductGroups bundleProductGroup : bundleGroups) {
                        List<BundleProductItems> bundleGroupItems = bundleProductGroup.getBundleProductItems();
                        for (BundleProductItems bundleProductItem : bundleGroupItems)
                            if (parentOL.getSku().equalsIgnoreCase(bundleProductItem.getBundleitemsku())) {
                                if (bundleGroupItems.size() == 1) {
                                    //default item as only 1 item is present in the bundle group items.
                                    log.info("orderline: {} is default item for the deal", parentOL);
                                    return;
                                }

                                List modifiersList = Arrays.asList(modifierBundleItemIds.split(SymbolUtil.COMMA));
                                if(modifiersList.contains(bundleProductItem.getBundleItemId())) {
                                    // item is modifier
                                    parentObject.put(NCR_MOD_CODE_ALIAS, ENTRY_MOD_CODES.WITH);
                                    handleQuantity(parentOL.getQuantity(), parentObject,
                                                   parentItemArray);
                                    return;
                                }
                                else {
                                    // item is not a modifier
                                    handleQuantity(parentOL.getQuantity(), parentObject,
                                                   finalGrParentItemsArray);
                                    return;
                                }
                            }
                    }
                }
            }
            else {
                //parent ol is not a deal.
                if (ENTRY_MOD_CODES.NO != parentOL.getCustomField(NCR_MOD_CODE_ALIAS)) {
                    parentObject.put(NCR_MOD_CODE_ALIAS, ENTRY_MOD_CODES.WITH);
                } else {
                    parentObject.put(NCR_MOD_CODE_ALIAS, ENTRY_MOD_CODES.NO);
                }
                handleQuantity(parentOL.getQuantity(), parentObject, parentItemArray);
            }
        }
    }

    /**
     * If any items inside the bundle group items add extra price to deal then this method return true.
     *
     * @param bundleGroupItems
     * @return
     */
    private boolean isBundleItemsContainsExtraPrice(List<BundleProductItems> bundleGroupItems) {
        for (BundleProductItems bundleProductItem : bundleGroupItems) {
            if (IS_PRICE_INCLUDED_IN_ECOM_DEAL.equalsIgnoreCase(bundleProductItem.getIsIncludeBundlePrice())) {
                return true;
            }
        }
        return false;
    }

    /**
     * add the ncrItemsArray to finalNCRItemsArray depending up quantity.
     *
     * @param quantity
     * @param ncrItemsArray      - complete one set of deal or pizza or single item.
     * @param finalNcrItemsArray
     */
    private void handleQuantity(Double quantity, JSONArray ncrItemsArray, JSONArray finalNcrItemsArray) {
        while (quantity > 0) {
            for (int i = 0; i < ncrItemsArray.length(); i++) {
                JSONObject object = (JSONObject) ncrItemsArray.get(i);
                finalNcrItemsArray.put(object);
            }
            quantity--;
        }
    }

    /**
     * add the ncrItemObject to finalNCRItemsArray depending up quantity.
     *
     * @param quantity
     * @param ncrItemObject      - complete one set of deal or pizza or single item.
     * @param finalNcrItemsArray
     */
    private void handleQuantity(Double quantity, JSONObject ncrItemObject, JSONArray finalNcrItemsArray) {
        while (quantity > 0) {
            finalNcrItemsArray.put(ncrItemObject);
            quantity--;
        }
    }

    /**
     * Recursive called method to create final set of cEntries and add it to the order custom field.
     *
     * @param order
     * @param finalItemsArray
     * @param level
     * @param ncrConfigMap
     * @param orderCreatedDate
     * @return
     */
    private ArrayOfCEntry getEntries(Order order, JSONArray finalItemsArray, int level,
                                     Map<String, String> ncrConfigMap, Calendar orderCreatedDate) {
        log.info("getentries itemsjsonarray: {}", finalItemsArray.toString(1));
        ArrayOfCEntry itemEntriesArray = new ArrayOfCEntry();
        CEntry[] entries = new CEntry[finalItemsArray.length()];
        itemEntriesArray.setCEntry(entries);

        String askDesc = ncrConfigMap.get(NCRUtil.ORDER_ASK_DESC);
        String askPrice = ncrConfigMap.get(NCRUtil.ORDER_ASK_PRICE);
        Integer checkId = Integer.parseInt(ncrConfigMap.get(NCRUtil.ORDER_CHECK_ID));
        Integer category = Integer.parseInt(ncrConfigMap.get(NCRUtil.ORDER_CATEGORY));
        String ncrOrderCreatedBy = ncrConfigMap.get(NCRConfigKeys.NCR_ORDER_CREATED_BY);
        int ncrItemLevelCustId = Integer.parseInt(ncrConfigMap.get(NCRConfigKeys.NCR_ITEM_LEVEL_CUST_ID));
        Integer voidReason = Integer.parseInt(ncrConfigMap.get(NCRUtil.ORDER_VOID_REASON));

        // Fill up the CEntry[] array
        for (int i = 0; i < finalItemsArray.length(); i++) {
            CEntry entry = new CEntry();

            JSONObject item = finalItemsArray.getJSONObject(i);

            entry.setVoidReason(voidReason);
            JSONObject itemJson = ncrItemSvc.getNCRItemDetailsJson(ncrConfigMap, item.getInt(NCRUtil.ITEM_ID));
            entry.setAdj1(itemJson.getBigDecimal("adj1"));
            entry.setAdj2(itemJson.getBigDecimal("adj2"));
            entry.setAdj3(itemJson.getBigDecimal("adj3"));
            entry.setAdj4(itemJson.getBigDecimal("adj4"));
            entry.setAdjGroup1(itemJson.getBigDecimal("adjGroup1"));
            entry.setAdjGroup2(itemJson.getBigDecimal("adjGroup2"));
            entry.setAdjGroup3(itemJson.getBigDecimal("adjGroup3"));
            entry.setAdjGroup4(itemJson.getBigDecimal("adjGroup4"));
            entry.setNoun(itemJson.getBigDecimal("noun"));
            entry.setAskDesc(askDesc);
            entry.setAskPrice(askPrice);
            entry.setCheckID(checkId);
            entry.setCategory(category);
            entry.setCreateBy(ncrOrderCreatedBy);
            entry.setTransBy(ncrOrderCreatedBy);
            entry.setUpdateBy(ncrOrderCreatedBy);
            entry.setCreateTime(orderCreatedDate);
            entry.setDateOfTrans(orderCreatedDate);
            entry.setDueTime(orderCreatedDate);
            entry.setCustID(ncrItemLevelCustId);
            entry.setDOB(NCR_ITEM_LEVEL_DOB_FALSE);
            entry.setItemID(Integer.parseInt((String) item.get(NCRUtil.ITEM_ID)));
            entry.setLevel(level);

            if (item.has(NCRUtil.NCR_DEAL_ID)) {
                entry.setDealID(new BigDecimal((String) item.get(NCRUtil.NCR_DEAL_ID)));
            }
            if (item.has(NCRUtil.ITEM_NAME)) {
                String itemName = (String) item.get(NCRUtil.ITEM_NAME);
                entry.setName(itemName);
                if (order.getLanguageCode().equalsIgnoreCase(LANGUAGE_CODE_EN)) {
                    entry.setLongName(itemName);
                    entry.setShortName(itemName);
                }
                else {
                    entry.setLongnameUn(itemName);
                    entry.setLongnameUn(itemName);
                }
            }

            if (item.has(NCR_MOD_CODE_ALIAS)) {
                entry.setModCode((ENTRY_MOD_CODES) item.get(NCR_MOD_CODE_ALIAS));
            }

            if (item.has(NCRUtil.CONCEPT_ID)) {
                entry.setConceptID(item.getInt(NCRUtil.CONCEPT_ID));
            }
            if (item.has(NCRUtil.ITEM_CUST_ID)) {
                entry.setCustID(item.getInt(NCRUtil.ITEM_CUST_ID));
            }

            if (item.has(NCRUtil.ITEM_PRICE_INCLUSIVE_TAX)) {
                entry.setPrice(item.getDouble(NCRUtil.ITEM_PRICE_INCLUSIVE_TAX));
            }

            if (item.has(NCRUtil.ITEM_ORDR_MODE)) {
                entry.setOrdrMode((String) item.get(NCRUtil.ITEM_ORDR_MODE));
            }

            if (item.has(NCRUtil.ITEM_PRICE)) {
                entry.setPrice(((BigDecimal) item.get(NCRUtil.ITEM_PRICE)).doubleValue());
            }

            if (item.has(NCRUtil.ORDER_CART_ITEMS)) {
                entry.setEntries(
                        getEntries(order, item.getJSONArray(NCRUtil.ORDER_CART_ITEMS), 1, ncrConfigMap,
                                   orderCreatedDate));
            }
            entry.setQCInstanceID(new BigDecimal(0));
            entry.setQCComponent(new BigDecimal(0));
            entry.setQCProID(0);
            entry.setStatus(ENTRY_MOD_CODES.NOTAPPLIED);
            entry.setStoreEntryID(-1);
            entry.setSubType(new BigDecimal(-1));
            entry.setType(ENTRY_TYPE.ITEM);

            //TODO future use for item level discount currently not supported.
            entry.setDiscountPrice(DISCOUNT_PRICE);
            if (!item.get("modgroupId").toString().isEmpty()) {
                entry.setModgroupID(Integer.parseInt(item.get("modgroupId").toString()));
            }
            log.info("item id: {} and item mod group id: {}", entry.getItemID(), entry.getModgroupID());
            entries[i] = entry;
        }
        return itemEntriesArray;
    }


    private JSONObject createBaseCEntryJson(OrderLine orderLine, String itemIdentifier, int bookmark, int dealId) {

        JSONObject cEntryJson = new JSONObject();
        cEntryJson.put(NCRUtil.ITEM_ID, orderLine.getCustomField(NCRUtil.ITEM_ID));
        cEntryJson.put("modgroupId", "-1");

        if (StringUtils.isNotEmpty(itemIdentifier) && 0 != dealId) {
            cEntryJson.put(NCRUtil.NCR_DEAL_ID, getDealId(orderLine, itemIdentifier, bookmark, dealId));
        }
        cEntryJson.put(NCRUtil.ITEM_NAME, orderLine.getProductTitle());
        cEntryJson.put(NCRUtil.ITEM_PRICE, orderLine.getProductPrice());
        cEntryJson.put(NCRUtil.ITEM_ORDR_MODE, orderLine.getCustomField(NCRUtil.ITEM_ORDR_MODE));
        //added ModCode as none of all, will update once item is passing as modifier.
        cEntryJson.put(NCRUtil.CONCEPT_ID, NCR_ITEM_LEVEL_CONCEPT_ID);
        cEntryJson.put(NCRUtil.ITEM_CUST_ID, NCR_ITEM_LEVEL_CUST_ID);

        if (null != orderLine.getCustomField(NCR_MOD_CODE_ALIAS)) {
            cEntryJson.put(NCR_MOD_CODE_ALIAS, orderLine.getCustomField(NCR_MOD_CODE_ALIAS));
        }
        else {
            cEntryJson.put(NCR_MOD_CODE_ALIAS, ENTRY_MOD_CODES.NONE);
        }

        cEntryJson.put(NCRUtil.ITEM_ORDR_MODE, NCR_ITEM_ORDR_MODE_ALIAS);

        return cEntryJson;
    }

    /**
     * custom logic to calculate item deal ID in PH UAE.
     *
     * @param orderLine
     * @param itemIdentifier
     * @return
     */
    protected String getDealId(OrderLine orderLine, String itemIdentifier, int bookmark, int dealId) {
        int itemDealId = bookmark * 100 * 10000 + Integer.parseInt(itemIdentifier) * 10000 + dealId;
        log.info("deal id: {} for order line: {}", itemDealId, orderLine);
        return String.valueOf(itemDealId);
    }
}
