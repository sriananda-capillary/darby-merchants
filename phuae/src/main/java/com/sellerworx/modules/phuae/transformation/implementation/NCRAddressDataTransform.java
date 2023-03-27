package com.sellerworx.modules.phuae.transformation.implementation;

import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.OrderCustomKeys;
import com.sellerworx.darby.util.SymbolUtil;
import com.sellerworx.darby.util.Util;
import com.sellerworx.modules.martjack.transformer.order.OrderTransformer;
import com.sellerworx.modules.ncr.util.NCRConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.*;

/**
 * this transformer take specific input below and generate output.
 * Input :
 * {shipAddress1a=ADDRESS_BUILDING_NAME, shipAddress1b=ADDRESS_FLOOR_NO, shipAddress2a=ADDRESS_FLAT_NO,
 * shipAddress2b=ADDRESS_ROAD, addrWdirections=ADDRESS_WDIRECTIONS, addrSketch=ADDRESS_SKETCH,
 * addrDesc=ADDRESS_DESCRIPTION, addrDescFields=ADDRESS_FLAT_NO,ADDRESS_ROAD}
 *
 * Output:
 * {shipAddress1a=ADDRESS_BUILDING_NAME, shipAddress1b=ADDRESS_FLOOR_NO, shipAddress2a=ADDRESS_FLAT_NO,
 * shipAddress2b=ADDRESS_ROAD, addrWdirections=ADDRESS_WDIRECTIONS, addrSketch=ADDRESS_SKETCH,
 * addrDesc=ADDRESS_DESCRIPTION, addrDescFields=ADDRESS_FLAT_NO,ADDRESS_ROAD}
 */
@Slf4j
@Component("NCRAddressDataTransform")
public class NCRAddressDataTransform implements OrderTransformer {

    private final String ADDRESS_MAP = "addressMap";
    private static final String PIPE_DELIMITER_WITH_SPACES = StringUtils.SPACE + SymbolUtil.PIPE + StringUtils.SPACE;
    private static final String COMMA_DELIMITER_WITH_SPACES = SymbolUtil.COMMA + StringUtils.SPACE;

