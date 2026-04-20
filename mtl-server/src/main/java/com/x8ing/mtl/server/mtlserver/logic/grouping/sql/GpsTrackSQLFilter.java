package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.readonly.DynamicSqlService;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template.TemplateProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GpsTrackSQLFilter {

    private final DynamicSqlService dynamicSqlService;
    private final TemplateProcessingService templateProcessingService;
    private final FilterConfigRepository filterConfigRepository;


    public GpsTrackSQLFilter(DynamicSqlService dynamicSqlService, TemplateProcessingService templateProcessingService, FilterConfigRepository filterConfigRepository) {
        this.dynamicSqlService = dynamicSqlService;
        this.templateProcessingService = templateProcessingService;
        this.filterConfigRepository = filterConfigRepository;
    }

    public QueryResult getGpsTrackIdsFor(FilterConfigEntity filterConfigEntity) {
        return getGpsTrackIdsFor(filterConfigEntity, new HashMap<>());
    }

    public QueryResult getGpsTrackIdsForOptionalFilterName(String optionalFilterName, Map<String, String> params) {

        FilterConfigEntity filterConfigEntity = null;
        if (StringUtils.isBlank(optionalFilterName)) {
            filterConfigEntity = filterConfigRepository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME);
        } else {
            filterConfigEntity = filterConfigRepository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, optionalFilterName);

        }

        return getGpsTrackIdsFor(filterConfigEntity, params);
    }

    public QueryResult getGpsTrackIdsFor(FilterConfigEntity filterConfigEntity, Map<String, String> params) {

        if (filterConfigEntity == null) {
            log.warn("Requested filter configuration is missing. Falling back to default filter: {}", FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME);
            filterConfigEntity = filterConfigRepository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME);
            if (filterConfigEntity == null) {
                log.error("Default filter configuration ({}) is also missing!", FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME);
                return new QueryResult();
            }
        }

        String sql = getTemplateToSQL(filterConfigEntity);
        return getGpsTrackIdsForSQL(sql, params);
    }

    public String getTemplateToSQL(FilterConfigEntity filterConfigEntity) {
        String templatePath = "/" + filterConfigEntity.getFilterDomain().name() + "/" + filterConfigEntity.getFilterName();
        log.debug("About to resolve template: {}", templatePath);
        String resolvedTemplate = templateProcessingService.processTemplate(templatePath);

        // HACK / TODO FIX remove hacky unescape of HTML
        //  can't convince Thymeleaf to NOT treat is as HTML
        // while it works for templates which are directly resolved, it will break, if the template itself loads an included fragment. the latter then has HTML mode it seems...
        return StringEscapeUtils.unescapeHtml4(resolvedTemplate);
    }

    public QueryResult getGpsTrackIdsForSQL(String sql, Map<String, String> params) {
        if (StringUtils.isBlank(sql)) {
            return new QueryResult();
        }

        return dynamicSqlService.executeDynamicSqlReadOnly(sql, params, true);
    }

}
