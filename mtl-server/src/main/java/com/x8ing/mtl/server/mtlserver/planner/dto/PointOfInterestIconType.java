package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable icon keys stored in a planner map, independent of the rendering library.
 */
public enum PointOfInterestIconType {
    PIN("pin"),
    VIEWPOINT("viewpoint"),
    FOOD("food"),
    WATER("water"),
    ACCOMMODATION("accommodation"),
    CAMPING("camping"),
    PARKING("parking"),
    INFORMATION("information"),
    PHOTO("photo"),
    PEAK("peak");

    private final String value;

    PointOfInterestIconType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PointOfInterestIconType fromValue(String value) {
        for (PointOfInterestIconType type : values()) {
            if (type.value.equals(value)) return type;
        }
        throw new IllegalArgumentException("Unsupported point of interest icon type");
    }
}
