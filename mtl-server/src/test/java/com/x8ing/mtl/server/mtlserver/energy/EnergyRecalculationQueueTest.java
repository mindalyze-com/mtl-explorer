package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.mockito.Mockito.*;

class EnergyRecalculationQueueTest {
    @Test
    void unsupportedTimingCompletesTheQueuedGenerationWithoutAnEndlessRetry() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EnergyService energy = mock(EnergyService.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(42L);
        when(row.getLong(2)).thenReturn(7L);
        when(row.getBoolean(3)).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        new EnergyRecalculationQueue(jdbc, energy, new ShutdownSignal(), directRunner()).processPending();
        verifyNoInteractions(energy);
        verify(jdbc).update("DELETE FROM public.track_energy_recalculation WHERE track_id = ? AND generation = ?", 42L, 7L);
        verify(jdbc, never()).update(startsWith("UPDATE"), any(), any(), any(), any());
    }

    @Test
    void failedCalculationRemainsQueuedAndRetryAcknowledgesOnlyTheReadGeneration() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var energy = mock(EnergyService.class);
        var row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(42L);
        when(row.getLong(2)).thenReturn(7L);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(energy.recalculateEnergyForTrack(42L, null)).thenThrow(new IllegalStateException("synthetic failure")).thenReturn(true);
        var queue = new EnergyRecalculationQueue(jdbc, energy, new ShutdownSignal(), directRunner());
        queue.processPending();
        verify(jdbc, never()).update(startsWith("DELETE"), any(), any());
        verify(jdbc).update(contains("SET next_attempt_at"), eq(60L), eq("IllegalStateException"), eq(42L), eq(7L));
        queue.processPending();
        verify(jdbc).update("DELETE FROM public.track_energy_recalculation WHERE track_id = ? AND generation = ?", 42L, 7L);
    }

    @Test
    void statusDistinguishesQueuedRetriesFromCompletedAndUnavailable() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var energy = mock(EnergyService.class);
        var queue = new EnergyRecalculationQueue(jdbc, energy, new ShutdownSignal(), directRunner());
        var track = new GpsTrack();
        track.setId(42L);
        var row = mock(ResultSet.class);
        when(row.getTimestamp("next_attempt_at")).thenReturn(Timestamp.from(Instant.parse("2026-09-30T12:00:00Z")));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        Assertions.assertThat(queue.statusForTrack(track).state()).isEqualTo(EnergyCalculationStatus.State.PENDING);
        when(row.getString("last_error")).thenReturn("IllegalStateException");
        var status = queue.statusForTrack(track);
        Assertions.assertThat(status.state()).isEqualTo(EnergyCalculationStatus.State.RETRYING);
        Assertions.assertThat(status.message()).contains("failed").doesNotContain("IllegalStateException");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());
        var awaitingClassification = queue.statusForTrack(track);
        Assertions.assertThat(awaitingClassification.state()).isEqualTo(EnergyCalculationStatus.State.PENDING);
        Assertions.assertThat(awaitingClassification.message()).contains("activity classification");
        track.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        var missingActivity = queue.statusForTrack(track);
        Assertions.assertThat(missingActivity.state()).isEqualTo(EnergyCalculationStatus.State.UNAVAILABLE);
        Assertions.assertThat(missingActivity.message()).contains("Select an activity");
        track.setEnergyNetTotalWh(0.0);
        Assertions.assertThat(queue.statusForTrack(track).state()).isEqualTo(EnergyCalculationStatus.State.CALCULATED);
        track.setTimingUnreliable(true);
        Assertions.assertThat(queue.statusForTrack(track).state()).isEqualTo(EnergyCalculationStatus.State.UNAVAILABLE);
    }
    @Test
    void importedActivityWithoutASourceStillWaitsForClassification() {
        var jdbc = mock(JdbcTemplate.class);
        var queue = new EnergyRecalculationQueue(jdbc, mock(EnergyService.class), new ShutdownSignal(), directRunner());
        var track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.RUNNING);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());

        var status = queue.statusForTrack(track);

        Assertions.assertThat(status.state()).isEqualTo(EnergyCalculationStatus.State.PENDING);
        Assertions.assertThat(status.message()).contains("activity classification");
    }

    @Test
    void fullBatchContinuesOnlyWhenRequestsWereAcknowledgedOrDeferred() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var energy = mock(EnergyService.class);
        var row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(42L);
        when(row.getLong(2)).thenReturn(7L);
        when(row.getBoolean(3)).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenAnswer(call -> {
            var requests = new ArrayList<>();
            for (int i = 0; i < 100; i++) requests.add(((RowMapper<?>) call.getArgument(1)).mapRow(row, i));
            return requests;
        });
        var queue = new EnergyRecalculationQueue(jdbc, energy, new ShutdownSignal(), directRunner());
        Assertions.assertThat(queue.processBatch())
                .isEqualTo(false);
        when(jdbc.update(startsWith("DELETE"), anyLong(), anyLong())).thenReturn(1);
        Assertions.assertThat(queue.processBatch())
                .isEqualTo(true);
    }

}
