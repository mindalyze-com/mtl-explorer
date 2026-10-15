package com.x8ing.mtl.server.mtlserver.planner.service;

import com.x8ing.mtl.server.mtlserver.planner.client.BRouterClient;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.LegResultDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.RouteRequestDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.RouteResponseDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlannerServiceTest {
    @Test
    void routeAndCacheUseOneContinuousBufferAcrossLegs() {
        BRouterClient client = mock(BRouterClient.class);
        PlannerService service = new PlannerService(client, new PlannerProperties());
        LegResultDto first = leg(new double[]{100, 120, 100}, 100, 60);
        LegResultDto second = leg(new double[]{100, 120, 100}, 200, 120);
        when(client.computeLeg(any(), any(), eq("trekking"))).thenReturn(first, second);
        RouteRequestDto request = new RouteRequestDto();
        request.setProfile("trekking");
        request.setWaypoints(List.of(new WaypointDto(47, 8), new WaypointDto(47, 8.01), new WaypointDto(47, 8.02)));

        RouteResponseDto route = service.computeRoute(request);
        assertEquals(30, route.getStats().getAscentM());
        assertEquals(30, route.getStats().getDescentM());
        assertEquals(300, route.getStats().getDistanceM());
        assertEquals(180, route.getStats().getDurationSec());
        assertEquals(2, route.getStats().getLegCount());
        assertFalse(route.getStats().isAnyLegCached());

        RouteResponseDto cached = service.computeRoute(request);
        assertEquals(30, cached.getStats().getAscentM());
        assertEquals(30, cached.getStats().getDescentM());
        assertTrue(cached.getStats().isAnyLegCached());
        verify(client, times(2)).computeLeg(any(), any(), anyString());
    }

    @Test
    void trimmingRecalculatesFromRetainedGeometryWithoutRouting() {
        BRouterClient client = mock(BRouterClient.class);
        PlannerService service = new PlannerService(client, new PlannerProperties());
        var retained = leg(new double[]{100, 120, 100}, 150, 90);
        var stats = service.computeStats(List.of(retained));
        assertEquals(20, stats.getAscentM());
        assertEquals(20, stats.getDescentM());
        verifyNoInteractions(client);
    }

    @Test
    void partitioningAndDuplicateBoundaryPointsDoNotChangeTotals() {
        PlannerService service = new PlannerService(mock(BRouterClient.class), new PlannerProperties());
        var whole = service.computeStats(List.of(leg(new double[]{100, 120, 100, 120, 100}, 200, 100)));
        var split = service.computeStats(List.of(leg(new double[]{100, 120, 100}, 100, 50),
                leg(new double[]{100, 120, 100}, 100, 50)));
        assertEquals(whole.getAscentM(), split.getAscentM());
        assertEquals(whole.getDescentM(), split.getDescentM());
        assertEquals(whole.getDistanceM(), split.getDistanceM());
    }

    @Test
    void rejectsMalformedStatisticsRequests() {
        PlannerService service = new PlannerService(mock(BRouterClient.class), new PlannerProperties());
        assertThrows(IllegalArgumentException.class, () -> service.computeStats(null));
        var invalid = leg(new double[]{100, 101}, 10, 10);
        invalid.setDistanceM(-1);
        assertThrows(IllegalArgumentException.class, () -> service.computeStats(List.of(invalid)));
        invalid.setDistanceM(10);
        invalid.setCoordinates(List.of(new double[]{8, 95, 100}, new double[]{8, 47, 101}));
        assertThrows(IllegalArgumentException.class, () -> service.computeStats(List.of(invalid)));
    }

    private static LegResultDto leg(double[] heights, double distance, double duration) {
        LegResultDto leg = new LegResultDto();
        List<double[]> coordinates = new ArrayList<>();
        for (int i = 0; i < heights.length; i++) coordinates.add(new double[]{8 + i * .001, 47, heights[i]});
        leg.setCoordinates(coordinates);
        leg.setDistanceM(distance);
        leg.setDurationSec(duration);
        // Deliberately wrong supplied values: the service must use the geometry.
        leg.setAscentM(999);
        leg.setDescentM(999);
        return leg;
    }
}
