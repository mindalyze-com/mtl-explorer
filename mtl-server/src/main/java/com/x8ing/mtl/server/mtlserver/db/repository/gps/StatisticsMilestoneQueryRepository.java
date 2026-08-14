package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.measurement.MilestoneDefinition;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneDimension;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Finds the earliest qualifying track for arbitrary canonical milestone definitions.
 */
@Repository
public class StatisticsMilestoneQueryRepository {

    static final String QUERY_TEMPLATE = """
            WITH definitions(dimension, threshold, sort_order) AS (
                VALUES %s
            ),
            eligible_tracks AS (
                SELECT
                    id,
                    start_date,
                    GREATEST(0, COALESCE(track_length_in_meter, 0))::double precision AS distance_m,
                    GREATEST(0, COALESCE(ascent_in_meter, 0))::double precision AS ascent_m,
                    GREATEST(0, COALESCE(energy_net_total_wh, 0))::double precision AS energy_wh
                FROM gps_track
                WHERE id = ANY(:filterIds)
                  AND start_date IS NOT NULL
                  AND highlight_exclusion_reason IS NULL
                  AND statistics_exclusion_reason IS NULL
            )
            SELECT
                definition.dimension,
                definition.threshold,
                definition.sort_order,
                matched.track_id,
                matched.achieved
            FROM definitions definition
            CROSS JOIN LATERAL (
                SELECT
                    track.id AS track_id,
                    CASE definition.dimension
                        WHEN 'DISTANCE' THEN track.distance_m
                        WHEN 'ASCENT' THEN track.ascent_m
                        WHEN 'ENERGY' THEN track.energy_wh
                    END AS achieved
                FROM eligible_tracks track
                WHERE CASE definition.dimension
                    WHEN 'DISTANCE' THEN track.distance_m
                    WHEN 'ASCENT' THEN track.ascent_m
                    WHEN 'ENERGY' THEN track.energy_wh
                END >= definition.threshold
                ORDER BY track.start_date ASC, track.id ASC
                LIMIT 1
            ) matched
            ORDER BY definition.sort_order ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StatisticsMilestoneQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MilestoneResult> findMilestones(Long[] filterTrackIds, List<MilestoneDefinition> definitions) {
        if (filterTrackIds.length == 0 || definitions.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("filterIds", new SqlArrayValue("bigint", (Object[]) filterTrackIds));
        for (int index = 0; index < definitions.size(); index++) {
            MilestoneDefinition definition = definitions.get(index);
            parameters
                    .addValue("dimension" + index, definition.dimension().name())
                    .addValue("threshold" + index, definition.thresholdCanonical())
                    .addValue("sortOrder" + index, definition.sortOrder());
        }

        String query = QUERY_TEMPLATE.formatted(definitionRows(definitions.size()));
        return jdbcTemplate.query(query, parameters, (resultSet, rowNumber) -> new MilestoneResult(
                MilestoneDimension.valueOf(resultSet.getString("dimension")),
                resultSet.getDouble("threshold"),
                resultSet.getInt("sort_order"),
                resultSet.getLong("track_id"),
                resultSet.getDouble("achieved")
        ));
    }

    private static String definitionRows(int definitionCount) {
        return IntStream.range(0, definitionCount)
                .mapToObj(index -> "(CAST(:dimension%d AS text), CAST(:threshold%d AS double precision), CAST(:sortOrder%d AS integer))"
                        .formatted(index, index, index))
                .collect(Collectors.joining(",\n                "));
    }
}
