package com.easybpm.auth.sso;

/**
 * OIDC discovery 结果（只保留本项目用到的字段）。
 */
public record OidcMetadata(String issuer, String authorizationEndpoint, String tokenEndpoint,
                           String userinfoEndpoint, String jwksUri, String endSessionEndpoint) {
}
