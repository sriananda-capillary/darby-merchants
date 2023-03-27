package com.sellerworx.modules.apollo.hospital.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApolloAckOrderResponse {

    String responseMessage;
    String status;
    String errors;
    String originalJSONSubmitted;

}
