package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupKey;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupSelection;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterExecutionServiceTest {

    @Test
    void discoversGroupsAndKeepsAllRowsWithoutSelection() {
        FilterExecutionService service = service();
        QueryResult result = groupedResult(
                entry(1L, "2024"),
                entry(2L, "2023"),
                entry(3L, "2024"));

        QueryResult refined = service.refine(result, null);

        assertThat(refined.getResultEntries()).extracting(QueryResult.QueryResultEntry::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(refined.getPreGroupSelectionCount()).isEqualTo(3L);
        assertThat(refined.getAvailableGroups()).satisfiesExactly(
                group -> {
                    assertThat(group.getKey()).isEqualTo(FilterResultGroupKey.grouped("2024"));
                    assertThat(group.getCount()).isEqualTo(2L);
                },
                group -> {
                    assertThat(group.getKey()).isEqualTo(FilterResultGroupKey.grouped("2023"));
                    assertThat(group.getCount()).isEqualTo(1L);
                });
    }

    @Test
    void appliesExactMultipleGroupSelectionAndPreservesSqlOrder() {
        FilterExecutionService service = service();
        QueryResult result = groupedResult(
                entry(1L, "WALKING"),
                entry(2L, "CYCLING"),
                entry(3L, "HIKING"),
                entry(4L, "WALKING"));
        FilterResultGroupSelection selection = selection(
                FilterResultGroupKey.grouped("HIKING"),
                FilterResultGroupKey.grouped("WALKING"),
                FilterResultGroupKey.grouped("WALKING"));

        QueryResult refined = service.refine(result, selection);

        assertThat(refined.getResultEntries()).extracting(QueryResult.QueryResultEntry::getId)
                .containsExactly(1L, 3L, 4L);
        assertThat(refined.getAvailableGroups()).hasSize(3);
        assertThat(refined.getPreGroupSelectionCount()).isEqualTo(4L);
    }

    @Test
    void emptyOrUnavailableExactSelectionReturnsNoRowsButKeepsCatalog() {
        FilterExecutionService service = service();
        QueryResult emptySelectionResult = groupedResult(entry(1L, "A"), entry(2L, "B"));
        QueryResult unavailableSelectionResult = groupedResult(entry(1L, "A"), entry(2L, "B"));

        QueryResult empty = service.refine(emptySelectionResult, selection());
        QueryResult unavailable = service.refine(
                unavailableSelectionResult,
                selection(FilterResultGroupKey.grouped("MISSING")));

        assertThat(empty.getResultEntries()).isEmpty();
        assertThat(unavailable.getResultEntries()).isEmpty();
        assertThat(empty.getAvailableGroups()).hasSize(2);
        assertThat(unavailable.getAvailableGroups()).hasSize(2);
    }

    @Test
    void supportsUngroupedRowsWithoutMagicValues() {
        FilterExecutionService service = service();
        QueryResult result = groupedResult(entry(1L, null), entry(2L, ""), entry(3L, null));

        QueryResult refined = service.refine(result, selection(FilterResultGroupKey.ungrouped()));

        assertThat(refined.getResultEntries()).extracting(QueryResult.QueryResultEntry::getId)
                .containsExactly(1L, 3L);
        assertThat(refined.getAvailableGroups()).satisfiesExactly(
                group -> {
                    assertThat(group.getKey()).isEqualTo(FilterResultGroupKey.ungrouped());
                    assertThat(group.getCount()).isEqualTo(2L);
                },
                group -> assertThat(group.getKey()).isEqualTo(FilterResultGroupKey.grouped("")));
    }

    @Test
    void rejectsSelectionForResultWithoutGroupColumn() {
        FilterExecutionService service = service();
        QueryResult result = new QueryResult();
        result.setResultEntries(List.of(entry(1L, null)));

        assertThatThrownBy(() -> service.refine(result, selection(FilterResultGroupKey.ungrouped())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requires a filter that returns a grp column");
    }

    @Test
    void rejectsNullGroupKeyObjects() {
        FilterExecutionService service = service();

        assertThatThrownBy(() -> service.refine(
                groupedResult(entry(1L, "A")),
                selection((FilterResultGroupKey) null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot contain null keys");
    }

    @Test
    void executionExpandsOnlySqlParamsAndRunsDynamicFilterOnce() {
        GpsTrackSQLFilter sqlFilter = mock(GpsTrackSQLFilter.class);
        FilterParamResolver paramResolver = mock(FilterParamResolver.class);
        FilterExecutionService service = new FilterExecutionService(sqlFilter, paramResolver);
        FilterConfigEntity filter = new FilterConfigEntity();
        FilterParamsRequest request = new FilterParamsRequest();
        request.setResultGroupSelection(selection(FilterResultGroupKey.grouped("2024")));
        Map<String, String> sqlParams = Map.of("YEAR_FROM", "2020");
        when(paramResolver.expand(same(request))).thenReturn(sqlParams);
        when(sqlFilter.getGpsTrackIdsFor(same(filter), same(sqlParams)))
                .thenReturn(groupedResult(entry(1L, "2024"), entry(2L, "2023")));

        QueryResult result = service.execute(filter, request);

        assertThat(result.getResultEntries()).extracting(QueryResult.QueryResultEntry::getId).containsExactly(1L);
        verify(paramResolver).expand(same(request));
        verify(sqlFilter, times(1)).getGpsTrackIdsFor(same(filter), same(sqlParams));
    }

    private static FilterExecutionService service() {
        return new FilterExecutionService(mock(GpsTrackSQLFilter.class), mock(FilterParamResolver.class));
    }

    private static QueryResult groupedResult(QueryResult.QueryResultEntry... entries) {
        QueryResult result = new QueryResult();
        result.setGroupingAvailable(true);
        result.setResultEntries(List.of(entries));
        return result;
    }

    private static QueryResult.QueryResultEntry entry(Long id, String group) {
        QueryResult.QueryResultEntry entry = new QueryResult.QueryResultEntry();
        entry.setId(id);
        entry.setGroup(group);
        return entry;
    }

    private static FilterResultGroupSelection selection(FilterResultGroupKey... keys) {
        FilterResultGroupSelection selection = new FilterResultGroupSelection();
        selection.setIncludedGroups(java.util.Arrays.asList(keys));
        return selection;
    }
}
