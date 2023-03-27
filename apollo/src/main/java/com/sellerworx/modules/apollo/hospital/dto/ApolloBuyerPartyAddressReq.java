package com.sellerworx.modules.apollo.hospital.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloBuyerPartyAddressReq {

    final String buyerPartName;
    final String addressStreet;
    final String addressLocation;
    final String addressArea;
    final String city;
    final String state;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApolloBuyerPartyAddressReq(@JsonProperty("buyerPartName") String buyerPartName,
                                      @JsonProperty("addressStreet") String addressStreet,
                                      @JsonProperty("addressLocation") String addressLocation,
                                      @JsonProperty("addressArea") String addressArea,
                                      @JsonProperty("city") String city,
                                      @JsonProperty("state") String state) {
        this.buyerPartName = buyerPartName;
        this.addressStreet = addressStreet;
        this.addressLocation = addressLocation;
        this.addressArea = addressArea;
        this.city = city;
        this.state = state;
    }
}
