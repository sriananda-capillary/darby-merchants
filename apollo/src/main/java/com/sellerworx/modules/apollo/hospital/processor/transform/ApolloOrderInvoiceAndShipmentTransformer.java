package com.sellerworx.modules.apollo.hospital.processor.transform;

import com.google.common.base.Objects;
import com.sellerworx.darby.core.context.TenantContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.Order;
import com.sellerworx.darby.entity.OrderLine;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.*;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import com.sellerworx.modules.apollo.hospital.dto.*;
import com.sellerworx.modules.apollo.hospital.filter.ApolloOrderSourceFilter;
import com.sellerworx.modules.invoice.model.OrderDetails;
import com.sellerworx.modules.invoice.model.OrderInvoiceInputFeed;
import com.sellerworx.modules.martjack.services.OrderService;
import com.sellerworx.modules.martjack.util.MJExchangeHeaderKeys;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("ApolloOrderInvoiceAndShipmentTransformer")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Documented(description = "Transforms Apollo invoice feed into orders for Micros",
            inHeaders = {@KeyInfo(comment = "apollo invoice feed pojo",
                                  name = "MJExchangeHeaderKeys.INVOICE_FEED_JSON")},
            outBody = @KeyInfo(comment = "transformed list of payload for invoice and shipment api call",
                               name = "ApolloTransformedPayload.class"))
public class ApolloOrderInvoiceAndShipmentTransformer extends DarbyBaseProcessor {

    static final String UOM = StringUtils.EMPTY;
    private static final String DEFAULT_DATE = "1900-01-01";

    @Autowired
    OrderService mjOrderService;

    @Autowired
    ApolloOrderSourceFilter apolloOrderSourceFilter;

