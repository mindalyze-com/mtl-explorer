package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilterTemplateGraphResolverTest {

    @Test
    void resolvesIncludedFiltersBeforeSelectedFilter() {
        FilterConfigRepository repository = mock(FilterConfigRepository.class);
        FilterConfigEntity base = filter("SmartBaseFilter", "select id from gps_track");
        FilterConfigEntity child = filter(
                "TracksByDistanceGradient",
                "select id from ( [[~{/GPS_TRACK/SmartBaseFilter}]] ) base_filter");
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "SmartBaseFilter"))
                .thenReturn(base);

        FilterTemplateGraphResolver resolver = resolver(repository);

        List<FilterConfigEntity> mergeOrder = resolver.resolveMergeOrder(child);

        assertThat(mergeOrder).containsExactly(base, child);
    }

    @Test
    void detectsCyclesInTemplateIncludes() {
        FilterConfigRepository repository = mock(FilterConfigRepository.class);
        FilterConfigEntity first = filter("First", "select id from ( [[~{/GPS_TRACK/Second}]] ) second_filter");
        FilterConfigEntity second = filter("Second", "select id from ( [[~{/GPS_TRACK/First}]] ) first_filter");
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "First"))
                .thenReturn(first);
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "Second"))
                .thenReturn(second);

        FilterTemplateGraphResolver resolver = resolver(repository);

        assertThatThrownBy(() -> resolver.resolveMergeOrder(first))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/GPS_TRACK/First -> /GPS_TRACK/Second -> /GPS_TRACK/First");
    }

    @Test
    void standaloneFiltersDoNotInheritMetadataSources() {
        FilterConfigEntity tracksByYear = filter("TracksByYear", "select id from gps_track where year = :YEAR_FROM");

        List<FilterConfigEntity> mergeOrder = resolver(mock(FilterConfigRepository.class)).resolveMergeOrder(tracksByYear);

        assertThat(mergeOrder).containsExactly(tracksByYear);
    }

    private static FilterTemplateGraphResolver resolver(FilterConfigRepository repository) {
        return new FilterTemplateGraphResolver(new FilterTemplateReferenceResolver(repository));
    }

    private static FilterConfigEntity filter(String name, String expression) {
        FilterConfigEntity filter = new FilterConfigEntity();
        filter.setFilterDomain(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK);
        filter.setFilterName(name);
        filter.setExpression(expression);
        return filter;
    }
}
