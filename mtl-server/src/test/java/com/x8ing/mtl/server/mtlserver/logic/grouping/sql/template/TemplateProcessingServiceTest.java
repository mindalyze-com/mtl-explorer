package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.readonly.DynamicSqlService;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateProcessingServiceTest {

    @Test
    void resolvesNestedTemplateIncludesAsPlainSql() {
        FilterConfigRepository repository = mock(FilterConfigRepository.class);
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "SmartBaseFilter"))
                .thenReturn(filter("SmartBaseFilter", "select id from gps_track where :DATE_TIME_FROM is null"));
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "ActivitiesByMainTypes"))
                .thenReturn(filter(
                        "ActivitiesByMainTypes",
                        """
                        select id, 'CYCLING' as grp
                        from gps_track
                        where id = any(select id from ( [[~{/GPS_TRACK/SmartBaseFilter}]] ) base_filter)
                        """));
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "ActivitiesByKeyword"))
                .thenReturn(filter(
                        "ActivitiesByKeyword",
                        """
                        select categorized.id, categorized.grp
                        from ( [[~{/GPS_TRACK/ActivitiesByMainTypes}]] ) categorized
                        where :SEARCH_WORD is null
                        """));
        TemplateProcessingService service = service(repository);

        String resolved = service.processTemplate("/GPS_TRACK/ActivitiesByKeyword");

        assertThat(resolved)
                .contains("select id from gps_track where :DATE_TIME_FROM is null")
                .doesNotContain("[[~{")
                .doesNotContain("#th:block")
                .doesNotContain("th:text");
        assertThat(DynamicSqlService.getNamedParamsForSQL(resolved))
                .containsExactly("DATE_TIME_FROM", "SEARCH_WORD");
    }

    @Test
    void detectsCyclesInTemplateIncludes() {
        FilterConfigRepository repository = mock(FilterConfigRepository.class);
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "First"))
                .thenReturn(filter("First", "select id from ( [[~{/GPS_TRACK/Second}]] ) second_filter"));
        when(repository.findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK, "Second"))
                .thenReturn(filter("Second", "select id from ( [[~{/GPS_TRACK/First}]] ) first_filter"));
        TemplateProcessingService service = service(repository);

        assertThatThrownBy(() -> service.processTemplate("/GPS_TRACK/First"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/GPS_TRACK/First -> /GPS_TRACK/Second -> /GPS_TRACK/First");
    }

    private static TemplateProcessingService service(FilterConfigRepository repository) {
        return new TemplateProcessingService(new FilterTemplateReferenceResolver(repository));
    }

    private static FilterConfigEntity filter(String name, String expression) {
        FilterConfigEntity filter = new FilterConfigEntity();
        filter.setFilterDomain(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK);
        filter.setFilterName(name);
        filter.setExpression(expression);
        return filter;
    }
}
