package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.measurement.MilestoneDefinition;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneDimension;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsMilestoneQueryRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void bindsCanonicalDefinitionsIntoOneFilteredLateralQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        StatisticsMilestoneQueryRepository repository = new StatisticsMilestoneQueryRepository(jdbcTemplate);

        repository.findMilestones(
                new Long[]{7L, 11L},
                List.of(
                        new MilestoneDefinition(MilestoneDimension.DISTANCE, 160_934.4, 1),
                        new MilestoneDefinition(MilestoneDimension.ASCENT, 1524.0, 2)
                )
        );

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(query.capture(), parameters.capture(), any(RowMapper.class));

        assertThat(query.getValue())
                .contains("CROSS JOIN LATERAL")
                .contains("id = ANY(:filterIds)")
                .contains("highlight_exclusion_reason IS NULL")
                .contains("statistics_exclusion_reason IS NULL")
                .contains("ORDER BY track.start_date ASC, track.id ASC")
                .doesNotContain("160934.4")
                .doesNotContain("100000");
        assertThat(parameters.getValue().getValue("filterIds")).isInstanceOf(SqlArrayValue.class);
        assertThat(parameters.getValue().getValue("dimension0")).isEqualTo("DISTANCE");
        assertThat(parameters.getValue().getValue("threshold0")).isEqualTo(160_934.4);
        assertThat(parameters.getValue().getValue("dimension1")).isEqualTo("ASCENT");
        assertThat(parameters.getValue().getValue("threshold1")).isEqualTo(1524.0);
    }

    @Test
    void skipsTheDatabaseWhenThereAreNoTracks() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        StatisticsMilestoneQueryRepository repository = new StatisticsMilestoneQueryRepository(jdbcTemplate);

        List<MilestoneResult> results = repository.findMilestones(
                new Long[0],
                List.of(new MilestoneDefinition(MilestoneDimension.DISTANCE, 100_000.0, 1))
        );

        assertThat(results).isEmpty();
    }
}
