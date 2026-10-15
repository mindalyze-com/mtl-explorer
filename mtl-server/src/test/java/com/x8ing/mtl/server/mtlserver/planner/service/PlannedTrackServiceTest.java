package com.x8ing.mtl.server.mtlserver.planner.service;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedTrackServiceTest {

    @Mock
    private GpsTrackRepository gpsTrackRepository;
    @Mock
    private GpsTrackDataRepository gpsTrackDataRepository;
    @Mock
    private GpsTrackEventRepository gpsTrackEventRepository;

    @Test
    void rejectsMalformedRouteCoordinatesBeforeAnyPersistence() {
        for (double[] invalid : Arrays.asList(null, new double[]{8}, new double[]{181, 47},
                new double[]{8, 91}, new double[]{Double.NaN, 47}, new double[]{8, Double.POSITIVE_INFINITY})) {
            for (boolean fromLegs : List.of(false, true)) {
                SavePlannedTrackDto dto = new SavePlannedTrackDto();
                List<double[]> coordinates = Arrays.asList(invalid, new double[]{8, 47, 100});
                dto.setCoordinates(coordinates);
                if (fromLegs) {
                    LegResultDto leg = new LegResultDto();
                    leg.setCoordinates(coordinates);
                    dto.setLegs(List.of(leg));
                    // Valid flattened input cannot hide invalid authoritative leg geometry.
                    dto.setCoordinates(List.of(new double[]{8, 47}, new double[]{8.01, 47.01}));
                }
                assertThrows(IllegalArgumentException.class, () -> service().save(dto));
            }
        }
        verifyNoInteractions(gpsTrackRepository, gpsTrackDataRepository, gpsTrackEventRepository);
    }

    @Test
    void rejectsInvalidWaypointMetadataBeforePersistence() {
        SavePlannedTrackDto dto = new SavePlannedTrackDto();
        dto.setCoordinates(List.of(new double[]{8, 47}, new double[]{8.01, 47.01}));
        dto.setWaypoints(List.of(new WaypointDto(91, 8), new WaypointDto(47, 8.01)));
        assertThrows(IllegalArgumentException.class, () -> service().save(dto));
        verifyNoInteractions(gpsTrackRepository, gpsTrackDataRepository, gpsTrackEventRepository);
    }

    @Test
    void saveAndLoadRecalculatesElevationAndPreservesGeometryDistanceAndDuration() {
        PlannedTrackService service = new PlannedTrackService(
                JsonMapper.builder().build(),
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
        assertEquals(20.0, saved.getAscentInMeter());
        assertEquals(30.0, saved.getDescentInMeter());
        assertNotNull(saved.getPlannerRouteJson());
        assertTrue(saved.getPlannerRouteJson().contains("\"schemaVersion\":2"));
        assertTrue(saved.getPlannerRouteJson().contains("\"distanceM\":100.0"));
        assertTrue(saved.getPlannerRouteJson().contains("\"ascentM\":20.0"));
        // A read must also calculate from geometry when stored statistics are stale.
        saved.setPlannerRouteJson(saved.getPlannerRouteJson().replace("\"ascentM\":20.0", "\"ascentM\":999.0"));

        when(gpsTrackRepository.findById(99L)).thenReturn(Optional.of(saved));
        when(gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(99L, GpsTrackData.PRECISION_1M))
                .thenReturn(dataCaptor.getValue());

        PlannedTrackDetailDto loaded = service.loadDetail(99L);

        assertEquals(2, loaded.getLegs().size());
        assertEquals(300.0, loaded.getStats().getDistanceM());
        assertEquals(20.0, loaded.getStats().getAscentM());
        assertEquals(30.0, loaded.getStats().getDescentM());
        assertEquals(20.0, loaded.getLegs().getFirst().getAscentM());
        assertEquals(1_200.0, loaded.getStats().getDurationSec());
        assertEquals(4, loaded.getCoordinates().size());
    }

    @Test
    void geometryOnlySaveComputesBothElevationTotals() {
        PlannedTrackService service = new PlannedTrackService(JsonMapper.builder().build(),
                gpsTrackRepository, gpsTrackDataRepository, gpsTrackEventRepository);
        when(gpsTrackRepository.save(any())).thenAnswer(invocation -> {
            GpsTrack track = invocation.getArgument(0);
            track.setId(100L);
            return track;
        });
        SavePlannedTrackDto dto = new SavePlannedTrackDto();
        dto.setCoordinates(List.of(new double[]{8, 47, 100}, new double[]{8.001, 47, 99.5},
                new double[]{8.002, 47, 99}, new double[]{8.003, 47, 98.5}));
        GpsTrack saved = service.save(dto);
        assertEquals(0.0, saved.getAscentInMeter());
        assertEquals(1.5, saved.getDescentInMeter());
        assertTrue(saved.getTrackLengthInMeter() > 0);
    }

    @Test
    void savesPointsInsideThePlanAndExportsOnlyRouteGeometry() {
        PlannedTrackService service = service();
        SavePlannedTrackDto dto = plannedRoute();
        PointOfInterestDto point = point();
        point.setName("Waterfall & picnic");
        point.setIconType(PointOfInterestIconType.VIEWPOINT);
        point.setNotes("Visit later <when dry>");
        dto.setPointsOfInterest(List.of(point));
        when(gpsTrackRepository.save(any())).thenAnswer(invocation -> {
            GpsTrack track = invocation.getArgument(0);
            track.setId(101L);
            return track;
        });
        ArgumentCaptor<GpsTrackData> geometry = ArgumentCaptor.forClass(GpsTrackData.class);
        when(gpsTrackDataRepository.save(geometry.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        GpsTrack saved = service.save(dto);
        when(gpsTrackRepository.findById(101L)).thenReturn(Optional.of(saved));
        when(gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(101L, GpsTrackData.PRECISION_1M))
                .thenReturn(geometry.getValue());

        assertEquals(List.of(point), service.loadDetail(101L).getPointsOfInterest());
        assertTrue(saved.getPlannerRouteJson().contains("pointsOfInterest"));
        assertTrue(saved.getPlannerRouteJson().contains("\"iconType\":\"viewpoint\""));
        assertEquals(300.0, saved.getTrackLengthInMeter());
        String gpx = service.buildGpx(101L);
        assertFalse(gpx.contains("<wpt"));
        assertFalse(gpx.contains("Waterfall"));
        assertEquals(4, gpx.split("<trkpt ").length - 1);
    }

    @Test
    void pointOnlyPlanHasBoundsButNoTrackGeometryOrGpx() {
        PlannedTrackService service = service();
        SavePlannedTrackDto dto = new SavePlannedTrackDto();
        dto.setPointsOfInterest(List.of(point()));
        dto.setStats(new LiveStatsDto());
        dto.getStats().setDistanceM(1234.0);
        when(gpsTrackRepository.save(any())).thenAnswer(invocation -> {
            GpsTrack track = invocation.getArgument(0);
            track.setId(102L);
            return track;
        });

        GpsTrack saved = service.save(dto);
        assertEquals(0, saved.getNumberOfTrackPoints());
        assertEquals(0.0, saved.getTrackLengthInMeter());
        assertEquals(point().getLat(), saved.getCenterLat());
        assertEquals(point().getLng(), saved.getCenterLng());
        verify(gpsTrackDataRepository, never()).save(any());
        when(gpsTrackRepository.findById(102L)).thenReturn(Optional.of(saved));
        PlannedTrackDetailDto loaded = service.loadDetail(102L);
        assertTrue(loaded.getCoordinates().isEmpty());
        assertEquals(List.of(point()), loaded.getPointsOfInterest());
        assertEquals(0, loaded.getStats().getLegCount());
        assertThrows(IllegalArgumentException.class, () -> service.buildGpx(102L));
        when(gpsTrackRepository.findByTrackSource(GpsTrack.TRACK_SOURCE.PLANNED)).thenReturn(List.of(saved));
        assertFalse(service.listPlannedTracks().getFirst().isHasRoute());
        assertEquals(1, service.listPlannedTracks().getFirst().getPointOfInterestCount());
    }

    @Test
    void rejectsInvalidPointsAndEmptyPlansBeforePersistence() {
        PlannedTrackService service = service();
        SavePlannedTrackDto dto = plannedRoute();
        PointOfInterestDto point = point();
        dto.setPointsOfInterest(List.of(point, point));
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        dto.setPointsOfInterest(List.of(point));
        point.setLat(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        point.setLat(47.0);
        point.setName(" ");
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        point.setName("Waterfall");
        point.setId("not-an-id");
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        point = point();
        dto.setPointsOfInterest(List.of(point));
        point.setLng(181.0);
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        point.setLng(8.0);
        point.setName("a".repeat(PlannerConstants.MAX_POINT_OF_INTEREST_NAME_LENGTH + 1));
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        point.setName("Stop");
        point.setNotes("a".repeat(PlannerConstants.MAX_POINT_OF_INTEREST_NOTES_LENGTH + 1));
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        dto.setPointsOfInterest(java.util.Collections.nCopies(PlannerConstants.MAX_POINTS_OF_INTEREST + 1, point));
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        assertThrows(IllegalArgumentException.class, () -> service.save(new SavePlannedTrackDto()));
        verify(gpsTrackRepository, never()).save(any());
    }

    @Test
    void loadsExistingRouteMetadataAndRefusesUnreadableOrFutureDocuments() {
        PlannedTrackService service = service();
        GpsTrack track = new GpsTrack();
        track.setId(103L);
        track.setTrackSource(GpsTrack.TRACK_SOURCE.PLANNED);
        track.setPlannerRouteJson("{\"schemaVersion\":1,\"legs\":[]}");
        when(gpsTrackRepository.findById(103L)).thenReturn(Optional.of(track));
        assertTrue(service.loadDetail(103L).getPointsOfInterest().isEmpty());
        track.setPlannerRouteJson("{\"schemaVersion\":999}");
        assertThrows(IllegalArgumentException.class, () -> service.loadDetail(103L));
        track.setPlannerRouteJson("{broken");
        assertThrows(IllegalArgumentException.class, () -> service.loadDetail(103L));
        track.setPlannerRouteJson("[]");
        assertThrows(IllegalArgumentException.class, () -> service.loadDetail(103L));
        track.setPlannerRouteJson("{broken");
        when(gpsTrackRepository.findByTrackSource(GpsTrack.TRACK_SOURCE.PLANNED)).thenReturn(List.of(track));
        assertEquals(1, service.listPlannedTracks().size());
        assertEquals(0, service.listPlannedTracks().getFirst().getPointOfInterestCount());
    }

    private PlannedTrackService service() {
        return new PlannedTrackService(JsonMapper.builder().build(),
                gpsTrackRepository, gpsTrackDataRepository, gpsTrackEventRepository);
    }

    @Test
    void missingOrNullIconUsesPinAndUnknownIconIsRejected() {
        var mapper = JsonMapper.builder().build();
        String json = "{\"id\":\"73d1b1eb-e829-4b4c-a15b-87a2e2f4a7d5\",\"lat\":47,\"lng\":8,\"name\":\"Stop\"}";
        PointOfInterestDto omitted = mapper.readValue(json, PointOfInterestDto.class);
        assertEquals(PointOfInterestIconType.PIN, omitted.getIconType());
        PointOfInterestDto explicitNull = mapper.readValue(json.replace("}", ",\"iconType\":null}"), PointOfInterestDto.class);
        SavePlannedTrackDto dto = new SavePlannedTrackDto();
        dto.setPointsOfInterest(List.of(explicitNull));
        when(gpsTrackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GpsTrack saved = service().save(dto);
        assertEquals(PointOfInterestIconType.PIN, explicitNull.getIconType());
        assertTrue(saved.getPlannerRouteJson().contains("\"iconType\":\"pin\""));
        assertThrows(Exception.class, () -> mapper.readValue(json.replace("}", ",\"iconType\":\"unknown\"}"), PointOfInterestDto.class));
    }

    private static PointOfInterestDto point() {
        PointOfInterestDto point = new PointOfInterestDto();
        point.setId("73d1b1eb-e829-4b4c-a15b-87a2e2f4a7d5");
        point.setLat(47.3);
        point.setLng(8.3);
        point.setName("Waterfall");
        return point;
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
