package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterGradientDirection {
    LOW_TO_HIGH("low-to-high");

    private final String value;

    FilterGradientDirection(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FilterGradientDirection fromValue(String value) {
        for (FilterGradientDirection direction : values()) {
            if (direction.value.equals(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unsupported filter gradient direction: " + value);
    }
}
