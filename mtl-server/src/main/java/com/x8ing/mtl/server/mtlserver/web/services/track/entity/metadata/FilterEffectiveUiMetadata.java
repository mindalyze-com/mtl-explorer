package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "metadataVersion",
        "paramGroups",
        "params",
        "result"
})
public class FilterEffectiveUiMetadata {

    @Builder.Default
    @Schema(description = "UI metadata schema version. Only version 2 is supported.")
    private Integer metadataVersion = 2;

    @Builder.Default
    @Schema(description = "Parameter groups keyed by group id.")
    private Map<String, FilterParamGroupMetadata> paramGroups = new LinkedHashMap<>();

    @Builder.Default
    @Schema(description = "Parameter metadata keyed by SQL parameter name.")
    private Map<String, FilterParamMetadata> params = new LinkedHashMap<>();

    @Builder.Default
    @Schema(description = "Result rendering metadata.")
    private FilterResultMetadata result = new FilterResultMetadata();
}
