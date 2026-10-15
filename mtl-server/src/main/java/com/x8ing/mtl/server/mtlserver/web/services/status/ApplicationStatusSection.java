package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

public enum ApplicationStatusSection {
    FRESHNESS("freshness"),
    PROCESSING("processing"),
    SERVICES("services"),
    GOOGLE_HEALTH("google-health");

    private final String apiValue;

    ApplicationStatusSection(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    public static ApplicationStatusSection fromApiValue(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(section -> section.apiValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown status section: " + value));
    }
}
