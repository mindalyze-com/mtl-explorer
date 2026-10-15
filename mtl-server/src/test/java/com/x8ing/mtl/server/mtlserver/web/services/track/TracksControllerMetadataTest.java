package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TracksControllerMetadataTest {
    @Mock
    GpsTrackRepository tracks;
    @Mock
    GpsTrackDataRepository geometry;
    @Mock
    GpsTrackEventRepository events;
    @InjectMocks
    TracksController controller;

    @Test
    void metadataRequestReturnsCurrentFieldsWithoutLoadingGeometryOrEvents() throws Exception {
        var track = new GpsTrack();
        track.setId(1L);
        track.setTrackName("Synthetic track");
        track.setTrackDescription("Synthetic description");
        when(tracks.findById(1L)).thenReturn(Optional.of(track));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/tracks/get/1").param("includeGeometry", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackName").value("Synthetic track"))
                .andExpect(jsonPath("$.trackDescription").value("Synthetic description"))
                .andExpect(jsonPath("$.gpsTracksData").isEmpty());

        verifyNoInteractions(geometry, events);
    }

    @Test
    void existingRequestsStillIncludeGeometryAndEventsByDefault() throws Exception {
        var track = new GpsTrack();
        track.setId(1L);
        var data = new GpsTrackData();
        data.setId(2L);
        when(tracks.findById(1L)).thenReturn(Optional.of(track));
        when(geometry.findFirstByGpsTrackIdAndPrecisionInMeter(1L, BigDecimal.ONE)).thenReturn(data);
        when(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(1L)).thenReturn(List.of());

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/tracks/get/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpsTracksData[0].id").value(2));

        verify(events).findAllByGpsTrackIdOrderByStartPointIndexAsc(1L);
    }
}
