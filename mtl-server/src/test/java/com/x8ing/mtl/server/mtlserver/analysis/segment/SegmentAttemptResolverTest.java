package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.*;
import com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.core;
import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.point;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SegmentAttemptResolverTest {
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final GpsTrackVariantSelector variants = mock(GpsTrackVariantSelector.class);
    private final GpsTrackEventRepository events = mock(GpsTrackEventRepository.class);
    private final SegmentAttemptResolver resolver = new SegmentAttemptResolver(tracks, variants, events, new SegmentAttemptTokens(
            io.jsonwebtoken.Jwts.SIG.HS256.key().build(), java.time.Clock.systemUTC()));

    @AfterEach
    void clearOwner() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serverVisitNumbersRetainPreviousVisitForSameZoneReturns() {
        var dto = publish(List.of(point(0, 0, 0), point(1, 60, 600), point(2, 120, 1200)), "A", "A", "A");
        var attempts = dto.getCrossings().get(1L).getAttempts();
        assertEquals(1, attempts.get(0).identity().fromVisit());
        assertEquals(2, attempts.get(0).identity().toVisit());
        assertEquals(2, attempts.get(1).identity().fromVisit());
        assertEquals(3, attempts.get(1).identity().toVisit());
        assertEquals(3, dto.getCrossings().get(1L).getCrossings().get(2).getVisitNumber());
    }

    @Test
    void changedPointMetricsInvalidateEvenWithoutParentVersionIncrement() {
        var points = List.of(point(0, 0, 0), point(1, 60, 600));
        var reference = publish(points, "A", "B").getCrossings().get(1L).getAttempts().getFirst().identity().reference();
        assertEquals(2, resolver.resolve(reference).points().size());
        points.getLast().setPowerWatts(999d);
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class, () -> resolver.resolve(reference)).getStatusCode());
    }

    @Test
    void unknownReferencesAreExplicitlyStaleAndReferencesAreOwnerScoped() {
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class, () -> resolver.resolve("evicted")).getStatusCode());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("one", "unused"));
        var reference = publish(List.of(point(0, 0, 0), point(1, 60, 600)), "A", "B")
                .getCrossings().get(1L).getAttempts().getFirst().identity().reference();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("two", "unused"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> resolver.resolve(reference)).getStatusCode());
    }

    @Test
    void unreliableTrackRemainsResolvableForGeometryInspection() {
        var points = List.of(point(0, 0, 0), point(1, 60, 600));
        var reference = publish(true, points, "A", "B").getCrossings().get(1L).getAttempts().getFirst().identity().reference();
        assertEquals(points, resolver.resolve(reference).points());
    }

    @Test
    void aChangedTimingClassificationInvalidatesPublishedAttempts() {
        var reference = publish(List.of(point(0, 0, 0), point(1, 60, 600)), "A", "B")
                .getCrossings().get(1L).getAttempts().getFirst().identity().reference();
        tracks.findById(1L).orElseThrow().setTimingUnreliable(true);
        assertEquals(HttpStatus.GONE,
                assertThrows(ResponseStatusException.class, () -> resolver.resolve(reference)).getStatusCode());
    }

    @Test
    void consolidatedMeansUseDifferentValidDenominatorsForEachMetric() {
        var a = core(60, 600, new double[][]{{0, 0}, {600, 60}}).summary();
        var b = new SegmentAttemptSummary(a.identity(), 1000d, null, null, null, null, null, null, Map.of());
        var mean = SegmentConsolidation.summarize(List.of(a, b)).getFirst();
        assertEquals(2, mean.visitCount());
        assertEquals(1, mean.elapsedCount());
        assertEquals(2, mean.distanceCount());
        assertEquals(60d, mean.meanElapsedSeconds());
        assertEquals(800d, mean.meanDistanceMeters());
        assertEquals(36d, mean.meanVisitSpeedKmh());
        var longer = core(180, 600, new double[][]{{0, 0}, {600, 180}}).summary();
        var repeatMean = SegmentConsolidation.summarize(List.of(a, longer)).getFirst();
        assertEquals(120d, repeatMean.meanElapsedSeconds());
        assertEquals(24d, repeatMean.meanVisitSpeedKmh());
    }

    @Test
    void overnightElapsedDoesNotContributeToConsolidatedPerformanceMean() {
        var normal = core(10, 100, new double[][]{{0, 0}, {100, 10}}).summary();
        var overnight = new SegmentAttemptSummary(normal.identity(), null, 72004d, null, null, null, null, null,
                Map.of("performance", new SegmentMetricAvailability(SegmentMetricAvailability.State.UNAVAILABLE, "RECORDING_GAP", null)));
        var summary = SegmentConsolidation.summarize(List.of(normal, overnight)).getFirst();
        assertEquals(2, summary.visitCount());
        assertEquals(1, summary.elapsedCount());
        assertEquals(10d, summary.meanElapsedSeconds());
    }

    private CrossingPointsResponseDto publish(List<GpsTrackDataPoint> points, String... names) {
        return publish(false, points, names);
    }

    private CrossingPointsResponseDto publish(boolean unreliable, List<GpsTrackDataPoint> points, String... names) {
        var track = new GpsTrack();
        track.setId(1L);
        track.setVersion(1L);
        track.setTimingUnreliable(unreliable);
        List<Crossing> crossings = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            var c = new Crossing();
            var trigger = new TriggerPoint();
            trigger.name = names[i];
            c.triggerPoint = trigger;
            c.gpsTrackId = 1L;
            c.gpsTrackDataPoint = points.get(i);
            c.trackPointPosition = (double) i;
            c.timeInSecSinceLastTriggerPoint = i == 0 ? null : 60d;
            c.distanceInMeterSinceLastTriggerPoint = i == 0 ? null : 600d;
            c.avgSpeedSinceLastTriggerPoint = i == 0 ? null : 36d;
            c.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.AVAILABLE;
            crossings.add(c);
        }
        var perTrack = new CrossingsPerTrack(track, crossings);
        perTrack.setZoneRevision("zones");
        perTrack.setSourceRevision(CanonicalTrackRevision.of(track, points, List.of()));
        SegmentAttemptResolver.prepare(perTrack, points, List.of());
        var response = new CrossingPointsResponse();
        response.crossings.put(1L, perTrack);
        var dto = CrossingPointsResponseDto.from(response);
        when(tracks.findById(1L)).thenReturn(Optional.of(track));
        when(variants.pointsForMetrics(1L)).thenReturn(points);
        when(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(1L)).thenReturn(List.of());
        resolver.publish(response, dto);
        return dto;
    }
}
