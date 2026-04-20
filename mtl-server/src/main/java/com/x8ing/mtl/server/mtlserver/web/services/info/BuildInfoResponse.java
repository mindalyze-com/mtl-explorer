package com.x8ing.mtl.server.mtlserver.web.services.info;

public record BuildInfoResponse(String version, String buildTime, String defaultLocale, String defaultGpsTrackFilterName) {
}
