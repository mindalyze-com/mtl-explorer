package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PublicGpxRegressionDeckTest {

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/gpx/public-regression/viewmygpx");
    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";
    private static final double EARTH_RADIUS_M = 6_371_008.8;
    private static final double SOURCE_LENGTH_TOLERANCE_RATIO = 0.03;
    private static final double IMPORTED_LENGTH_TOLERANCE_RATIO = 0.08;
    private static final double MIN_CLEANED_POINT_RETENTION_RATIO = 0.95;
    private static final double MAX_IMPORTED_ADJACENT_DISTANCE_M = 500.0;

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void sourceAudit_matchesPublicFixtureManifest(Fixture fixture) throws Exception {
        SourceAudit audit = SourceAudit.from(fixture.path());

        assertEquals(fixture.expectedTracks(), audit.tracks(), "source track count changed");
        assertEquals(fixture.expectedSegments(), audit.segments(), "source segment count changed");
        assertEquals(fixture.expectedTrackPoints(), audit.trackPoints(), "source trackpoint count changed");
        assertEquals(fixture.expectedWaypoints(), audit.waypoints(), "source waypoint count changed");
        assertEquals(fixture.expectedLengthMeters(), audit.lengthMeters(),
                fixture.expectedLengthMeters() * SOURCE_LENGTH_TOLERANCE_RATIO,
                "source length changed materially");
        assertTrue(audit.maxAdjacentDistanceMeters() <= fixture.maxSourceAdjacentDistanceMeters(),
                () -> "source has an unexpected adjacent jump: " + audit.maxAdjacentDistanceMeters());
        assertEquals(0, audit.negativeTimeDeltas(), "source timestamps must be monotonic inside each segment");
        assertTrue(audit.end().compareTo(audit.start()) > 0, "source must have a positive time domain");

        if (fixture.zeroElevation()) {
            assertEquals(0.0, audit.minElevation(), 0.001);
            assertEquals(0.0, audit.maxElevation(), 0.001);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void importPipeline_preservesPublicFixtureShapeAndTimeDomain(Fixture fixture) throws Exception {
        GPXReader reader = new GPXReader();
        List<GPXReader.LoadResult> results = reader.importGpxFile(indexedFileFor(fixture));

        assertEquals(fixture.expectedImportedTracks(), results.size(),
                "normal public fixtures must not explode into many pseudo-tracks");

        int totalCleanedPoints = 0;
        double totalCleanedLength = 0.0;
        for (GPXReader.LoadResult result : results) {
            assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
            assertNotNull(result.trackRAW, "RAW geometry must be present");
            assertNotNull(result.trackCleaned, "RAW_OUTLIER_CLEANED geometry must be present");
            assertTrue(result.trackCleaned.getNumPoints() >= fixture.minImportedTrackPoints(),
                    "imported track should be substantial, not a one-point fragment");
            assertFalse(Boolean.TRUE.equals(result.gpsTrack.getDidFilterOutlierByDistance()),
                    "clean public fixture should not need outlier filtering");
            assertMonotonicTime(result.trackCleaned);
            assertImportedTrackDates(result.gpsTrack);

            double maxAdjacentDistance = maxAdjacentDistanceMeters(result.trackCleaned);
            assertTrue(maxAdjacentDistance <= MAX_IMPORTED_ADJACENT_DISTANCE_M,
                    () -> String.format(Locale.ROOT,
                            "import invented an adjacent %.1f m jump in %s",
                            maxAdjacentDistance,
                            fixture.fileName()));

            totalCleanedPoints += result.trackCleaned.getNumPoints();
            totalCleanedLength += GPXReader.getDistanceOfWGS84(result.trackCleaned);
        }

        assertTrue(totalCleanedPoints >= fixture.expectedTrackPoints() * MIN_CLEANED_POINT_RETENTION_RATIO,
                "clean public fixture lost too many points during import");
        assertEquals(fixture.expectedLengthMeters(), totalCleanedLength,
                fixture.expectedLengthMeters() * IMPORTED_LENGTH_TOLERANCE_RATIO,
                "imported cleaned geometry length drifted too far from the audited source");
    }

    @Test
    void publicRegressionDeck_doesNotUsePrivateTrackDirectory() {
        assertFalse(FIXTURE_DIR.toAbsolutePath().normalize().toString().contains("/Downloads/"));
    }

    private static Stream<Fixture> fixtures() {
        return Stream.of(
                new Fixture("short-hike-5km.gpx", 1, 1, 720, 2, 5_057.1, 1, 500, 20.0, false),
                new Fixture("mountain-hike.gpx", 1, 1, 1_800, 3, 14_808.9, 1, 1_500, 25.0, false),
                new Fixture("road-cycling-50km.gpx", 1, 1, 2_400, 0, 49_990.8, 1, 2_000, 50.0, false),
                new Fixture("multi-day-hike.gpx", 4, 4, 2_400, 5, 101_259.4, 4, 500, 100.0, false),
                new Fixture("sailing-route.gpx", 1, 1, 2_000, 3, 50_258.3, 1, 1_500, 50.0, true),
                new Fixture("extensions-test.gpx", 1, 1, 1_200, 0, 38_895.6, 1, 1_000, 50.0, false)
        );
    }

    private static IndexedFile indexedFileFor(Fixture fixture) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setIndex("public-gpx-regression");
        indexedFile.setName(fixture.fileName());
        indexedFile.setPath(FIXTURE_DIR.toString());
        indexedFile.setFullPath(fixture.path().toString());
        return indexedFile;
    }

    private static void assertMonotonicTime(LineString lineString) {
        double previous = Double.NaN;
        for (Coordinate coordinate : lineString.getCoordinates()) {
            double current = coordinate.getM();
            if (!Double.isNaN(previous) && !Double.isNaN(current)) {
                assertTrue(current >= previous,
                        "imported time domain moved backwards from " + previous + " to " + current);
            }
            previous = current;
        }
    }

    private static void assertImportedTrackDates(GpsTrack track) {
        assertNotNull(track.getStartDate(), "track start date must be present");
        assertNotNull(track.getEndDate(), "track end date must be present");
        assertFalse(track.getEndDate().before(track.getStartDate()), "track date range must be non-negative");
    }

    private static double maxAdjacentDistanceMeters(LineString lineString) {
        double max = 0.0;
        for (int i = 1; i < lineString.getNumPoints(); i++) {
            max = Math.max(max, GPXReader.getDistanceBetweenTwoWGS84(
                    lineString.getCoordinateN(i - 1),
                    lineString.getCoordinateN(i)));
        }
        return max;
    }

    private record Fixture(String fileName,
                           int expectedTracks,
                           int expectedSegments,
                           int expectedTrackPoints,
                           int expectedWaypoints,
                           double expectedLengthMeters,
                           int expectedImportedTracks,
                           int minImportedTrackPoints,
                           double maxSourceAdjacentDistanceMeters,
                           boolean zeroElevation) {

        Path path() {
            return FIXTURE_DIR.resolve(fileName);
        }

        @Override
        public String toString() {
            return fileName;
        }
    }

    private record SourceAudit(int tracks,
                               int segments,
                               int trackPoints,
                               int waypoints,
                               double lengthMeters,
                               double maxAdjacentDistanceMeters,
                               int negativeTimeDeltas,
                               Instant start,
                               Instant end,
                               double minElevation,
                               double maxElevation) {

        static SourceAudit from(Path path) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(path.toFile());
            Element root = document.getDocumentElement();

            NodeList tracks = root.getElementsByTagNameNS(GPX_NS, "trk");
            NodeList segments = root.getElementsByTagNameNS(GPX_NS, "trkseg");
            NodeList trackPoints = root.getElementsByTagNameNS(GPX_NS, "trkpt");
            NodeList waypoints = root.getElementsByTagNameNS(GPX_NS, "wpt");

            double lengthMeters = 0.0;
            double maxAdjacentDistanceMeters = 0.0;
            int negativeTimeDeltas = 0;
            Instant start = null;
            Instant end = null;
            double minElevation = Double.POSITIVE_INFINITY;
            double maxElevation = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < segments.getLength(); i++) {
                Element segment = (Element) segments.item(i);
                NodeList points = segment.getElementsByTagNameNS(GPX_NS, "trkpt");
                TrackPoint previous = null;
                for (int j = 0; j < points.getLength(); j++) {
                    Element point = (Element) points.item(j);
                    TrackPoint current = TrackPoint.from(point);
                    if (current.time() != null) {
                        start = start == null || current.time().isBefore(start) ? current.time() : start;
                        end = end == null || current.time().isAfter(end) ? current.time() : end;
                    }
                    if (!Double.isNaN(current.elevation())) {
                        minElevation = Math.min(minElevation, current.elevation());
                        maxElevation = Math.max(maxElevation, current.elevation());
                    }
                    if (previous != null) {
                        double distance = haversineMeters(previous.latitude(), previous.longitude(),
                                current.latitude(), current.longitude());
                        lengthMeters += distance;
                        maxAdjacentDistanceMeters = Math.max(maxAdjacentDistanceMeters, distance);
                        if (previous.time() != null && current.time() != null && current.time().isBefore(previous.time())) {
                            negativeTimeDeltas++;
                        }
                    }
                    previous = current;
                }
            }

            return new SourceAudit(
                    tracks.getLength(),
                    segments.getLength(),
                    trackPoints.getLength(),
                    waypoints.getLength(),
                    lengthMeters,
                    maxAdjacentDistanceMeters,
                    negativeTimeDeltas,
                    start,
                    end,
                    minElevation,
                    maxElevation);
        }
    }

    private record TrackPoint(double latitude, double longitude, double elevation, Instant time) {

        static TrackPoint from(Element point) {
            double latitude = Double.parseDouble(point.getAttribute("lat"));
            double longitude = Double.parseDouble(point.getAttribute("lon"));
            String elevationText = childText(point, "ele");
            String timeText = childText(point, "time");
            return new TrackPoint(
                    latitude,
                    longitude,
                    elevationText == null ? Double.NaN : Double.parseDouble(elevationText),
                    timeText == null ? null : Instant.parse(timeText));
        }
    }

    private static String childText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS(GPX_NS, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
                   + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                     * Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);
        return EARTH_RADIUS_M * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }
}
