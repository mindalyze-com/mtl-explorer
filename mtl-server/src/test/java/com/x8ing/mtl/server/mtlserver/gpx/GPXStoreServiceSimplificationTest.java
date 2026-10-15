package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified.SimplifiedTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified.SimplifiedTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.logic.motion.GpsTrackEventService;
import jakarta.persistence.EntityManager;
import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.jts.JTS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class GPXStoreServiceSimplificationTest {
    private static final long TRACK_ID = 101L;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private final GpsTrackDataRepository trackData = mock(GpsTrackDataRepository.class);
    private final GpsTrackDataPointRepository points = mock(GpsTrackDataPointRepository.class);
    private final SimplifiedTrackRepository shapes = mock(SimplifiedTrackRepository.class);
    private final GPXStoreService store = new GPXStoreService(
            mock(GpsTrackRepository.class), trackData, shapes, points, mock(GpsTrackEventRepository.class),
            mock(GpsTrackEventService.class), Map.of("kalman", mock(GpsSmoothingAlgorithm.class)), "kalman");

    GPXStoreServiceSimplificationTest() {
        ReflectionTestUtils.setField(store, "entityManager", mock(EntityManager.class));
    }

    @ParameterizedTest
    @CsvSource({"NaN, 20", "Infinity, 20", "-Infinity, 20", "10, NaN", "10, Infinity", "10, 91", "10, -91"})
    void invalidSimplifiedVertexFallsBackToCanonicalGeometryAndPreservesRecordingGap(double longitude, double latitude) {
        LineString canonical = canonicalTrack();
        GpsTrackData cleaned = GpsTrackData.builder().track(canonical)
                .recordingBreakBeforePointIndexes(List.of(2)).build();
        // Synthetic invalid derived vertex at the same time as a recording-gap endpoint.
        LineString invalidShape = line(canonical.getCoordinateN(0),
                new CoordinateXYZM(longitude, latitude, 100, 70), canonical.getCoordinateN(3));
        stubShape(invalidShape, cleaned);

        assertDoesNotThrow(this::calculateSimplified);

        GpsTrackData saved = savedShape();
        assertThat(saved.getTrack().equalsExact(canonical)).isTrue();
        assertThat(saved.getTrack().getSRID()).isEqualTo(4326);
        for (int i = 0; i < canonical.getNumPoints(); i++) {
            assertThat(saved.getTrack().getCoordinateN(i).getM()).isEqualTo(canonical.getCoordinateN(i).getM());
            assertThat(saved.getTrack().getCoordinateN(i).getZ()).isEqualTo(canonical.getCoordinateN(i).getZ());
        }
        assertThat(saved.getRecordingBreakBeforePointIndexes()).containsExactly(2);
        assertThat(saved.getTrackType()).isEqualTo(GpsTrackData.TRACK_TYPE.SIMPLIFIED_SHAPE);
        assertThat(saved.getPrecisionInMeter()).isEqualByComparingTo(GpsTrackData.PRECISION_10M);
        assertCanonicalPointRows(0, 1, 2, 3);
        assertThat(canonical.getCoordinateN(2).getX()).isEqualTo(10.0002);
        assertThat(Double.doubleToLongBits(invalidShape.getCoordinateN(1).getX()))
                .isEqualTo(Double.doubleToLongBits(longitude));
    }

    @Test
    void validSimplificationKeepsReducedGeometryAndCanonicalPointReferences() {
        LineString canonical = canonicalTrack();
        LineString reduced = line(canonical.getCoordinateN(0), canonical.getCoordinateN(3));
        stubShape(reduced, GpsTrackData.builder().track(canonical)
                .recordingBreakBeforePointIndexes(List.of()).build());

        calculateSimplified();

        assertThat(savedShape().getTrack().equalsExact(reduced)).isTrue();
        assertCanonicalPointRows(0, 3);
    }

    @ParameterizedTest
    @CsvSource({"NaN", "100"})
    void xymSimplificationRestoresCanonicalElevationsAndKeepsTimestamps(double firstElevation) {
        LineString canonical = canonicalTrack();
        canonical.getCoordinateN(0).setZ(firstElevation);
        canonical.getCoordinateN(3).setZ(130.25);
        LineString reduced = line(new CoordinateXYM(10, 20, 0), new CoordinateXYM(10.0003, 20, 80));
        stubShape(reduced, GpsTrackData.builder().track(canonical).build());

        calculateSimplified();

        LineString saved = savedShape().getTrack();
        assertThat(saved.getNumPoints()).isEqualTo(2);
        assertThat(Double.doubleToLongBits(saved.getCoordinateN(0).getZ()))
                .isEqualTo(Double.doubleToLongBits(firstElevation));
        assertThat(saved.getCoordinateN(1).getZ()).isEqualTo(130.25);
        assertThat(saved.getCoordinateN(0).getM()).isZero();
        assertThat(saved.getCoordinateN(1).getM()).isEqualTo(80);
        assertThat(reduced.getCoordinateN(1).getZ()).isNaN();
        assertCanonicalPointRows(0, 3);
    }

    @Test
    void xymSimplificationPreservesRecordingGapAnchorsAndTheirElevations() {
        LineString canonical = canonicalTrack();
        canonical.getCoordinateN(0).setZ(Double.NaN);
        canonical.getCoordinateN(2).setZ(125);
        LineString reduced = line(new CoordinateXYM(10, 20, 0), new CoordinateXYM(10.0003, 20, 80));
        stubShape(reduced, GpsTrackData.builder().track(canonical)
                .recordingBreakBeforePointIndexes(List.of(2)).build());

        calculateSimplified();

        GpsTrackData saved = savedShape();
        assertThat(saved.getTrack().getNumPoints()).isEqualTo(4);
        assertThat(saved.getTrack().getCoordinateN(0).getZ()).isNaN();
        assertThat(saved.getTrack().getCoordinateN(2).getZ()).isEqualTo(125);
        assertThat(saved.getRecordingBreakBeforePointIndexes()).containsExactly(2);
        assertCanonicalPointRows(0, 1, 2, 3);
    }

    @ParameterizedTest
    @CsvSource({"NaN", "Infinity", "-Infinity", "100"})
    @EnabledIfSystemProperty(named = "mtl.test.simplification.jdbc-url", matches = "jdbc:postgresql:.*")
    void databaseProjectionIgnoresElevationAndRestoresReducedCanonicalMetadata(double firstElevation) throws Exception {
        LineString canonical = line(new CoordinateXYZM(10, 20, firstElevation, 0),
                new CoordinateXYZM(10.0001, 20, 110, 10), new CoordinateXYZM(10.0002, 20, 120, 20));
        String repositoryQuery = SimplifiedTrackRepository.class
                .getMethod("getTrackSimplified", BigDecimal.class, Long.class)
                .getAnnotation(Query.class).value().replace(":tolerance", "?").replace(":id", "?");
        // CTEs shadow the source tables without reading or changing application data.
        String sql = """
                             WITH gps_track(id) AS (VALUES (?::bigint)),
                                  gps_track_data(id, gps_track_id, track_type, track) AS (
                                      VALUES (?::bigint, ?::bigint, 'RAW_OUTLIER_CLEANED', ST_SetSRID(ST_MakeLine(ARRAY[
                                          ST_MakePoint(10, 20, ?::double precision, 0),
                                          ST_MakePoint(10.0001, 20, 110, 10),
                                          ST_MakePoint(10.0002, 20, 120, 20)]), 4326)))
                             SELECT ST_AsEWKB(line_string) FROM (
                             """ + repositoryQuery + ") simplified";
        LineString reduced;
        try (var connection = DriverManager.getConnection(
                System.getProperty("mtl.test.simplification.jdbc-url"),
                System.getProperty("mtl.test.simplification.jdbc-user"),
                System.getProperty("mtl.test.simplification.jdbc-password"))) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, TRACK_ID);
                statement.setLong(2, TRACK_ID);
                statement.setLong(3, TRACK_ID);
                statement.setDouble(4, firstElevation);
                statement.setBigDecimal(5, GpsTrackData.PRECISION_10M);
                statement.setLong(6, TRACK_ID);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    // Use Hibernate Spatial's geometry conversion, including M.
                    reduced = (LineString) JTS.to(Wkb.fromWkb(ByteBuffer.from(rows.getBytes(1))));
                }
            }
        }
        assertThat(reduced.getNumPoints()).isEqualTo(2);
        assertThat(reduced.getCoordinateN(0).getX()).isEqualTo(10);
        assertThat(reduced.getCoordinateN(0).getY()).isEqualTo(20);
        stubShape(reduced, GpsTrackData.builder().track(canonical).build());

        calculateSimplified();

        LineString saved = savedShape().getTrack();
        assertThat(saved.getNumPoints()).isEqualTo(2);
        assertThat(Double.doubleToLongBits(saved.getCoordinateN(0).getZ()))
                .isEqualTo(Double.doubleToLongBits(firstElevation));
        assertThat(saved.getCoordinateN(1).getZ()).isEqualTo(120);
        assertThat(saved.getCoordinateN(0).getM()).isZero();
        assertThat(saved.getCoordinateN(1).getM()).isEqualTo(20);
        assertCanonicalPointRows(0, 2);
    }

    @Test
    void invalidSimplificationWithoutCanonicalGeometrySkipsOnlyTheDisplayShape() {
        stubShape(line(new CoordinateXYZM(Double.NaN, 20, 100, 0),
                new CoordinateXYZM(10.0003, 20, 100, 80)), null);

        assertDoesNotThrow(this::calculateSimplified);

        verify(trackData, never()).save(any());
        verifyNoInteractions(points);
    }

    @Test
    void invalidCanonicalGeometryIsNeverUsedAsTheFallback() {
        LineString invalid = line(new CoordinateXYZM(Double.NaN, 20, 100, 0),
                new CoordinateXYZM(10.0003, 20, 100, 80));
        stubShape(invalid, GpsTrackData.builder().track(invalid).build());

        assertDoesNotThrow(this::calculateSimplified);

        verify(trackData, never()).save(any());
        verifyNoInteractions(points);
    }

    private void calculateSimplified() {
        ReflectionTestUtils.invokeMethod(store, "calculateSimplified", TRACK_ID, GpsTrackData.PRECISION_10M);
    }

    private void stubShape(LineString line, GpsTrackData cleaned) {
        SimplifiedTrack shape = new SimplifiedTrack();
        shape.setLineString(line);
        when(shapes.getTrackSimplified(GpsTrackData.PRECISION_10M, TRACK_ID)).thenReturn(shape);
        when(trackData.findFirstByGpsTrackIdAndTrackType(TRACK_ID, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()))
                .thenReturn(cleaned);
        when(trackData.save(any(GpsTrackData.class))).thenAnswer(invocation -> {
            GpsTrackData data = invocation.getArgument(0);
            data.setId(201L);
            return data;
        });
    }

    private GpsTrackData savedShape() {
        ArgumentCaptor<GpsTrackData> saved = ArgumentCaptor.forClass(GpsTrackData.class);
        verify(trackData).save(saved.capture());
        return saved.getValue();
    }

    private void assertCanonicalPointRows(Integer... indexes) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<GpsTrackDataPoint>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(points).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(GpsTrackDataPoint::getCanonicalPointIndex).containsExactly(indexes);
        assertThat(saved.getValue()).allSatisfy(point -> {
            assertThat(Double.isFinite(point.getPointLongLat().getX())).isTrue();
            assertThat(Double.isFinite(point.getPointLongLat().getY())).isTrue();
            assertThat(point.getPointTimestamp()).isNotNull();
        });
    }

    private static LineString canonicalTrack() {
        return line(new CoordinateXYZM(10, 20, 100, 0), new CoordinateXYZM(10.0001, 20, 100, 10),
                new CoordinateXYZM(10.0002, 20, 100, 70), new CoordinateXYZM(10.0003, 20, 100, 80));
    }

    private static LineString line(Coordinate... coordinates) {
        return GEOMETRY_FACTORY.createLineString(coordinates);
    }
}
