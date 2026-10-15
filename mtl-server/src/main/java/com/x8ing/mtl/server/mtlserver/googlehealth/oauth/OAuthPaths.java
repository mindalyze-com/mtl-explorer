package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

public final class OAuthPaths {

    public static final String CALLBACK_ENDPOINT = "/oauth/callback";
    public static final String CALLBACK_PATH = "/api/google-health" + CALLBACK_ENDPOINT;
    public static final String MANUAL_REDIRECT_URI = "http://127.0.0.1:1/mtl-explorer-oauth";

    private OAuthPaths() {
    }
}