    @Override
    public void startProcess(Exchange exchange) {

        ApolloTransformedPayload apolloTransformedPayload = new ApolloTransformedPayload();

        List<ApolloInvoiceReqPayload> invoicePayloadList = new ArrayList<>();
        List<ApolloShipmentReqPayload> shipmentPayloadList = new ArrayList<>();

        OrderInvoiceInputFeed[] invoiceFeedArr =
                ExchangeHeaderKeys.getValueFromExchangeHeader(
                        MJExchangeHeaderKeys.ORDER_INVOICE_FILE_FEED, exchange);

        for (OrderInvoiceInputFeed invoiceFeed : invoiceFeedArr) {

            String ecomOrderId = invoiceFeed.getOrderId();

            Order ecomOrder = mjOrderService.orderInfoV2(ecomOrderId);

            ExchangeUtil.setBody(ecomOrder, exchange);
            if (apolloOrderSourceFilter.isOrderSource(exchange)) {

                ApolloInvoiceReqPayload.ApolloInvoiceReqPayloadBuilder apolloInvoiceReqPayloadBuilder =
                        ApolloInvoiceReqPayload.builder();

                ApolloShipmentReqPayload.ApolloShipmentReqPayloadBuilder apolloShipmentReqPayloadBuilder =
                        ApolloShipmentReqPayload.builder();

                List<ApolloInvoiceReqPayload.OrderDetails> invoiceBatchDetails = new ArrayList<>();
                List<ApolloShipmentReqPayload.OrderDetails> shipmentOrderBatchDetails = new ArrayList<>();


                if (CollectionUtils.isNotEmpty(invoiceFeed.getOrderDetails())) {
                    List<OrderDetails> invoiceBatchesList = invoiceFeed.getOrderDetails();

                    GroupByAny<OrderDetails> groupByItemCode =
                            new ApolloOrderInvoiceAndShipmentTransformer.GroupByItemCode();

                    List<List<OrderDetails>> invoiceBatches = Grouper.group(invoiceBatchesList, groupByItemCode);

                    for (List<OrderDetails> invoiceBatchesItemCode : invoiceBatches) {
                        final String sku = invoiceBatchesItemCode.get(0).getItemCode();
                        final String itemDescription = getItemDescByItemCode(ecomOrder, sku);
                        Integer quantityInvoiced = getTotalInvoiceQty(invoiceBatchesItemCode);
                        final String uom = UOM;
                        BigDecimal unitPriceValue = new BigDecimal(invoiceBatchesItemCode.get(0).getCost());
                        BigDecimal taxPercentage = getTotalTaxPercentFromFirstBatch(invoiceBatchesItemCode);
                        BigDecimal taxAmount = getTotalTaxAmount(invoiceBatchesItemCode);
                        BigDecimal totalTaxAmount = getTotalTaxAmountMultipleQty(invoiceBatchesItemCode);
                        BigDecimal discountPercentage = new BigDecimal(
                                invoiceBatchesItemCode.get(0).getDiscount());
                        BigDecimal discountValue = new BigDecimal(invoiceBatchesItemCode.get(0).getDiscountAmt());
                        BigDecimal totalAmount = new BigDecimal(invoiceBatchesItemCode.get(0).getNetAmount());
                        BigDecimal mrp = new BigDecimal(invoiceBatchesItemCode.get(0).getMrp());

                        /**
                         * create invoice payload orderline details
                         */
                        ApolloInvoiceReqPayload.OrderDetails.OrderDetailsBuilder invoiceOrderLineBuilder =
                                ApolloInvoiceReqPayload.OrderDetails.builder();

                        invoiceOrderLineBuilder.sku(sku)
                                               .itemDescription(itemDescription)
                                               .quantityInvoiced(quantityInvoiced)
                                               .uom(uom)
                                               .unitPriceValue(unitPriceValue)
                                               .taxPercentage(taxPercentage)
                                               .taxAmount(taxAmount)
                                               .totalTaxAmount(totalTaxAmount)
                                               .discountPercentage(discountPercentage)
                                               .discountValue(discountValue)
                                               .totalAmount(totalAmount)
                                               .mrp(mrp);

                        /**
                         * create shipment payload orderline details
                         */
                        ApolloShipmentReqPayload.OrderDetails.OrderDetailsBuilder shipmentOrderLineBuilder =
                                ApolloShipmentReqPayload.OrderDetails.builder();

                        shipmentOrderLineBuilder.sku(sku)
                                                .itemDescription(itemDescription)
                                                .orderedQty(getTotalQty(invoiceBatchesItemCode))
                                                .uom(uom);

                        shipmentOrderLineBuilder.batchDetails(
                                getShipmentPayloadLineBatchDetail(invoiceBatchesItemCode));

                        invoiceBatchDetails.add(invoiceOrderLineBuilder.build());
                        shipmentOrderBatchDetails.add(shipmentOrderLineBuilder.build());
                    }

                    String orderDate = invoiceFeed.getOrderDate();
                    if (StringUtils.isNotBlank(orderDate)) {
                        orderDate = TransformUtil.convertDateFromOnePatternToAnother(
                                orderDate, DatePatternUtil.DD_MMM_YY_FORMAT, TenantContext.getTimeZone(),
                                DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT);
                    }

                    String shipmentDate = invoiceFeed.getShipmentDate();
                    if (StringUtils.isNotBlank(shipmentDate)) {
                        shipmentDate = TransformUtil.convertDateFromOnePatternToAnother(
                                shipmentDate, DatePatternUtil.DD_MMM_YYYY_FORMAT, TenantContext.getTimeZone(),
                                DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT);
                    }

                    String dcDate = invoiceFeed.getDeliveryChallanDate();
                    if (StringUtils.isNotBlank(dcDate)) {
                        dcDate = TransformUtil.convertDateFromOnePatternToAnother(
                                dcDate, DatePatternUtil.DD_MMM_YYYY_FORMAT, TenantContext.getTimeZone(),
                                DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT);
                    }

                    String invoiceDate = invoiceFeed.getInvoiceDate();
                    if (StringUtils.isNotBlank(invoiceDate)) {
                        invoiceDate = TransformUtil.convertDateFromOnePatternToAnother(
                                invoiceDate, DatePatternUtil.DD_MMM_YYYY_FORMAT, TenantContext.getTimeZone(),
                                DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT);
                    }


                    apolloInvoiceReqPayloadBuilder.buyerOrderResponseNumber(ecomOrder.getReferenceNo())
                                                  .sellerOrderResponseNumber(invoiceFeed.getOrderId())
                                                  .invoiceNumber(invoiceFeed.getInvoiceNumber())
                                                  .deliveryNoteNumber(invoiceFeed.getShipmentNumber())
                                                  .orderResponseIssueDate(orderDate)
                                                  .shipmentNo(invoiceFeed.getShipmentNumber())
                                                  .orderShippedDate(shipmentDate)
                                                  .invoiceDate(invoiceDate)
                                                  .netAmount(new BigDecimal(invoiceFeed.getNetAmount()))
                                                  .totalTax(new BigDecimal(invoiceFeed.getGstAmount()))
                                                  .grossAmount(
                                                          new BigDecimal(invoiceFeed.getAmount()))
                                                  .totalDiscountValue(getTotalDiscountValue(invoiceBatchesList))
                                                  .buyerPartyAddress(getBuyerPartyDetail(ecomOrder))
                                                  .orderDetails(invoiceBatchDetails);

                    apolloShipmentReqPayloadBuilder.buyerOrderResponseNumber(ecomOrder.getReferenceNo())
                                                   .sellerOrderResponseNumber(invoiceFeed.getOrderId())
                                                   .shipmentNumber(invoiceFeed.getShipmentNumber())
                                                   .orderResponseIssueDate(orderDate)
                                                   .orderShippedDate(shipmentDate)
                                                   .dcNumber(invoiceFeed.getDeliveryChallanNumber())
                                                   .dcDate(dcDate)
                                                   .buyerPartyAddress(getBuyerPartyDetail(ecomOrder))
                                                   .orderDetails(shipmentOrderBatchDetails);
                }

                invoicePayloadList.add(apolloInvoiceReqPayloadBuilder.build());
                shipmentPayloadList.add(apolloShipmentReqPayloadBuilder.build());
            }
            else {
                log.info("skipping order transformation for order id :{}, as order source attributes: {}",
                         ecomOrderId, ecomOrder.getOrderAttributes());
            }
        }

        apolloTransformedPayload.setInvoicePayloadList(invoicePayloadList);
        apolloTransformedPayload.setShipmentPayloadList(shipmentPayloadList);

        ExchangeUtil.setBody(apolloTransformedPayload, exchange);
    }

