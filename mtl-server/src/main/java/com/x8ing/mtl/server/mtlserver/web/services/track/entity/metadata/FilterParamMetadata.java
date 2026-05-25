package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "label",
        "group",
        "widget",
        "optional",
        "unit",
        "originFilterRef",
        "relation",
        "optionsSource"
})
public class FilterParamMetadata {

    @Schema(description = "User-facing parameter label.")
    private String label;

    @Schema(description = "Group id from paramGroups.")
    private String group;

    @Schema(description = "Widget used to edit this parameter.", allowableValues = {
            "dateTime",
            "trackPicker",
            "number",
            "text",
            "geoCircle",
            "geoRectangle",
            "geoPolygon"
    })
    private FilterParamWidget widget;

    @Schema(description = "Whether the parameter may be left blank.")
    private Boolean optional;

    @Schema(description = "User-facing value unit.")
    private String unit;

    @Schema(description = "Template filter where this effective parameter metadata was defined.")
    private String originFilterRef;

    @Schema(description = "Relationship between the selected filter and the parameter metadata origin.", allowableValues = {"selected", "inherited"})
    private FilterParamRelation relation;

    @Schema(description = "Metadata source for widgets that need selectable options.")
    private FilterOptionsSourceMetadata optionsSource;
}
