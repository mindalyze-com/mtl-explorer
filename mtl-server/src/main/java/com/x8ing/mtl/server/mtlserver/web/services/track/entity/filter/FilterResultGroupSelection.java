package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonPropertyOrder({
        "includedGroups"
})
public class FilterResultGroupSelection {

    private List<FilterResultGroupKey> includedGroups = new ArrayList<>();
}