    /**
     * get shipment payload batch details.
     *
     * @param invoiceBatchesItemCode
     * @return
     */
    private List<ApolloShipmentReqPayload.BatchDetails> getShipmentPayloadLineBatchDetail(
            List<OrderDetails> invoiceBatchesItemCode) {
        List<ApolloShipmentReqPayload.BatchDetails> shipmentBatchDetails = new ArrayList<>();

        for (OrderDetails invoiceBatch : invoiceBatchesItemCode) {

            ApolloShipmentReqPayload.BatchDetails.BatchDetailsBuilder shipmentBatch =
                    ApolloShipmentReqPayload.BatchDetails.builder();

            shipmentBatch.batchLotNo(invoiceBatch.getBatchNumber());
            shipmentBatch.shippedQty(
                    (int) Double.parseDouble(invoiceBatch.getInvoicedQty()));
            shipmentBatch.manufacturedDate(DEFAULT_DATE);
            shipmentBatch.expiryDate(StringUtils.isNotBlank(invoiceBatch.getExpDate()) ?
                                     TransformUtil.convertDateFromOnePatternToAnother(
                                             invoiceBatch.getExpDate(),
                                             DatePatternUtil.DATE_FORMAT_DD_MM_YYYY_DASHSEPERATED,
                                             TenantContext.getTimeZone(),
                                             DatePatternUtil.YYYY_MM_DD_HYPHEN_FORMAT) :
                                     StringUtils.EMPTY);
            shipmentBatch.unitPrice(new BigDecimal(invoiceBatch.getCost()));
            shipmentBatch.mrp(new BigDecimal(invoiceBatch.getMrp()));

            shipmentBatchDetails.add(shipmentBatch.build());
        }
        return shipmentBatchDetails;
    }

    /**
     * get Buyer party details from ecom order.
     *
     * @param ecomOrder
     * @return
     */
    private ApolloBuyerPartyAddressReq getBuyerPartyDetail(Order ecomOrder) {
        ApolloBuyerPartyAddressReq.ApolloBuyerPartyAddressReqBuilder
                buyerPartyAddressBuilder = ApolloBuyerPartyAddressReq.builder();
        buyerPartyAddressBuilder.buyerPartName(ecomOrder.getShipFirstname() + " " + ecomOrder.getShipLastname())
                                .addressStreet(ecomOrder.getShipAddress1())
                                .addressLocation(ecomOrder.getShipAddress2())
                                .addressArea(getAddressArea(ecomOrder))
                                .city(ecomOrder.getShipCity())
                                .state(ecomOrder.getShipState());
        return buyerPartyAddressBuilder.build();

    }

    /**
     * get Item product title from ecom order
     *
     * @param ecomOrder
     * @param itemCode
     * @return
     */
    private String getItemDescByItemCode(Order ecomOrder, String itemCode) {
        for (OrderLine orderLine : ecomOrder.getOrderLines()) {
            if (itemCode.equalsIgnoreCase(orderLine.getSku()) || itemCode.equalsIgnoreCase(
                    orderLine.getVariantSku())) {
                return orderLine.getProductTitle();
            }
        }
        log.error("item code: {}, not found in ecom order: {}", itemCode, ecomOrder);
        throw new DarbyException("itemcode: " + itemCode + ", not found in ecom order: " + ecomOrder,
                                 ErrorCode.NOTFOUND);
    }

