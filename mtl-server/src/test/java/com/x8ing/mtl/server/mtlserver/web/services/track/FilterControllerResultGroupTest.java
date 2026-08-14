package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterExecutionService;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterParamResolver;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.GpsTrackSQLFilter;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata.FilterMetadataResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupKey;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterControllerResultGroupTest {

    @Test
    void resolveKeepsRawCatalogButHydratesOnlyEffectiveEntries() {
        GpsTrackSQLFilter sqlFilter = mock(GpsTrackSQLFilter.class);
        FilterParamResolver paramResolver = mock(FilterParamResolver.class);
        FilterExecutionService executionService = mock(FilterExecutionService.class);
        FilterMetadataResolver metadataResolver = mock(FilterMetadataResolver.class);
        FilterConfigRepository filterRepository = mock(FilterConfigRepository.class);
        GpsTrackRepository trackRepository = mock(GpsTrackRepository.class);
        FilterConfigEntity filter = new FilterConfigEntity();
        filter.setId(42L);
        FilterParamsRequest request = new FilterParamsRequest();

        QueryResult refined = queryResult(entry(2L, "2025"));
        refined.setGroupingAvailable(true);
        refined.setPreGroupSelectionCount(3);
        refined.setAvailableGroups(List.of(
                new FilterResultGroupSummary(FilterResultGroupKey.grouped("2024"), 2L),
                new FilterResultGroupSummary(FilterResultGroupKey.grouped("2025"), 1L)));
        when(filterRepository.findById(42L)).thenReturn(Optional.of(filter));
        when(sqlFilter.getTemplateToSQL(filter)).thenReturn("select id, grp from gps_track");
        when(paramResolver.analyze(any())).thenReturn(List.of());
        when(executionService.execute(filter, request)).thenReturn(refined);
        when(executionService.standardFilterCount(1L)).thenReturn(3L);
        when(trackRepository.findVersionMapByIds(anyList())).thenReturn(Map.of(2L, 9L));

        QueryResult result = new FilterController(
                sqlFilter,
                paramResolver,
                executionService,
                metadataResolver,
                filterRepository,
                trackRepository
        ).getResolveById(42L, false, false, request);

        assertThat(result.getAvailableGroups())
                .extracting(summary -> summary.getKey().getValue(), FilterResultGroupSummary::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2024", 2L),
                        org.assertj.core.groups.Tuple.tuple("2025", 1L));
        assertThat(result.getResultEntries()).extracting(QueryResult.QueryResultEntry::getId).containsExactly(2L);
        assertThat(result.getTrackVersions()).containsExactlyEntriesOf(Map.of(2L, 9L));
        assertThat(result.getFilterGroups()).containsExactlyEntriesOf(Map.of(2L, "2025"));
        assertThat(result.getStandardFilterCount()).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> versionIds = ArgumentCaptor.forClass(List.class);
        verify(trackRepository).findVersionMapByIds(versionIds.capture());
        assertThat(versionIds.getValue()).containsExactly(2L);
        verify(executionService).execute(eq(filter), eq(request));
    }

    private static QueryResult queryResult(QueryResult.QueryResultEntry... entries) {
        QueryResult result = new QueryResult();
        result.setResultEntries(List.of(entries));
        return result;
    }

    private static QueryResult.QueryResultEntry entry(Long id, String group) {
        QueryResult.QueryResultEntry entry = new QueryResult.QueryResultEntry();
        entry.setId(id);
        entry.setGroup(group);
        return entry;
    }
}