    @Override
    public void transform(Exchange exchange, Order order) {

        Map<String, String> tenantConfigMap =
                (Map<String, String>) exchange.getIn().getHeader(ExchangeHeaderKeys.TENANT_CONFIG_MAP);

        Map<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1A, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1A, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1B, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1B, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2A, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2A, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2B, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2B, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_SKETCH, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_SKETCH, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_WDIRECTIONS, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_WDIRECTIONS, exchange, false));
        addressMap.put(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC_FIELDS, (String) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC_FIELDS, exchange, false));

        order.addCustomField(ADDRESS_MAP, addressMap);

        transformAddress(order, tenantConfigMap);

        /**
         * transform city code.
         */
        order.addCustomField("shipCityCode", order.getShipCityCode());
        order.addCustomField("billCityCode", order.getBillCityCode());
    }

    /**
     * Transform address.
     *
     * @param order           the order
     * @param tenantConfigMap the tenant config map
     */
    protected void transformAddress(Order order, Map<String, String> tenantConfigMap) {

        log.info("shipaddress1 {}, shipaddress2 {} ", order.getShipAddress1(), order.getShipAddress2());
        String[] shipAddress1 = new String[2];
        Arrays.fill(shipAddress1, StringUtils.EMPTY);

        if (StringUtils.isNotBlank(order.getShipAddress1())) {
            if (order.getShipAddress1().contains(SymbolUtil.PIPE)) {
                shipAddress1 = Util.splitStringByDelimiter(order.getShipAddress1(), SymbolUtil.PIPE, 2);
            }
            else if (order.getShipAddress1().length() >= 1) {
                shipAddress1[0] = order.getShipAddress1();
                shipAddress1[1] = StringUtils.EMPTY;
            }
        }

        Map<String, String> addressMap = new HashMap<String, String>();
        if (null != order.getCustomField(ADDRESS_MAP)) {
            addressMap = (Map<String, String>) order.getCustomField(ADDRESS_MAP);
        }
        Map<String, Integer> addrFieldsLengthMap = buildAddrFieldsLengthMap(tenantConfigMap);

        String shipAddress1a = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1A, addressMap);
        String shipAddress1b = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS1B, addressMap);

        log.info("shipaddress1a {}, shipaddress1b {}", shipAddress1a, shipAddress1b);

        if (StringUtils.isNotBlank(shipAddress1a)) {
            truncateAddressFieldsBasedOnLength(order, shipAddress1a, Util.getHtmlDecodedText(shipAddress1[0]),
                                               addrFieldsLengthMap.get(shipAddress1a));
        }

        if (StringUtils.isNotBlank(shipAddress1b)) {
            truncateAddressFieldsBasedOnLength(order, shipAddress1b, Util.getHtmlDecodedText(shipAddress1[1]),
                                               addrFieldsLengthMap.get(shipAddress1b));
        }

        String[] shipAddress2 = new String[2];
        Arrays.fill(shipAddress2, StringUtils.EMPTY);

        if (StringUtils.isNotBlank(order.getShipAddress2())) {
            if (order.getShipAddress2().contains(SymbolUtil.PIPE)) {
                shipAddress2 = Util.splitStringByDelimiter(order.getShipAddress2(), SymbolUtil.PIPE, 2);
            }
            else if (order.getShipAddress2().length() >= 1) {
                shipAddress2[0] = order.getShipAddress2();
                shipAddress2[1] = StringUtils.EMPTY;
            }
        }

        String shipAddress2a = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2A, addressMap);
        String shipAddress2b = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_SHIP_ADDRESS2B, addressMap);

        log.info("shipaddress2a {}, shipaddress2b {}", shipAddress2a, shipAddress2b);

        if (StringUtils.isNotBlank(shipAddress2a)) {
            truncateAddressFieldsBasedOnLength(order, shipAddress2a, Util.getHtmlDecodedText(shipAddress2[0]),
                                               addrFieldsLengthMap.get(shipAddress2a));
        }

        if (StringUtils.isNotBlank(shipAddress2b)) {
            truncateAddressFieldsBasedOnLength(order, shipAddress2b, Util.getHtmlDecodedText(shipAddress2[1]),
                                               addrFieldsLengthMap.get(shipAddress2b));
        }

        String shipAddr[] = ArrayUtils.addAll(shipAddress1, shipAddress2);

        log.info("address details from martjack {}", Arrays.toString(shipAddr));

        String shipAddrSketch = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_SKETCH, addressMap);
        String shipAddrWdirections =
                Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_WDIRECTIONS, addressMap);

        log.info("shipAddrSketch {}, shipAddrWdirections {}", shipAddrSketch, shipAddrWdirections);

        /*
         *  getting only the address fields having non-empty values to
         *  avoid extra pipe delimiter for blank fields
         *  eg : if shipAddr has [building, , ,flat]
         *  shipAddrList will have {building,flat}
         *  sending shipAddress as building | flat instead of building | | | flat
         */
        List<String> shipAddrList = Util.getNonEmptyValues(shipAddr);
        String shipAddress = Util.getHtmlDecodedText(String.join(PIPE_DELIMITER_WITH_SPACES, shipAddrList));

        order.addCustomField(shipAddrSketch, shipAddress);
        order.addCustomField(shipAddrWdirections, shipAddress);

        String shipAddrDesc = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC, addressMap);
        String shipAddrDescFields = Util.getValueOrEmpty(ExchangeHeaderKeys.HEADER_NCR_ADDRESS_DESC_FIELDS, addressMap);

        log.info("shipAddressDesc {}, shipAddrDescFields {}", shipAddrDesc, shipAddrDescFields);

        List<String> shipAddrDescList = new ArrayList<String>();

        /*
         *  getting the address field names to be mapped to address desc
         *  and creating a list of nonempty values
         */
        if (StringUtils.isNotBlank(shipAddrDescFields)) {
            shipAddrDescList = Util.getNonEmptyValues(shipAddrDescFields.split(SymbolUtil.COMMA));
        }

        String orderAddressDesc = StringUtils.EMPTY;

        String orderAddrDescArray[] = new String[shipAddrDescList.size()];
        /*
         * mapping the corresponding address field names to address field values
         */
        for (int i = 0; i < shipAddrDescList.size(); i++) {

            Object shipAddrDescItem = order.getCustomField(shipAddrDescList.get(i));
            orderAddrDescArray[i] = (null == shipAddrDescItem) ? StringUtils.EMPTY
                                                               : Util.getHtmlDecodedText(shipAddrDescItem.toString());
        }

        log.info("shipAddrDescList {}, orderAddrDescArray {}", shipAddrDescList,
                 Arrays.toString(orderAddrDescArray));

        orderAddressDesc = String.join(COMMA_DELIMITER_WITH_SPACES, Util.getNonEmptyValues(orderAddrDescArray));

        log.info("orderAddressDesc {}", orderAddressDesc);
        order.addCustomField(shipAddrDesc, orderAddressDesc);

        /*
         * The address fields which are not part of exchange header
         * will be null.so setting the values to empty and passing
         * to NCR
         */
        for (String addrField : addrFieldsLengthMap.keySet()) {
            setEmptyForNullFields(order, addrField);
        }

        setEmptyForNullFields(order, OrderCustomKeys.ADDRESS_SKETCH);
        setEmptyForNullFields(order, OrderCustomKeys.ADDRESS_WDIRECTIONS);
        setEmptyForNullFields(order, OrderCustomKeys.ADDRESS_DESCRIPTION);

        log.info(
                "transformed address details, building {}, floor {}, flat {}, road {}, wdirections {}, sketch {}, description {}",
                order.getCustomField(OrderCustomKeys.ADDRESS_BUILDING_NAME),
                order.getCustomField(OrderCustomKeys.ADDRESS_FLOOR_NO),
                order.getCustomField(OrderCustomKeys.ADDRESS_FLAT_NO),
                order.getCustomField(OrderCustomKeys.ADDRESS_ROAD),
                order.getCustomField(OrderCustomKeys.ADDRESS_WDIRECTIONS),
                order.getCustomField(OrderCustomKeys.ADDRESS_SKETCH),
                order.getCustomField(OrderCustomKeys.ADDRESS_DESCRIPTION));
    }

    /**
     * This method is used for building map of fields with its
     * length restrictions
     * This is used for mapping
     * during the truncation of fields based on length
     *
     * @return
     */
    private Map<String, Integer> buildAddrFieldsLengthMap(Map<String, String> tenantConfigMap) {

        Map<String, Integer> addrFieldsLengthMap = new HashMap<String, Integer>();
        addrFieldsLengthMap.put(OrderCustomKeys.ADDRESS_BUILDING_NAME, Util.convertToInteger(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.NCR_ADDR_BUILDING_NAME_LENGTH, tenantConfigMap)));
        addrFieldsLengthMap.put(OrderCustomKeys.ADDRESS_FLOOR_NO, Util.convertToInteger(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.NCR_ADDR_FLOOR_NO_LENGTH, tenantConfigMap)));
        addrFieldsLengthMap.put(OrderCustomKeys.ADDRESS_FLAT_NO, Util.convertToInteger(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.NCR_ADDR_FLAT_NO_LENGTH, tenantConfigMap)));
        addrFieldsLengthMap.put(OrderCustomKeys.ADDRESS_ROAD, Util.convertToInteger(
                NCRConfigKeys.getValueFromTenantConfig(NCRConfigKeys.NCR_ADDR_ROAD_NAME_LENGTH, tenantConfigMap)));
        return addrFieldsLengthMap;
    }

    private void truncateAddressFieldsBasedOnLength(Order order, String fieldName, String fieldValue, int length) {
        Charset charSet = Charset.forName(Util.CHARACTER_SET_UTF8);
        if (fieldValue.getBytes(charSet).length >= length) {
            order.addCustomField(fieldName, Util.truncateToFitUtf8ByteLength(fieldValue, length));
        }
        else {
            order.addCustomField(fieldName, fieldValue);
        }
    }

    private void setEmptyForNullFields(Order order, String addrField) {
        if (null == order.getCustomField(addrField)) {
            order.addCustomField(addrField, StringUtils.EMPTY);
        }
    }
}