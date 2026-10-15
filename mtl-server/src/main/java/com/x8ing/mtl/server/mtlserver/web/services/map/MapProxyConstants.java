package com.x8ing.mtl.server.mtlserver.web.services.map;

public final class MapProxyConstants {

    public static final String UPSTREAM_AUTH_VERSION_PARAM = "mtl-version";
    public static final String UPSTREAM_BUILD_PARAM = "mtl-build";
    public static final String UPSTREAM_AUTH_SERVER_ID_PARAM = "mtl-server-id";
    public static final String UPSTREAM_INSTANCE_TYPE_PARAM = "mtl-instance-type";
    public static final String UPSTREAM_CLIENT_LOGIN_REF_PARAM = "mtl-client-login-ref";

    public static final String INSTANCE_TYPE_DEMO = "demo";
    public static final String INSTANCE_TYPE_STANDARD = "standard";

    public static final String CACHE_SOURCE_PARAM = "mtl-map-source";
    public static final String CACHE_ARCHIVE_PARAM = "mtl-map-archive";

    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_PUBLIC = "public";

    public static final String SCOPE_PROD = "prod";

    public static final String TILE_MODE_LOCAL = "local";
    public static final String TILE_MODE_REMOTE = "remote";

    public static final String UNKNOWN_ARCHIVE_ID = "unknown";

    private MapProxyConstants() {
    }
}
