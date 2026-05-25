package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterOptionsSourceType {
    ORIGIN_FILTER_RESULT("originFilterResult");

    private final String value;

    FilterOptionsSourceType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FilterOptionsSourceType fromValue(String value) {
        for (FilterOptionsSourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported filter options source type: " + value);
    }
}
