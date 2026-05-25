package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterParamRelation {
    SELECTED("selected"),
    INHERITED("inherited");

    private final String value;

    FilterParamRelation(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FilterParamRelation fromValue(String value) {
        for (FilterParamRelation relation : values()) {
            if (relation.value.equals(value)) {
                return relation;
            }
        }
        throw new IllegalArgumentException("Unsupported filter parameter relation: " + value);
    }
}
