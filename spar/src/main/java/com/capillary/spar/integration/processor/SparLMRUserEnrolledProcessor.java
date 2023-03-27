package com.capillary.spar.integration.processor;
import com.capillary.spar.integration.model.GetMemberForLMRPayload;
import com.capillary.spar.integration.model.LMREnrollmentRequest;
import com.capillary.spar.integration.model.LMREnrollmentResult;
import com.capillary.spar.integration.service.SparLMRUserEnrolledService;
import com.capillary.spar.integration.util.SparLMRTenantConfigKeys;
import com.sellerworx.darby.core.context.RequestContext;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.core.document.KeyInfo;
import com.sellerworx.darby.core.processor.DarbyBaseProcessor;
import com.sellerworx.darby.entity.Customer;
import com.sellerworx.darby.entity.UserProfileAttribute;
import com.sellerworx.darby.exception.DarbyException;
import com.sellerworx.darby.util.ErrorCode;
import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.exchange.ExchangeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("SparLMRUserEnrolledProcessor")
@Documented(description = "enrolled the user by calling the spar lmr api and if the user is already enrolled then call  " +
        "the getMemberForLMR api and then setting the userprofileatrributes, userProfileAttrValue as the lmrid" +
        "(cardnumber-coming in the response) in exchange body",
        inBody = @KeyInfo(comment = "contains the customer map, which has customer and address details",
                type = HashMap.class),
        outBody = @KeyInfo(comment = "conatins the userprofileattr list", type = UserProfileAttribute.class)
)
public class SparLMRUserEnrolledProcessor extends DarbyBaseProcessor {

    @Autowired
    private SparLMRUserEnrolledService sparLMRUserEnrolledService;

    @Override
    public void startProcess(Exchange exchange) {
        Map<String, Object> customerMap = ExchangeUtil.getBody(exchange, Map.class);
        Customer customer = (Customer) customerMap.get(ExchangeHeaderKeys.CUSTOMER);
        Map<String, String> configMap = RequestContext.getTenantInfo().getConfigMap();
        String lmrProfileAttributeId = configMap.get(SparLMRTenantConfigKeys.LMR_PROFILE_ATTRIBUTE_ID);
        if(StringUtils.isBlank(customer.getEmail()) ||
                StringUtils.isBlank(customer.getFirstName()) ||
                        StringUtils.isBlank(customer.getMobileNumber()))
        {
            String errorMsg = "value of the field mobilenumber, emailid, name cannot be empty";
            throw new DarbyException(errorMsg, ErrorCode.EMPTY);
        }
        try {
            LMREnrollmentRequest lmrEnrollmentRequest = getLMREnrollmentResult(customer);
            LMREnrollmentResult lmrEnrollmentResult =
                    sparLMRUserEnrolledService.enrollNewUserToLandmark(lmrEnrollmentRequest);
            List<UserProfileAttribute> userProfileAttributes = new ArrayList<>();
            if(lmrEnrollmentResult.isResult())
            {
                userProfileAttributes = addUserProfileAtrribute(lmrProfileAttributeId, lmrEnrollmentResult);
            }
            else
            {
                log.info("already enrolled: {} hence fetching the lmr member by getmemberforlmr api",
                        lmrEnrollmentResult.getMessage().toLowerCase());
                GetMemberForLMRPayload getMemberForLMRPayload = new GetMemberForLMRPayload();
                getMemberForLMRPayload.setMobileNumber(customer.getMobileNumber());
                try {
                    LMREnrollmentResult enrollmentResult =
                            sparLMRUserEnrolledService.getMemberForLMR(getMemberForLMRPayload);
                    if(enrollmentResult.isResult())
                    {
                        userProfileAttributes = addUserProfileAtrribute(lmrProfileAttributeId, enrollmentResult);
                    }
                    else
                    {
                        String errorMsg =  enrollmentResult.getMessage().toLowerCase();
                        log.error(errorMsg);
                        throw new DarbyException(errorMsg, ErrorCode.INVALID);
                    }
                }catch (Exception e)
                {
                    String errorMsg = "an error occured while calling the lmr_enrollment_api "
                            +e.getMessage();
                    throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
                }
            }
            ExchangeUtil.setBody(userProfileAttributes, exchange);
        }
        catch(Exception e)
        {
            String errorMsg = "an error occured while calling the get_member_for_lmr_api "
                    +e.getMessage();
            throw new DarbyException(errorMsg, e, ErrorCode.UNKNOWN);
        }
    }

    private LMREnrollmentRequest getLMREnrollmentResult(Customer customer)
    {
        LMREnrollmentRequest lmrEnrollmentRequest = new LMREnrollmentRequest();
        lmrEnrollmentRequest.setFirstName(customer.getFirstName());
        lmrEnrollmentRequest.setLastName(customer.getLastName());
        lmrEnrollmentRequest.setCity(customer.getCity());
        lmrEnrollmentRequest.setDateOfBirth(customer.getDateOfBirth());
        lmrEnrollmentRequest.setStoreCode(RequestContext.configString(SparLMRTenantConfigKeys.SPAR_LMR_STORE_CODE));
        lmrEnrollmentRequest.setPinCode(RequestContext.configString(SparLMRTenantConfigKeys.SPAR_LMR_PIN_CODE));
        lmrEnrollmentRequest.setMobileNumber(customer.getMobileNumber());
        lmrEnrollmentRequest.setNationality(customer.getNationality());
        lmrEnrollmentRequest.setEmailID(customer.getEmail());
        return  lmrEnrollmentRequest;
    }

    private List<UserProfileAttribute> addUserProfileAtrribute(String lmrProfileAttributeId,
                                                               LMREnrollmentResult lmrEnrollmentResult)
    {
        UserProfileAttribute userProfile = new UserProfileAttribute();
        userProfile.setProfileAttributeId(Long.parseLong(lmrProfileAttributeId));
        userProfile.setProfileAttributeValue(lmrEnrollmentResult.getCardNumber());
        List<UserProfileAttribute> customerProfile = new ArrayList<>();
        customerProfile.add(userProfile);
        return customerProfile;
    }
}
