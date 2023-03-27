package com.sellerworx.modules.apollo.processor;

import com.sellerworx.darby.alert.AlertHandler;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.entity.BatchProcessDetails;
import com.sellerworx.darby.enums.RESOURCE_STATUS;
import com.sellerworx.darby.service.expectation.util.ExpectationUtil;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.TenantConfigKeys;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("ApolloSendMailProcessor")
@Documented(description = "This processor is used to send the summary notification to the clients based on file type.",
        inHeaders = {
                @KeyInfo(comment = "ftp file details", name = "ExchangeHeaderKeys.BATCH_PROCESS_DETAILS",
                        type = BatchProcessDetails.class),
                @KeyInfo(comment = "mj task ids", name = ExchangeHeaderKeys.MJ_TASKID, type = String.class),
                @KeyInfo(comment = "file prefix", name = ExchangeHeaderKeys.FILE_PREFIX, type = String.class) })

public class ApolloSendMailProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(ApolloSendMailProcessor.class);

    @Autowired
    private ExpectationUtil expectationUtil;

    @Autowired
    private AlertHandler alertHandler;

    @Override
    public void process(Exchange exchange) throws Exception {
        BatchProcessDetails ftpFileDetails = (BatchProcessDetails) ExchangeHeaderKeys
                .getValueFromExchangeHeader(ExchangeHeaderKeys.BATCH_PROCESS_DETAILS, exchange);
        Object taskIdsSet =
                ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.MJ_TASK_ID_SET, exchange, false);
        String filePrefix =
                (String) ExchangeHeaderKeys.getValueFromExchangeHeader(ExchangeHeaderKeys.FILE_PREFIX, exchange);
        if (taskIdsSet != null) {
            ftpFileDetails.setMjTaskIds((Set<String>) taskIdsSet);
        }

        sendDataImportNotification(ftpFileDetails);
    }

    private void sendDataImportNotification(BatchProcessDetails ftpFileDetails) {
        boolean isDataImportNotificationEnabled =
                RequestContext.configBoolean(TenantConfigKeys.IS_DATAIMPORT_NOTIFICATION_ENABLED, false);
        if (isDataImportNotificationEnabled) {
            String reportName = ftpFileDetails.getFileName() + StringUtils.SPACE + "Summary Report";
            String emailBody = reportName
                               + ":<br/>"
                               + "1. FileName :- "
                               + ftpFileDetails.getFileName()
                               + "<br/>"
                               + "2. Total no of records in file :- "
                               + ftpFileDetails.getTotalCount()
                               + "<br/>"
                               + "3. Total no of validation rows :- "
                               + ftpFileDetails.getValidationCount()
                               + "<br/>"
                               + "4. Task ids :- "
                               + ftpFileDetails.getMjTaskIds().toString();
            logger.info("{} mail body : {}", reportName, emailBody);
            sendNotificationByConfiguration(reportName, emailBody, RESOURCE_STATUS.COMPLETED);
        }
    }

    private void sendNotificationByConfiguration(String mailTitle, String emailBody, RESOURCE_STATUS status) {
        boolean isSendMailThroughSMTP = RequestContext.configBoolean(TenantConfigKeys.IS_MAIL_SEND_THROUGH_SMTP, false);
        if (isSendMailThroughSMTP) {
            String alertAddresses = RequestContext.configStringEx(TenantConfigKeys.CLIENT_ALERT_ADDRESS);
            alertHandler.alert(mailTitle, emailBody, alertAddresses);
        } else {
            expectationUtil.expectationServiceCallAsync(emailBody, status, getClass().getSimpleName());
        }
    }
}