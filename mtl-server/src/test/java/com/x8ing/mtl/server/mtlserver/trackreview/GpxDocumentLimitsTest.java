package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GpxDocumentLimitsTest {
    @TempDir
    Path directory;

    @Test
    void aSourceWithinByteAndPointLimitsCannotExhaustTheShippedHeapDuringAssessment() throws Exception {
        Path source = directory.resolve("synthetic-large.gpx");
        int pointCount = 650_000;
        try (var writer = Files.newBufferedWriter(source)) {
            writer.write("<gpx><trk><trkseg>");
            String point = "<trkpt lat=\"47.123456\" lon=\"8.123456\"><ele>500</ele><time>2026-01-01T00:00:00Z</time></trkpt>\n";
            for (int i = 0; i < pointCount; i++) writer.write(point);
            writer.write("</trkseg></trk></gpx>");
        }
        assertTrue(Files.size(source) < GpxDocument.MAX_SOURCE_BYTES);
        assertTrue(pointCount < GpxDocument.MAX_POINTS);
        Path log = directory.resolve("heap-probe.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx320m", "-XX:+ExitOnOutOfMemoryError",
                "--module-path", System.getProperty("jdk.module.path"),
                "--add-modules", GpxDocument.class.getModule().getName(),
                "-cp", System.getProperty("java.class.path"), HeapProbe.class.getName(), source.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Heap probe timed out");
            assertEquals(0, process.exitValue(), () -> read(log));
            assertTrue(read(log).contains("FULL_ASSESSMENT; small source still readable"), () -> read(log));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void deviceExtensionNodesCommentsAndTextDoNotUseObservationStorage() {
        for (String content : new String[]{"<extra/>".repeat(140_000), "<!--x-->".repeat(360_000), "<![CDATA[]]>".repeat(360_000),
                "x".repeat(17 * 1024 * 1024)}) {
            var document = assertDoesNotThrow(() -> GpxDocument.parse("<gpx><extensions>" + content + "</extensions></gpx>"));
            assertTrue(document.points().isEmpty());
        }
    }

    @Test
    void excessiveNestingAndRetainedFieldTextRemainBounded() {
        assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(
                "<gpx><extensions>" + "<extra>".repeat(70) + "</extra>".repeat(70) + "</extensions></gpx>"));
        assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(
                "<gpx><trk><name>" + "x".repeat(64 * 1024 + 1) + "</name></trk></gpx>"));
    }

    @Test
    void byteAndPointLimitsRemainEnforced() {
        assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(new byte[GpxDocument.MAX_SOURCE_BYTES + 1]));
        assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(
                "<gpx><trk><trkseg>" + "<trkpt/>".repeat(GpxDocument.MAX_POINTS + 1) + "</trkseg></trk></gpx>"));
    }

    @Test
    void excessiveRetainedParseProblemsStillHaveAnExplicitReviewLimit() {
        String point = "<trkpt lat='NaN' lon='NaN'><ele>NaN</ele><time>invalid timestamp</time></trkpt>";
        String xml = "<gpx><trk><trkseg>" + point.repeat(500_000) + "</trkseg></trk></gpx>";
        var limit = assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(xml));
        assertTrue(limit.getMessage().contains("observation storage budget"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot read heap probe result", e);
        }
    }

    public static class HeapProbe {
        public static void main(String[] args) throws Exception {
            var document = GpxDocument.parse(Files.readAllBytes(Path.of(args[0])));
            var diagnosis = new TrackDiagnosticEngine().diagnose(document);
            if (diagnosis.pointCount() != 650_000 || diagnosis.sections().getFirst().timedPointCount() != 650_000)
                throw new AssertionError("Large source assessment was incomplete");
            if (GpxDocument.parse("<gpx><trk><trkseg><trkpt lat='47' lon='8'/></trkseg></trk></gpx>").points().size() != 1)
                throw new AssertionError("Small source parsing failed");
            System.out.println("FULL_ASSESSMENT; small source still readable");
        }
    }
}
