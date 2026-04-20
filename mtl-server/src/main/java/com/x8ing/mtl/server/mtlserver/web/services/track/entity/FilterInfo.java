package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class FilterInfo {

    private FilterConfigEntity filterConfig;

    private String resolvedSQL;

    private Set<String> paramsInSQL;

    private List<ParamDefinition> paramDefinitions;
}
