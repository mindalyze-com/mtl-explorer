package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.readonly.DynamicSqlService;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterParamResolver;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterExecutionService;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.GpsTrackSQLFilter;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata.FilterMetadataResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterInfo;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.ParamDefinition;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterEffectiveUiMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/filter")
@JsonPropertyOrder({
        "gpsTrackSQLFilter",
        "filterParamResolver",
        "filterExecutionService",
        "filterMetadataResolver",
        "filterConfigRepository",
        "gpsTrackRepository"
})
public class FilterController {

    private final GpsTrackSQLFilter gpsTrackSQLFilter;
    private final FilterParamResolver filterParamResolver;
    private final FilterExecutionService filterExecutionService;
    private final FilterMetadataResolver filterMetadataResolver;

    private final FilterConfigRepository filterConfigRepository;
    private final GpsTrackRepository gpsTrackRepository;

    public FilterController(GpsTrackSQLFilter gpsTrackSQLFilter, FilterParamResolver filterParamResolver, FilterExecutionService filterExecutionService, FilterMetadataResolver filterMetadataResolver, FilterConfigRepository filterConfigRepository, GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackSQLFilter = gpsTrackSQLFilter;
        this.filterParamResolver = filterParamResolver;
        this.filterExecutionService = filterExecutionService;
        this.filterMetadataResolver = filterMetadataResolver;
        this.filterConfigRepository = filterConfigRepository;
        this.gpsTrackRepository = gpsTrackRepository;
    }

    @RequestMapping("/get")
    public List<FilterInfo> getFilters() {
        List<FilterConfigEntity> all = filterConfigRepository.findAll();
        return all.stream().map(f -> getFilterInfo(f.getId(), null, null)).toList();
    }


    @RequestMapping("/info")
    public FilterInfo getFilterInfo(@RequestParam(required = false) Long filterId, @RequestParam(required = false) String filterName, @RequestParam(required = false) String filterDomain) {
        if (StringUtils.isBlank(filterName) && StringUtils.isBlank(filterDomain) && filterId == null) {
            String msg = "Either a single param ('filterName') or both ('filterName' and 'filterDomain') are required as request parameters.";
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        // load it on the data given
        FilterConfigEntity filterConfig;
        if (filterId != null) {
            filterConfig = filterConfigRepository.findById(filterId).orElseThrow();
        } else {
            filterConfig = filterConfigRepository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.valueOf(filterDomain), filterName);
        }

        String sql = gpsTrackSQLFilter.getTemplateToSQL(filterConfig);
        Set<String> paramsInSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        List<ParamDefinition> paramDefinitions = filterParamResolver.analyze(paramsInSQL);
        Set<String> effectiveParamNames = paramDefinitions.stream()
                .map(ParamDefinition::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        FilterEffectiveUiMetadata effectiveUiMetadata = filterMetadataResolver.resolve(filterConfig, effectiveParamNames);

        return FilterInfo.builder()
                .filterConfig(filterConfig)
                .resolvedSQL(sql)
                .paramsInSQL(paramsInSQL)
                .paramDefinitions(paramDefinitions)
                .effectiveUiMetadata(effectiveUiMetadata)
                .build();
    }

    @RequestMapping("/resolve/{filterConfigId}")
    public QueryResult getResolveById(
            @PathVariable Long filterConfigId,
            @RequestParam(required = false, defaultValue = "false") boolean includeGPSTrack,
            @RequestParam(required = false, defaultValue = "false") boolean includeGPSTrackFile,
            @RequestBody(required = false) FilterParamsRequest params
    ) {
        FilterConfigEntity filter = getFilterById(filterConfigId).getFilterConfig();
        QueryResult queryResult = filterExecutionService.execute(filter, params);

        // Populate VersionAware fields for map cache invalidation
        // (avoids a second get-simplified?mode=ids call from the client)
        if (queryResult != null && queryResult.getResultEntries() != null) {
            List<Long> ids = queryResult.asIdList();
            if (!ids.isEmpty()) {
                queryResult.setTrackVersions(gpsTrackRepository.findVersionMapByIds(ids));
            }

            // Build filterGroups from result entries
            Map<Long, String> groups = new HashMap<>();
            for (QueryResult.QueryResultEntry entry : queryResult.getResultEntries()) {
                if (entry.getGroup() != null) {
                    groups.put(entry.getId(), entry.getGroup());
                }
            }
            queryResult.setFilterGroups(groups);

            queryResult.setStandardFilterCount(
                    filterExecutionService.standardFilterCount(queryResult.getResultEntries().size()));
        }

        // optional: if asked to include gps tracks do so
        if (includeGPSTrack && queryResult != null && queryResult.getResultEntries() != null) {
            Map<Long, GpsTrack> tracksById = gpsTrackRepository.findAll().stream().collect(Collectors.toMap(GpsTrack::getId, gpsTrack -> gpsTrack));

            if (CollectionUtils.isNotEmpty(queryResult.getResultEntries())) {
                queryResult.getResultEntries().forEach(entry -> {
                    GpsTrack track = tracksById.get(entry.getId());
                    entry.setGpsTrack(track);
                    if (!includeGPSTrackFile) {
                        track.setIndexedFile(null);
                    }
                });
            }
        }
        return queryResult;
    }

    @RequestMapping("/resolve-by")
    public QueryResult getResolveBy(
            @RequestParam String filterDomain,
            @RequestParam String filterName,
            @RequestParam(required = false, defaultValue = "false") boolean includeGPSTrack,
            @RequestParam(required = false, defaultValue = "false") boolean includeGPSTrackFile,
            @RequestBody(required = false) FilterParamsRequest params
    ) {
        FilterConfigEntity filterConfigEntity = filterConfigRepository.findByFilterDomainAndFilterName(
                FilterConfigEntity.FILTER_DOMAIN.valueOf(filterDomain),
                filterName);

        if (filterConfigEntity == null) {
            throw new RuntimeException("Filter config not found");
        }

        return getResolveById(filterConfigEntity.getId(), includeGPSTrack, includeGPSTrackFile, params);
    }

    private FilterInfo getFilterById(Long id) {
        return getFilterInfo(id, null, null);
    }
}
