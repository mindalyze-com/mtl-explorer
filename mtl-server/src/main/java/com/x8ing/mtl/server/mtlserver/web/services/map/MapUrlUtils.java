package com.x8ing.mtl.server.mtlserver.web.services.map;

final class MapUrlUtils {

    private MapUrlUtils() {
    }

    static String trimTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailing().replaceAll("/+$", "");
    }
}
