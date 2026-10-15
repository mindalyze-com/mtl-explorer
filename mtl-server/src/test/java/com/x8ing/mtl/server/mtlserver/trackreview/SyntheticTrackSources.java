package com.x8ing.mtl.server.mtlserver.trackreview;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fully invented observations; no coordinates, timestamps, or samples are derived from private GPX files.
 */
public final class SyntheticTrackSources {
    public static final Instant START = Instant.parse("2032-02-18T08:00:00Z");

    private SyntheticTrackSources() {
    }

    public static List<Instant> regular(int count) {
        List<Instant> times = new ArrayList<>();
        for (int i = 0; i < count; i++) times.add(START.plusSeconds(i * 5L));
        return times;
    }

    public static byte[] source(List<Instant> times) {
        return document(section(times, 0));
    }

    public static byte[] document(String... sections) {
        return ("<gpx version=\"1.1\" creator=\"MTL Explorer synthetic tests\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:example=\"urn:synthetic:sensor\">"
                + "<metadata><name>Synthetic recording</name><desc>Independent test observations</desc></metadata>"
                + "<trk><name>Synthetic route</name>" + String.join("", sections) + "</trk></gpx>").getBytes(StandardCharsets.UTF_8);
    }

    public static String section(List<Instant> times, int positionOffset) {
        StringBuilder result = new StringBuilder("<trkseg>");
        for (int i = 0; i < times.size(); i++) {
            result.append(String.format(Locale.ROOT, "<trkpt lat=\"%.7f\" lon=\"%.7f\"><ele>120.5</ele>",
                    60.0 + (i + positionOffset) * 0.000025, 24.0 + (i + positionOffset) * 0.00005));
            if (times.get(i) != null) result.append("<time>").append(times.get(i)).append("</time>");
            result.append("<extensions><example:sample index=\"").append(i).append("\">preserve me</example:sample></extensions></trkpt>");
        }
        return result.append("</trkseg>").toString();
    }

    public static String estimatedSection(List<Instant> times, int positionOffset) {
        return section(times, positionOffset).replace("<extensions>",
                "<extensions><e:time xmlns:e=\"https://mytraillog.com/xmlschemas/repair/v1\"/>");
    }

    public static byte[] alternatingClockDates() {
        List<Instant> times = regular(600);
        for (int i = 150; i < 450; i += 3) times.set(i, times.get(i).minusSeconds(9 * 86400L));
        for (int i = 170; i < 430; i += 11) times.set(i, null);
        return source(times);
    }
}
