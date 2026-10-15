package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

public class OAuthException extends RuntimeException {

    private final boolean reconnectRequired;
    private final String providerErrorCode;

    public OAuthException(String message, boolean reconnectRequired) {
        super(message);
        this.reconnectRequired = reconnectRequired;
        this.providerErrorCode = null;
    }

    public OAuthException(String message, boolean reconnectRequired, String providerErrorCode) {
        super(message);
        this.reconnectRequired = reconnectRequired;
        this.providerErrorCode = providerErrorCode;
    }

    public OAuthException(String message, boolean reconnectRequired, Throwable cause) {
        super(message, cause);
        this.reconnectRequired = reconnectRequired;
        this.providerErrorCode = null;
    }

    public boolean isReconnectRequired() {
        return reconnectRequired;
    }

    public String getProviderErrorCode() {
        return providerErrorCode;
    }
}
