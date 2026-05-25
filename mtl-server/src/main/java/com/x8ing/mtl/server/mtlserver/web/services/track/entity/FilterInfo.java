package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterEffectiveUiMetadata;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
@JsonPropertyOrder({
        "filterConfig",
        "resolvedSQL",
        "paramsInSQL",
        "paramDefinitions",
        "effectiveUiMetadata"
})
public class FilterInfo {

    private FilterConfigEntity filterConfig;

    private String resolvedSQL;

    private Set<String> paramsInSQL;

    private List<ParamDefinition> paramDefinitions;

    private FilterEffectiveUiMetadata effectiveUiMetadata;
}
