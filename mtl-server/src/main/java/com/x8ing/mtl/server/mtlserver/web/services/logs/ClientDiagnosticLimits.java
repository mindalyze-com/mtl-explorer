package com.x8ing.mtl.server.mtlserver.web.services.logs;

public final class ClientDiagnosticLimits {
    public static final int REQUEST_BYTES = 128 * 1024;
    public static final int MESSAGE_CHARS = 4096;
    public static final int DETAIL_CHARS = 16384;
    public static final int STACK_CHARS = 65536;
    public static final int CONTEXT_BYTES = 8192;
    public static final int TOPIC_CHARS = 255;
    public static final int BUILD_CHARS = 128;
    public static final int LOGIN_REF_CHARS = 32;

    private ClientDiagnosticLimits() {
    }
}
