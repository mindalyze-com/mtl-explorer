package com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "resolvedFilterRef"
})
public class FilterOptionsSourceMetadata {

    @Schema(description = "Option source kind.", allowableValues = {"originFilterResult"})
    private FilterOptionsSourceType type;

    @Schema(description = "Resolved filter template ref used to load options.")
    private String resolvedFilterRef;
}
