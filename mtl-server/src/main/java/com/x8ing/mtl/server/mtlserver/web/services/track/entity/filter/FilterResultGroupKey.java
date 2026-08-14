package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "value"
})
public class FilterResultGroupKey {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Exact raw grp value. Null represents an ungrouped SQL result."
    )
    private String value;

    public static FilterResultGroupKey grouped(String value) {
        return new FilterResultGroupKey(value);
    }

    public static FilterResultGroupKey ungrouped() {
        return new FilterResultGroupKey(null);
    }

    public static FilterResultGroupKey fromRawGroup(String value) {
        return new FilterResultGroupKey(value);
    }
}
