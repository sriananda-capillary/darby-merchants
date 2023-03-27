package com.capillary.spar.integration.client;
import com.sellerworx.darby.core.rest.RestModel;
import com.sellerworx.darby.util.RestService;
import com.sellerworx.darby.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("SparLMRUserEnrolledApiClient")
public class SparLMRUserEnrolledApiClient {
    private static final String API_NAME_LMR_ENROLLMENT = "LMREnrollmentAPI";
    private static final String API_NAME_GET_MEMBER_FOR_LMR = "GetMemberForLMR";
    private static final String AUTH_KEY = "AuthKey";
    private static final String POS = "SPAR";
    private static final int READ_TIMEOUT = 5000;
    private static final int CONNECTION_TIMEOUT = 5000;
    @Autowired
    private RestService restService;

    private String getLMREnrollmentURL(String host)
    {
        return Util.appendInUrl(host, SparLMRConfig.getLMREnrollmentEndPoint);
    }

    private String getMemberForLMRURL(String host)
    {
        return Util.appendInUrl(host, SparLMRConfig.getMemberForLMRURLEndPoint);
    }

    public ResponseEntity<String> enrollUserToLMR(String host, String requestBody, String authKey)
    {
        String apiUrl = getLMREnrollmentURL(host);
        RestModel rs = new RestModel(apiUrl, API_NAME_LMR_ENROLLMENT, POS, "user");
        rs.setRequestBody(requestBody);
        rs.setReadTimeoutMiliSec(READ_TIMEOUT);
        rs.setConnTimeoutMiliSec(CONNECTION_TIMEOUT);
        Map<String, String> headers = new HashMap<>();
        headers.put(AUTH_KEY, authKey);
        rs.setHeaders(headers);
        return restService.postReq(rs);
    }

    public ResponseEntity<String> getMemberForLMR(String host, String requestBody, String authKey)
    {
        String apiUrl = getMemberForLMRURL(host);
        RestModel rs = new RestModel(apiUrl, API_NAME_GET_MEMBER_FOR_LMR, POS, "user");
        rs.setRequestBody(requestBody);
        rs.setReadTimeoutMiliSec(READ_TIMEOUT);
        rs.setConnTimeoutMiliSec(CONNECTION_TIMEOUT);
        Map<String, String> headers = new HashMap<>();
        headers.put(AUTH_KEY, authKey);
        rs.setHeaders(headers);
        return restService.postReq(rs);
    }
}
