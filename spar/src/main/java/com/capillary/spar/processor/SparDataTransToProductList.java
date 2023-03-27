package com.capillary.spar.processor;

import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.entity.Product;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.rest.model.FieldErrorModel;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component("SparDataTranToProductList")
@Documented(
        description = "convert list of string data fetched from csv file into product object list",
        inBody = @KeyInfo(type = List.class, comment = "contains the list of string of data"),
        outBody = @KeyInfo(type = List.class,  comment = "contains the list of product object"),
        inHeaders = @KeyInfo(name = "ExchangeHeaderKeys.CSV_HEADER", comment = "csv header of file"))
public class SparDataTransToProductList extends DarbyBaseProcessor {
    private static final String PRODUCT_TYPE = "P";
    private static final String STD_PRODUCT_TYPE = "HSN";
    @Override
    public void startProcess(Exchange exchange) {
        List<String> dataList = ExchangeUtil.getBody(exchange, List.class);
        final String csvHeader = ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.CSV_HEADER,
                exchange);
        int transformDatalistSize = dataList.size();
        List<FieldErrorModel> validationStatuses = new ArrayList<>();
        log.info("size of transformed data for creating shipment is {}", transformDatalistSize);
        List<Product> productList = new ArrayList<>();
        boolean isCsvHeader = Boolean.FALSE;

        for (int i= 0; i< transformDatalistSize; i++)
        {
            String filteredLine =
                    dataList.get(i).replaceAll("\ufeff", StringUtils.EMPTY)
                            .replaceAll("\\r", StringUtils.EMPTY);

            if (filteredLine.equals(csvHeader)) {
                log.info("skipping line - {} as it is equal to csv header - {}", filteredLine, csvHeader);
                isCsvHeader = Boolean.TRUE;
                continue;
            }

            List<String> productTransformData = Arrays.asList(dataList.get(i).split(";"));
            try {
                Product productObject = createProductObject(productTransformData);
                productList.add(productObject);
            }
            catch (DarbyException e)
            {
                String errMsg = e.getErrorCode().getMessage();
                validationStatuses.add(new FieldErrorModel(filteredLine,  errMsg, e.getErrorCode().name()));
            }
        }
        batchProcessingDetails(exchange, validationStatuses, isCsvHeader);
        log.info("size of product list is {}", productList.size());
        ExchangeUtil.setBody(productList, exchange);
    }

    private Product createProductObject(List<String> productTransformData) {
        Product product = new Product();
        try {

            String sku = productTransformData.get(0);
            String hsnCode = productTransformData.get(1);
            String taxCode = productTransformData.get(2);
            product.setSku(sku);
            product.setProductType(PRODUCT_TYPE);
            product.setTaxCode(taxCode);
            product.setStdProductCode(new BigDecimal(hsnCode));
            product.setStdProductType(STD_PRODUCT_TYPE);
            return product;
        }
        catch (IndexOutOfBoundsException e)
        {
            String errMsg = "did not get all the data in transformed data list "+productTransformData+" with error "
                    +e.getMessage();
            log.error(errMsg, e, ErrorCode.INVALID);
            throw new DarbyException(errMsg, e, ErrorCode.INVALID);
        }
    }

    private void batchProcessingDetails(Exchange exchange, List<FieldErrorModel> validationStatuses,
                                        boolean isCSVHeader) {

        BatchProcessDetails batchProcessDetails = null;
        List<FieldErrorModel> fieldErrorModels = null;
        batchProcessDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAIL, exchange,
                        new BatchProcessDetails());
        fieldErrorModels = batchProcessDetails.getFieldErrorModelList();
        fieldErrorModels = fieldErrorModels == null ? new ArrayList<>() : fieldErrorModels;
        String fileName = (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILE_NAME,
                exchange, "SPAR_PRODUCT_FILE");
        batchProcessDetails.setFileName(fileName);
        batchProcessDetails.setValidationCount(
                batchProcessDetails.getValidationCount() + validationStatuses.size());
        if (isCSVHeader)
        {
            batchProcessDetails.setTotalCount(batchProcessDetails.getTotalCount() - 1);
        }
        fieldErrorModels.addAll(validationStatuses);
        batchProcessDetails.setFieldErrorModelList(fieldErrorModels);
        log.info("adding the data in batch object {}", batchProcessDetails);
        ExchangeHeaderKeys.setInHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAIL, batchProcessDetails, exchange);
    }
}
