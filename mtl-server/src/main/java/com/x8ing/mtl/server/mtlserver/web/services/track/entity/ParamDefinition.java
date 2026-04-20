package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParamDefinition {

    private String name;
    private ParamType type;
    private String label;
}
