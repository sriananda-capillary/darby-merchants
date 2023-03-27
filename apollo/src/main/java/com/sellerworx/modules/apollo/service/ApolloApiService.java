package com.sellerworx.modules.apollo.service;

import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.rest.RestModel;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.RestService;
import com.sellerworx.darby.util.TransformUtil;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAckOrderReqPayload;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAuthCredentials;
import com.sellerworx.modules.apollo.hospital.dto.ApolloAuthenticateResult;
import com.sellerworx.modules.apollo.util.ApolloTenantConfigKeys;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class ApolloApiService {

    public static final String ORDER_ACKNOWLEDGE_ENDPOINT = "/Acknowledgement";
    public static final String CREATE_SHIPMENT_ENDPOINT = "Shipment";
    public static final String CREATE_INVOICE_ENDPOINT = "Invoice";

    protected static final String AUTHENTICATE_ENDPOINT = "/users/Authenticate";
    private static final String POS_NAME = "apollo";
    private final ApolloApiService _selfReference;

    @Autowired
    RestService restSvc;

    public ApolloApiService() {
        this._selfReference = this;
    }


    protected static String getPosName() {
        return POS_NAME;
    }

    protected abstract String getEntityName();

    protected String joinHostAndEndPointEx(String host, String endPoint) {
        URI uri = null;
        try {
            uri = new URI(host + "/" + endPoint);
        } catch (URISyntaxException e) {
            String errMsg = "invalid apollo api host - " + host;
            throw new DarbyException(errMsg, ErrorCode.INVALID, e);
        }

        return uri.toString();
    }

    protected RestModel getRestModel(final String host, final String apiName) {
        return new RestModel(host, apiName, getPosName(), getEntityName());
    }

    @Cacheable(value = "apollo.users.authenticate",
               key = "#credentials.getUsername()",
               condition = "#isCache")
    public ApolloAuthenticateResult authenticate(ApolloAuthCredentials credentials, boolean isCache) {

        final String apolloApiHost = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_HOST);

        String apolloApiAuthUri = joinHostAndEndPointEx(apolloApiHost,
                                                        ApolloApiService.AUTHENTICATE_ENDPOINT);
        RestModel authenticateReqRestModel = new RestModel(apolloApiAuthUri, "authenticate", getPosName(),
                                                           "users");

        authenticateReqRestModel.setRequestBody(TransformUtil.toJsonEx(credentials));


        authenticateReqRestModel.setAcceptType(MediaType.ALL);
        try {
            authenticateReqRestModel.setHost(URI.create(authenticateReqRestModel.getHost()).normalize()
                                                .toString());
        } catch (IllegalArgumentException illegalArgumentException) {
            log.error("invalid url {}", authenticateReqRestModel.getHost(), illegalArgumentException);
            throw new DarbyException(illegalArgumentException, ErrorCode.INVALID);
        }

        RestService.HttpRespData authenticateRespEnt = restSvc.postReqTransferEncoding(authenticateReqRestModel);

        return new ApolloAuthenticateResult(authenticateRespEnt.getBody());

    }

    protected RestService.HttpRespData postReq(RestModel restModel) {

        RestService.HttpRespData result = postReq(restModel, true);
        if (result.getStatusCode() == HttpStatus.UNAUTHORIZED.value()) {
            return postReq(restModel, false);
        }

        return result;
    }

    private RestService.HttpRespData postReq(RestModel restModel, boolean isAuthCache) {
        Map<String, String> headersMap = restModel.getHeaders();
        headersMap = null == headersMap ? new HashMap<>() : headersMap;
        final String username = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_USERNAME);
        final String password = RequestContext.configStringEx(ApolloTenantConfigKeys.APOLLO_API_PASSWORD);
        ApolloAuthCredentials credentials = new ApolloAuthCredentials(username, password);

        String authorizationHeaderValue = "Bearer " + _selfReference.authenticate(credentials, isAuthCache)
                                                                    .getToken();
        headersMap.put("Authorization", authorizationHeaderValue);

        try {
            restModel.setHost(URI.create(restModel.getHost()).normalize().toString());
        } catch (IllegalArgumentException illegalArgumentException) {
            String errMsg = "invalid url - " + restModel.getHost();
            log.error(errMsg, illegalArgumentException);
            throw new DarbyException(errMsg, illegalArgumentException, ErrorCode.INVALID);
        }
        return restSvc.postReqTransferEncoding(restModel);

    }
}
