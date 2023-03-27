package com.sellerworx.modules.apollo.hospital.dto;

import lombok.Getter;

@Getter
public class ApolloAuthenticateResult {

    final String token;

    public ApolloAuthenticateResult(String token) {this.token = token;}
}