    /**
     * get total amount multiple with invoice QTY
     *
     * @param invoiceBatchesItemCode
     * @return
     */
    private BigDecimal getTotalTaxAmountMultipleQty(List<OrderDetails> invoiceBatchesItemCode) {
        BigDecimal taxAmount = getTotalTaxAmount(invoiceBatchesItemCode);
        return taxAmount.multiply(new BigDecimal(invoiceBatchesItemCode.get(0).getQty()));
    }

    /**
     * total amount is IGST + CGST + SGST
     * this tax must be same among all batches.
     *
     * @param invoiceBatchesItemCode
     * @return
     */
    private BigDecimal getTotalTaxAmount(List<OrderDetails> invoiceBatchesItemCode) {
        OrderDetails batch = invoiceBatchesItemCode.get(0);
        return new BigDecimal(batch.getCgstAmount()).add(
                new BigDecimal(batch.getSgstAmount()).add(new BigDecimal(batch.getIgstAmount())));
    }

    /**
     * total discount calc for parent Invoice object
     *
     * @param orderDetails
     * @return
     */
    private BigDecimal getTotalDiscountValue(List<OrderDetails> orderDetails) {
        BigDecimal totalDiscountValue = BigDecimal.ZERO;
        for (OrderDetails orderDetail : orderDetails) {
            BigDecimal discountAmount = new BigDecimal(orderDetail.getDiscountAmt());
            if (BigDecimal.ZERO.compareTo(discountAmount) != 0) {
                BigDecimal qty = new BigDecimal(orderDetail.getQty());
                totalDiscountValue = totalDiscountValue.add(discountAmount.multiply(qty));
            }
        }
        return totalDiscountValue;
    }

    /**
     * total percentage is IGST + CGST + SGST
     * this tax must be same among all batches.
     *
     * @param invoiceBatchesItemCode
     * @return
     */
    private BigDecimal getTotalTaxPercentFromFirstBatch(List<OrderDetails> invoiceBatchesItemCode) {
        OrderDetails batch = invoiceBatchesItemCode.get(0);
        return new BigDecimal(batch.getCgst()).add(
                new BigDecimal(batch.getSgst()).add(new BigDecimal(batch.getIgst())));
    }

    /**
     * Get total invoice quantity.
     *
     * @param invoiceBatches
     * @return
     */
    private Integer getTotalInvoiceQty(List<OrderDetails> invoiceBatches) {
        int totalInvoice = 0;
        for (OrderDetails invoiceBatch : invoiceBatches) {
            totalInvoice += (int) Double.parseDouble(invoiceBatch.getInvoicedQty());
        }
        return totalInvoice;
    }

    private Integer getTotalQty(List<OrderDetails> invoiceBatches) {
        int totalInvoice = 0;
        for (OrderDetails invoiceBatch : invoiceBatches) {
            totalInvoice += (int) Double.parseDouble(invoiceBatch.getQty());
        }
        return totalInvoice;
    }

    private String getAddressArea(Order ecomOrder) {
        List<String> details = new ArrayList<>();
        details.add(ecomOrder.getBillAddress1());
        details.add(ecomOrder.getBillAddress2());
        details.add(ecomOrder.getBillCity());
        details.add(ecomOrder.getBillingState());
        details.add(ecomOrder.getBillZIP());

        return StringUtils.join(details, SymbolUtil.PIPE);

    }

    private class GroupByItemCode implements GroupByAny<OrderDetails> {
        @Override
        public Object getKey(OrderDetails invoiceBatch) {
            return new ApolloOrderInvoiceAndShipmentTransformer.GroupByItemCode.ItemCode(invoiceBatch);
        }

        @Data
        @FieldDefaults(level = AccessLevel.PRIVATE)
        private class ItemCode {

            String itemCode;

            public ItemCode(OrderDetails invoiceBatch) {
                this.itemCode = invoiceBatch.getItemCode();
                if (StringUtils.isBlank(itemCode)) {
                    log.error("item code is empty in invoice batch: {}", invoiceBatch);
                    throw new DarbyException("item code is empty in invoice batch: " + invoiceBatch,
                                             ErrorCode.EMPTY);
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof ApolloOrderInvoiceAndShipmentTransformer.GroupByItemCode.ItemCode)) {
                    return false;
                }
                ApolloOrderInvoiceAndShipmentTransformer.GroupByItemCode.ItemCode itemCodeGrouping =
                        (ApolloOrderInvoiceAndShipmentTransformer.GroupByItemCode.ItemCode) o;
                return StringUtils.equalsIgnoreCase(itemCode, itemCodeGrouping.itemCode);
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(itemCode.toLowerCase());
            }
        }

    }
}
