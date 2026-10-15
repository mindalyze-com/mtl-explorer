package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

public class GoogleHealthAccountMismatchException extends RuntimeException {

    public GoogleHealthAccountMismatchException() {
        super("The selected Google account is different from the existing Google Health mirror");
    }
}
