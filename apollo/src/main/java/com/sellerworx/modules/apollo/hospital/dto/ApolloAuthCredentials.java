package com.sellerworx.modules.apollo.hospital.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.StringJoiner;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloAuthCredentials {

    final String username;
    final String password;

    public ApolloAuthCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ApolloAuthCredentials.class.getSimpleName() + "[", "]")
                .add("username='" + username + "'")
                .toString();
    }
}

