package com.x8ing.mtl.server.mtlserver.web.services.map;

public enum MapUpstreamSource {
    LOCAL(MapProxyConstants.SOURCE_LOCAL),
    PUBLIC(MapProxyConstants.SOURCE_PUBLIC);

    private final String cacheValue;

    MapUpstreamSource(String cacheValue) {
        this.cacheValue = cacheValue;
    }

    public String cacheValue() {
        return cacheValue;
    }

    public static MapUpstreamSource fromCacheValue(String value) {
        if (MapProxyConstants.SOURCE_LOCAL.equalsIgnoreCase(value)) {
            return LOCAL;
        }
        if (MapProxyConstants.SOURCE_PUBLIC.equalsIgnoreCase(value)) {
            return PUBLIC;
        }
        throw new IllegalArgumentException("Unsupported map source: " + value);
    }
}
