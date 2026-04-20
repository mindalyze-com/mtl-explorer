package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DuplicateComparatorTest {

    @Test
    public void testSorting() {

        List<GpsTrack> gpsTracks = new ArrayList<>();

        gpsTracks.add(generateGpsTrack(0, 100, null, "myTrack1", "desc1", null, null));

        gpsTracks.add(generateGpsTrack(1, 90, null, "myTrack2", "desc2", null, null));
        gpsTracks.add(generateGpsTrack(2, 90, null, null, "desc3", null, null));
        gpsTracks.add(generateGpsTrack(3, 90, null, null, null, null, null));
        gpsTracks.add(generateGpsTrack(4, 90, 1L, null, null, null, null));

        gpsTracks.add(generateGpsTrack(10, 50, null, "myTrack3", "desc10", null, null));

        gpsTracks.sort(new DuplicateComparator());

        assertEquals(0, gpsTracks.get(0).getId());
        assertEquals(1, gpsTracks.get(1).getId());
        assertEquals(2, gpsTracks.get(2).getId());
        assertEquals(3, gpsTracks.get(3).getId());
        assertEquals(4, gpsTracks.get(4).getId());

        assertEquals(10, gpsTracks.getLast().getId());
    }

    @Test
    public void testSortingWithCreator() {
        // Mirrors the real-world case: GPSBabel-converted copies vs. original Garmin Connect track,
        // all with identical numberOfTrackPoints and no description.
        List<GpsTrack> gpsTracks = new ArrayList<>();

        // Track A & C: GPSBabel-created copy — converter tool, GPX 1.0, no name, no activityType
        GpsTrack trackA = generateGpsTrack(100759, 17481, null, null, null,
                "GPSBabel - https://www.gpsbabel.org", "1.0");
        GpsTrack trackC = generateGpsTrack(101965, 17481, null, null, null,
                "GPSBabel - https://www.gpsbabel.org", "1.0");

        // Track B: Garmin Connect original — known source app, GPX 1.1, has name + activityType
        GpsTrack trackB = generateGpsTrack(101343, 17481, null, "Glattfelden Mountain Biking", null,
                "Garmin Connect", "1.1");
        trackB.setActivityType(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);

        gpsTracks.add(trackA);
        gpsTracks.add(trackB);
        gpsTracks.add(trackC);

        gpsTracks.sort(new DuplicateComparator());

        // Garmin Connect track must be the first (UNIQUE winner)
        assertEquals(101343, gpsTracks.get(0).getId(), "Garmin Connect track should be the UNIQUE winner");
        // GPSBabel tracks follow — their relative order may vary but they must not be first
        assertNotEquals(101343, gpsTracks.get(1).getId());
        assertNotEquals(101343, gpsTracks.get(2).getId());
    }

    @Test
    public void testIsConverterTool() {
        assertTrue(DuplicateComparator.isConverterTool("GPSBabel - https://www.gpsbabel.org"));
        assertTrue(DuplicateComparator.isConverterTool("gpsbabel 1.7"));
        assertFalse(DuplicateComparator.isConverterTool("Garmin Connect"));
        assertFalse(DuplicateComparator.isConverterTool(null));
        assertFalse(DuplicateComparator.isConverterTool(""));
    }

    @Test
    public void testIsKnownSourceApp() {
        assertTrue(DuplicateComparator.isKnownSourceApp("Garmin Connect"));
        assertTrue(DuplicateComparator.isKnownSourceApp("Strava"));
        assertTrue(DuplicateComparator.isKnownSourceApp("Komoot"));
        assertTrue(DuplicateComparator.isKnownSourceApp("Wahoo ELEMNT"));
        assertTrue(DuplicateComparator.isKnownSourceApp("Polar Flow"));
        assertFalse(DuplicateComparator.isKnownSourceApp("GPSBabel - https://www.gpsbabel.org"));
        assertFalse(DuplicateComparator.isKnownSourceApp(null));
    }

    @Test
    public void testGpxVersionScore() {
        assertEquals(1.1, DuplicateComparator.gpxVersionScore("1.1"), 0.001);
        assertEquals(1.0, DuplicateComparator.gpxVersionScore("1.0"), 0.001);
        assertEquals(0.0, DuplicateComparator.gpxVersionScore(null), 0.001);
        assertEquals(0.0, DuplicateComparator.gpxVersionScore(""), 0.001);
        assertEquals(0.0, DuplicateComparator.gpxVersionScore("unknown"), 0.001);
    }

    private static GpsTrack generateGpsTrack(long id, int numberOfTrackPoints, Long duplicateOf,
                                             String trackName, String trackDescription,
                                             String creator, String gpxVersion) {
        GpsTrack t1 = new GpsTrack();
        t1.setId(id);
        t1.setNumberOfTrackPoints(numberOfTrackPoints);
        t1.setDuplicateOf(duplicateOf);
        t1.setTrackName(trackName);
        t1.setTrackDescription(trackDescription);
        t1.setCreator(creator);
        t1.setGpxVersion(gpxVersion);
        return t1;
    }
}