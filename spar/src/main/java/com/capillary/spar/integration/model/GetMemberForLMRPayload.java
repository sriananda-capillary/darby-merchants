package com.capillary.spar.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sellerworx.darby.annotation.NoObfuscation;
import lombok.Data;

@Data
@NoObfuscation
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMemberForLMRPayload {
    private String mobileNumber;
}
