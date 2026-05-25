package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;

@JsonPropertyOrder({
        "filterDomain",
        "filterName"
})
public record FilterTemplateReference(FilterConfigEntity.FILTER_DOMAIN filterDomain, String filterName) {

    public String asTemplatePath() {
        return "/" + filterDomain.name() + "/" + filterName;
    }
}
