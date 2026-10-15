package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.TrackScopeProvider;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.TrackViewScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class TrackScopeService implements TrackScopeProvider {

    private static final short CONNECTION_ID = 1;

    private final JdbcTemplate jdbc;

    public TrackScopeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TrackViewScope scope() {
        return TrackViewScope.GOOGLE_HEALTH_EXERCISE;
    }

    @Override
    public Set<Long> trackIds() {
        return canonicalExerciseTrackIds();
    }

    public Set<Long> canonicalExerciseTrackIds() {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT COALESCE(track.duplicate_of, track.id) AS track_id
                  FROM google_health.route_ownership point
                  JOIN public.gps_track track ON track.id = point.gps_track_id
                 WHERE point.connection_id = ?
                   AND point.gps_track_id IS NOT NULL
                 ORDER BY track_id
                """, Long.class, CONNECTION_ID));
    }

    public Long canonicalTrackId(Long associatedTrackId) {
        if (associatedTrackId == null) {
            return null;
        }
        return jdbc.queryForList("""
                SELECT COALESCE(duplicate_of, id) FROM public.gps_track WHERE id = ?
                """, Long.class, associatedTrackId).stream().findFirst().orElse(null);
    }
}
