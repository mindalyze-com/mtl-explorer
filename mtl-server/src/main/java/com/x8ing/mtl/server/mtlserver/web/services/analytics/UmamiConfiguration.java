package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import com.x8ing.mtl.server.mtlserver.config.UmamiProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

/**
 * Shares the optional demo analytics configuration between the public API and CSP.
 * Validates settings locally only; server startup never contacts or depends on Umami.
 */
@Slf4j
@Getter
@Component
public class UmamiConfiguration {
    private final UmamiConfigResponse clientConfig;
    private final String scriptSource;

    public UmamiConfiguration(UmamiProperties umami) {
        UmamiConfigResponse config = UmamiConfigResponse.DISABLED;
        String source = "";
        if (!umami.isEnabled()) {
            log.info("Umami analytics disabled");
        } else {
            try {
                String scriptUrl = umami.getScriptUrl() == null ? "" : umami.getScriptUrl().trim();
                String websiteId = umami.getWebsiteId() == null ? "" : umami.getWebsiteId().trim();
                URI uri = new URI(scriptUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    throw new IllegalArgumentException("Expected an absolute HTTPS script URL without credentials, query or fragment");
                }
                if (!UUID.fromString(websiteId).toString().equalsIgnoreCase(websiteId)) {
                    throw new IllegalArgumentException("Expected a UUID website ID");
                }
                source = new URI("https", null, uri.getHost(), uri.getPort(), null, null, null).toASCIIString();
                config = new UmamiConfigResponse(true, uri.toASCIIString(), websiteId);
                log.info("Umami analytics enabled for MTL Explorer: scriptOrigin={}", source);
            } catch (IllegalArgumentException | URISyntaxException exception) {
                // Do not log the supplied URL; an invalid value could contain credentials.
                log.warn("Umami analytics disabled: invalid script URL or website ID ({})", exception.getClass().getSimpleName());
            }
        }
        clientConfig = config;
        scriptSource = source;
    }
}
