package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonPropertyOrder({
        "name",
        "type",
        "label"
})
public class ParamDefinition {

    private String name;
    private ParamType type;
    private String label;
}
