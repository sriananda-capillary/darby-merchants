package com.capillary.spar.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sellerworx.darby.annotation.NoObfuscation;
import lombok.Data;

@Data
@NoObfuscation
@JsonIgnoreProperties(ignoreUnknown = true)
public class LMREnrollmentRequest {
    private String firstName;
    private String lastName;
    private String country;
    private String mobileNumber;
    private String emailID;
    private String dateOfBirth;
    private String city;
    private String nationality;
    private String storeCode;
    private String pinCode;
}