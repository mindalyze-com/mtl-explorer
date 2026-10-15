package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.TrackViewScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackScopeServiceTest {

    @Test
    void resolvesGoogleDuplicatesToCanonicalMtlTracks() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq((short) 1)))
                .thenReturn(List.of(12L, 19L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(30L))).thenReturn(List.of(12L));
        TrackScopeService service = new TrackScopeService(jdbc);

        assertThat(service.canonicalExerciseTrackIds()).containsExactly(12L, 19L);
        assertThat(service.scope()).isEqualTo(TrackViewScope.GOOGLE_HEALTH_EXERCISE);
        assertThat(service.trackIds()).containsExactly(12L, 19L);
        assertThat(service.canonicalTrackId(30L)).isEqualTo(12L);
    }
}
