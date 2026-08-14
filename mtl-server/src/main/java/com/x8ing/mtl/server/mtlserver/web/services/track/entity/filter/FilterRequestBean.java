package com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "filterName",
        "params"
})
public class FilterRequestBean {

    private String filterName;

    private FilterParamsRequest params;

}
