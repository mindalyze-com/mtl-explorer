package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterParamGroupDefaultOpen {
    ALWAYS("always"),
    NEVER("never"),
    WHEN_ACTIVE("whenActive");

    private final String value;

    FilterParamGroupDefaultOpen(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FilterParamGroupDefaultOpen fromJson(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue ? ALWAYS : NEVER;
        }

        if (rawValue instanceof String stringValue) {
            for (FilterParamGroupDefaultOpen value : values()) {
                if (value.value.equals(stringValue)) {
                    return value;
                }
            }
            if ("true".equalsIgnoreCase(stringValue)) {
                return ALWAYS;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return NEVER;
            }
        }

        throw new IllegalArgumentException("Unsupported filter param group defaultOpen value: " + rawValue);
    }
}
