package com.x8ing.mtl.server.mtlserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mtl.updates")
public class ReleaseUpdateProperties {
    private boolean enabled = true;
    private String channel = "stable";
    private String feedUrl = "https://mtl-explorer.com/releases/releases.json";
    private Components components = new Components();

    public ManagedComponent component(String id) {
        return switch (id.replace("-", "")) {
            case "brouter" -> components.getBrouter();
            case "locationsearch" -> components.getLocationSearch();
            case "mapserver" -> components.getMapServer();
            default -> null;
        };
    }

    @Data
    public static class Components {
        private ManagedComponent brouter = new ManagedComponent();
        private ManagedComponent locationSearch = new ManagedComponent();
        private ManagedComponent mapServer = new ManagedComponent();
    }

    public enum Management {SELF_MANAGED, PROVIDER_MANAGED, DISABLED, UNKNOWN}

    @Data
    public static class ManagedComponent {
        private Management management = Management.UNKNOWN;
        private String statusUrl;
    }
}
