package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Executes a configured SQL filter and applies the generic result-group scope.
 * Result-group values are output data and are intentionally never bound into
 * the dynamic SQL parameter map.
 */
@Slf4j
@Service
public class FilterExecutionService {

    private final GpsTrackSQLFilter gpsTrackSQLFilter;
    private final FilterParamResolver filterParamResolver;
    private final List<TrackScopeProvider> trackScopeProviders;

    @Autowired
    public FilterExecutionService(GpsTrackSQLFilter gpsTrackSQLFilter,
                                  FilterParamResolver filterParamResolver,
                                  List<TrackScopeProvider> trackScopeProviders) {
        this.gpsTrackSQLFilter = gpsTrackSQLFilter;
        this.filterParamResolver = filterParamResolver;
        this.trackScopeProviders = List.copyOf(trackScopeProviders);
    }

    FilterExecutionService(GpsTrackSQLFilter gpsTrackSQLFilter, FilterParamResolver filterParamResolver) {
        this(gpsTrackSQLFilter, filterParamResolver, List.of());
    }

    public QueryResult execute(FilterConfigEntity filter, FilterParamsRequest request) {
        Map<String, String> sqlParams = filterParamResolver.expand(request);
        return refine(gpsTrackSQLFilter.getGpsTrackIdsFor(filter, sqlParams), selectionFrom(request), scopeFrom(request));
    }

    public QueryResult executeOptionalFilterName(String optionalFilterName, FilterParamsRequest request) {
        Map<String, String> sqlParams = filterParamResolver.expand(request);
        return refine(
                gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(optionalFilterName, sqlParams),
                selectionFrom(request),
                scopeFrom(request));
    }

    public QueryResult executeStandardFilter() {
        return executeOptionalFilterName(null, null);
    }

    public long standardFilterCount(long fallback) {
        QueryResult result = executeStandardFilter();
        return result != null && result.getResultEntries() != null
                ? result.getResultEntries().size()
                : fallback;
    }

    public long standardFilterCount(long fallback, FilterParamsRequest request) {
        if (scopeFrom(request) == TrackViewScope.ALL) {
            return standardFilterCount(fallback);
        }
        QueryResult standard = executeStandardFilter();
        if (standard == null || standard.getResultEntries() == null) {
            return fallback;
        }
        return refine(standard, null, scopeFrom(request)).getResultEntries().size();
    }

    QueryResult refine(QueryResult queryResult,
                       FilterResultGroupSelection selection,
                       TrackViewScope trackViewScope) {
        long startedNanos = System.nanoTime();
        QueryResult result = queryResult == null ? new QueryResult() : queryResult;
        List<QueryResult.QueryResultEntry> rawEntries = result.getResultEntries() == null
                ? List.of()
                : result.getResultEntries();
        if (trackViewScope != null && trackViewScope != TrackViewScope.ALL) {
            TrackScopeProvider provider = trackScopeProviders.stream()
                    .filter(candidate -> candidate.scope() == trackViewScope)
                    .findFirst()
                    .orElseThrow(() -> badRequest("Requested track scope is unavailable."));
            Set<Long> scopedTrackIds = provider.trackIds();
            rawEntries = rawEntries.stream()
                    .filter(entry -> scopedTrackIds.contains(entry.getId()))
                    .toList();
            result.setResultEntries(new ArrayList<>(rawEntries));
        }

        result.setPreGroupSelectionCount(rawEntries.size());
        result.setAvailableGroups(summarizeGroups(rawEntries, result.isGroupingAvailable()));

        if (selection == null) {
            logRefinement(result, rawEntries.size(), startedNanos);
            return result;
        }
        if (!result.isGroupingAvailable()) {
            throw badRequest("Result-group selection requires a filter that returns a grp column.");
        }

        Set<FilterResultGroupKey> includedGroups = validateAndNormalize(selection);
        List<QueryResult.QueryResultEntry> effectiveEntries = rawEntries.stream()
                .filter(entry -> includedGroups.contains(FilterResultGroupKey.fromRawGroup(entry.getGroup())))
                .toList();
        result.setResultEntries(new ArrayList<>(effectiveEntries));
        logRefinement(result, rawEntries.size(), startedNanos);
        return result;
    }

    QueryResult refine(QueryResult queryResult, FilterResultGroupSelection selection) {
        return refine(queryResult, selection, TrackViewScope.ALL);
    }

    private static FilterResultGroupSelection selectionFrom(FilterParamsRequest request) {
        return request == null ? null : request.getResultGroupSelection();
    }

    private static TrackViewScope scopeFrom(FilterParamsRequest request) {
        return request == null || request.getTrackViewScope() == null
                ? TrackViewScope.ALL
                : request.getTrackViewScope();
    }

    private static List<FilterResultGroupSummary> summarizeGroups(
            List<QueryResult.QueryResultEntry> entries,
            boolean groupingAvailable
    ) {
        if (!groupingAvailable) {
            return List.of();
        }
        Map<FilterResultGroupKey, Long> counts = new LinkedHashMap<>();
        for (QueryResult.QueryResultEntry entry : entries) {
            FilterResultGroupKey key = FilterResultGroupKey.fromRawGroup(entry.getGroup());
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new FilterResultGroupSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Set<FilterResultGroupKey> validateAndNormalize(FilterResultGroupSelection selection) {
        List<FilterResultGroupKey> requested = selection.getIncludedGroups() == null
                ? Collections.emptyList()
                : selection.getIncludedGroups();
        Set<FilterResultGroupKey> result = new LinkedHashSet<>();
        for (FilterResultGroupKey key : requested) {
            if (key == null) {
                throw badRequest("Result-group selections cannot contain null keys.");
            }
            result.add(new FilterResultGroupKey(key.getValue()));
        }
        return result;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static void logRefinement(QueryResult result, int rawRowCount, long startedNanos) {
        long durationMicros = (System.nanoTime() - startedNanos) / 1_000L;
        int effectiveRowCount = result.getResultEntries() == null ? 0 : result.getResultEntries().size();
        int groupCount = result.getAvailableGroups() == null ? 0 : result.getAvailableGroups().size();
        log.debug(
                "Filter result refinement rawRows={} availableGroups={} effectiveRows={} durationMicros={}",
                rawRowCount,
                groupCount,
                effectiveRowCount,
                durationMicros);
    }
}
