package com.capillary.spar.integration.service.impl;
import com.capillary.spar.integration.client.SparLMRUserEnrolledApiClient;
import com.capillary.spar.integration.model.GetMemberForLMRPayload;
import com.capillary.spar.integration.model.LMREnrollmentRequest;
import com.capillary.spar.integration.model.LMREnrollmentResult;
import com.capillary.spar.integration.service.SparLMRUserEnrolledService;
import com.capillary.spar.integration.util.SparLMRTenantConfigKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.graphite.annotations.Metric;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class SparLMRUserEnrolledServiceImpl implements SparLMRUserEnrolledService {
    @Autowired
    private SparLMRUserEnrolledApiClient sparUserApiClient;

    @Override
    @Metric(metricName = "LMREnrollUser", type = { Metric.MetricType.METER_RATE, Metric.MetricType.HISTOGRAM_LATENCY },
            publishSuccessFailMetrics = true)
    public LMREnrollmentResult enrollNewUserToLandmark(LMREnrollmentRequest payloadData) {
        ObjectMapper objectMapper = new ObjectMapper();
        String reqBodyStr = StringUtils.EMPTY;
        try {
            reqBodyStr = objectMapper.writeValueAsString(payloadData);
        } catch (JsonProcessingException e) {
            String errorMsg = "unable to parse spar payloadData error: "+e.getMessage();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.INVALID);
        }
        log.info("request payload after transform to string: {}", reqBodyStr);
        String auth_token = RequestContext.configString(SparLMRTenantConfigKeys.SPAR_USER_ENROLL_AUTH_KEY);
        try {
            ResponseEntity<String> apiResponse = sparUserApiClient.enrollUserToLMR(getHost(), reqBodyStr, auth_token);
            LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue( apiResponse.getBody().toString(),
                    LMREnrollmentResult.class);
            log.debug("user enrolled successfully, the response coming: {} with status code {}",
                                            apiResponse.getBody(), apiResponse.getStatusCode());
            return lmrEnrollmentResult;
        } catch (RestClientResponseException e) {
            String errorMsg = "lmrenrollment api called failed with the error coming: " +e.getResponseBodyAsString();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
        } catch (RestClientException e) {
            String errorMsg = "lmrenrollment api called failed with the error coming: " +e.getMessage();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
        } catch (IOException e) {
            String errMsg = "unexpected error while parsing api response " + e.getMessage();
            log.error(errMsg, e, ErrorCode.INVALID);
            throw new DarbyException(errMsg, e, ErrorCode.INVALID);
        }
    }

    @Override
    @Metric(metricName = "getMemberForLMR", type = { Metric.MetricType.METER_RATE, Metric.MetricType.HISTOGRAM_LATENCY },
            publishSuccessFailMetrics = true)
    public LMREnrollmentResult getMemberForLMR(GetMemberForLMRPayload payloadData) {
        ObjectMapper objectMapper = new ObjectMapper();
        String reqBodyStr = StringUtils.EMPTY;
        try {
            reqBodyStr = objectMapper.writeValueAsString(payloadData);
        } catch (JsonProcessingException e) {
            String errorMsg = "unable to parse spar payloadData error: "+e.getMessage();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.INVALID);
        }
        log.info("request payload after transform to string: {}", reqBodyStr);
        String auth_token = RequestContext.configString(SparLMRTenantConfigKeys.SPAR_USER_ENROLL_AUTH_KEY);
        try {
            ResponseEntity<String> apiResponse = sparUserApiClient.getMemberForLMR(getHost(), reqBodyStr, auth_token);
            LMREnrollmentResult lmrEnrollmentResult = objectMapper.readValue( apiResponse.getBody().toString(),
                    LMREnrollmentResult.class);
            log.debug(" response of getmemberforlmr api is: {} with status code",
                                                    apiResponse.getBody(), apiResponse.getStatusCode());
            return lmrEnrollmentResult;
        } catch (RestClientResponseException e) {
            String errorMsg = "getmemberforlmr api called failed with the error coming: " +e.getResponseBodyAsString();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
        } catch (RestClientException e) {
            String errorMsg = "getmemberforlmr api called failed with the error coming: " +e.getMessage();
            log.error(errorMsg, e);
            throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
        } catch (IOException e) {
            String errMsg = "unexpected error while parsing api response " + e.getMessage();
            log.error(errMsg, e, ErrorCode.INVALID);
            throw new DarbyException(errMsg, e, ErrorCode.INVALID);
        }
    }

    private String getHost()
    {
        String sparServiceHost  =  RequestContext.configString(SparLMRTenantConfigKeys.SPAR_LMR_WEBSERVICE_ENDPOINT);
        return sparServiceHost;
    }
}
