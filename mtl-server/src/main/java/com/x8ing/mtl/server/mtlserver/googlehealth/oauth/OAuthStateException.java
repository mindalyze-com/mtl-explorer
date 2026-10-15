package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

public class OAuthStateException extends OAuthException {

    public OAuthStateException() {
        super("Google authorization state is invalid or expired", false);
    }
}
