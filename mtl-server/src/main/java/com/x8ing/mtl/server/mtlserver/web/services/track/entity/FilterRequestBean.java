package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.Map;

@Data
@JsonPropertyOrder({
        "filterName",
        "params"
})
public class FilterRequestBean {

    private String filterName;

    private Map<String, String> params;

}
