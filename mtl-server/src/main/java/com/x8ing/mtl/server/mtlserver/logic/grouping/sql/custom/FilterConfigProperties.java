package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.custom;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * used if filters are given by application.yml config, as they are very specific...
 * actually to me, as developer and user of mtl, but probably not interesting for others :-)
 */
@Data
@Component
@ConfigurationProperties(prefix = "mtl")
public class FilterConfigProperties {

    private List<FilterConfigEntity> filterConfigs;

}
