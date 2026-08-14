package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "key",
        "count"
})
public class FilterResultGroupSummary {

    private FilterResultGroupKey key;
    private long count;
}
