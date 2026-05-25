package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterParamWidget {
    DATE_TIME("dateTime"),
    TRACK_PICKER("trackPicker"),
    NUMBER("number"),
    TEXT("text"),
    GEO_CIRCLE("geoCircle"),
    GEO_RECTANGLE("geoRectangle"),
    GEO_POLYGON("geoPolygon");

    private final String value;

    FilterParamWidget(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FilterParamWidget fromValue(String value) {
        for (FilterParamWidget widget : values()) {
            if (widget.value.equals(value)) {
                return widget;
            }
        }
        throw new IllegalArgumentException("Unsupported filter parameter widget: " + value);
    }
}
