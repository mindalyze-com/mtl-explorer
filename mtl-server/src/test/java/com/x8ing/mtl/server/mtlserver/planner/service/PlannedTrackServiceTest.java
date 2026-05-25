package com.x8ing.mtl.server.mtlserver.planner.service;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.planner.dto.LegResultDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.LiveStatsDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.PlannedTrackDetailDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.SavePlannedTrackDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannedTrackServiceTest {

    @Mock
    private GpsTrackRepository gpsTrackRepository;
    @Mock
    private GpsTrackDataRepository gpsTrackDataRepository;
    @Mock
    private GpsTrackEventRepository gpsTrackEventRepository;

    @Test
    void saveAndLoadPreservesPlannerLegsAndStats() {
        PlannedTrackService service = new PlannedTrackService(
                gpsTrackRepository,
                gpsTrackDataRepository,
                gpsTrackEventRepository
        );
        SavePlannedTrackDto saveDto = plannedRoute();
        ArgumentCaptor<GpsTrack> trackCaptor = ArgumentCaptor.forClass(GpsTrack.class);
        ArgumentCaptor<GpsTrackData> dataCaptor = ArgumentCaptor.forClass(GpsTrackData.class);
        when(gpsTrackRepository.save(trackCaptor.capture())).thenAnswer(invocation -> {
            GpsTrack track = invocation.getArgument(0);
            track.setId(99L);
            return track;
        });
        when(gpsTrackDataRepository.save(dataCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        GpsTrack saved = service.save(saveDto);

        assertEquals(99L, saved.getId());
        assertEquals(300.0, saved.getTrackLengthInMeter());
        assertEquals(42.0, saved.getAscentInMeter());
        assertEquals(9.0, saved.getDescentInMeter());
        assertNotNull(saved.getPlannerRouteJson());
        assertTrue(saved.getPlannerRouteJson().contains("\"schemaVersion\":1"));
        assertTrue(saved.getPlannerRouteJson().contains("\"distanceM\":100.0"));
        assertTrue(saved.getPlannerRouteJson().contains("\"ascentM\":42.0"));

        when(gpsTrackRepository.findById(99L)).thenReturn(Optional.of(saved));
        when(gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(99L, GpsTrackData.PRECISION_1M))
                .thenReturn(dataCaptor.getValue());

        PlannedTrackDetailDto loaded = service.loadDetail(99L);

        assertEquals(2, loaded.getLegs().size());
        assertEquals(300.0, loaded.getStats().getDistanceM());
        assertEquals(42.0, loaded.getStats().getAscentM());
        assertEquals(9.0, loaded.getStats().getDescentM());
        assertEquals(1_200.0, loaded.getStats().getDurationSec());
        assertEquals(4, loaded.getCoordinates().size());
    }

    private static SavePlannedTrackDto plannedRoute() {
        SavePlannedTrackDto dto = new SavePlannedTrackDto();
        dto.setName("Saved route");
        dto.setDescription("Planner persistence test");
        dto.setProfile("trekking");
        dto.setWaypoints(List.of(waypoint(47.0, 8.0), waypoint(47.2, 8.2)));
        dto.setLegs(List.of(
                leg(List.of(
                        new double[]{8.0, 47.0, 100.0},
                        new double[]{8.1, 47.1, 120.0}
                ), 100.0, 20.0, 2.0, 400.0),
                leg(List.of(
                        new double[]{8.1, 47.1, 120.0},
                        new double[]{8.2, 47.2, 90.0}
                ), 200.0, 22.0, 7.0, 800.0)
        ));
        LiveStatsDto stats = new LiveStatsDto();
        stats.setDistanceM(300.0);
        stats.setAscentM(42.0);
        stats.setDescentM(9.0);
        stats.setDurationSec(1_200.0);
        stats.setLegCount(2);
        dto.setStats(stats);
        return dto;
    }

    private static WaypointDto waypoint(double lat, double lng) {
        WaypointDto waypoint = new WaypointDto();
        waypoint.setLat(lat);
        waypoint.setLng(lng);
        return waypoint;
    }

    private static LegResultDto leg(List<double[]> coordinates,
                                    double distanceM,
                                    double ascentM,
                                    double descentM,
                                    double durationSec) {
        LegResultDto leg = new LegResultDto();
        leg.setCoordinates(coordinates);
        leg.setDistanceM(distanceM);
        leg.setAscentM(ascentM);
        leg.setDescentM(descentM);
        leg.setDurationSec(durationSec);
        return leg;
    }
}
