package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartSeriesController;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartSeriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissingTrackEndpointsTest {

    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final GpsTrackDataRepository trackData = mock(GpsTrackDataRepository.class);
    private final GpsTrackDataPointRepository points = mock(GpsTrackDataPointRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TracksController controller = new TracksController(tracks, null, null, trackData,
                null, null, null, null, null, null, null, null, Runnable::run,
                null, null, null, null);
        mvc = MockMvcBuilders.standaloneSetup(controller,
                new ChartSeriesController(new ChartSeriesService(tracks, points))).build();
    }

    @Test
    void missingTrackDetailsReturnNotFoundBeforeLoadingGeometry() throws Exception {
        mvc.perform(get("/api/tracks/get/999999999"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(trackData);
    }

    @Test
    void missingTrackChartReturnsNotFoundBeforeLoadingPoints() throws Exception {
        mvc.perform(get("/api/tracks/999999999/chart-series"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(points);
    }

    @Test
    void existingTrackDetailsStillReturnSuccessfully() throws Exception {
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        when(tracks.findById(42L)).thenReturn(Optional.of(track));

        mvc.perform(get("/api/tracks/get/42"))
                .andExpect(status().isOk());
    }
}
