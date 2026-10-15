package com.x8ing.mtl.server.mtlserver.gpx;

import io.jenetics.jpx.GPX;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TrackConversionTimeoutTest {
    private static final int POINTS = 5_000;
    private static final long SYNTHETIC_CONVERSION_MILLIS = 90_000;

    @TempDir
    private Path directory;

    @Test
    void backgroundConversionCanExceedOneMinuteAndPreservesTheWholeSyntheticTrack() throws Exception {
        Path input = directory.resolve("synthetic.tcx");
        byte[] original = "synthetic original source".getBytes(StandardCharsets.UTF_8);
        Files.write(input, original);
        String xml = syntheticGpx();
        Process process = process(xml);
        when(process.waitFor(anyLong(), any())).thenAnswer(call ->
                call.getArgument(1, TimeUnit.class).toMillis(call.getArgument(0, Long.class)) >= SYNTHETIC_CONVERSION_MILLIS);
        try (var construction = builder(process)) {
            var converter = new TrackFileConverterService();
            String output = converter.convertToGpxForImport(input, SupportedTrackFormat.TCX);

            assertThat(output).isEqualTo(xml);
            var points = GPX.Reader.DEFAULT.read(new StreamSource(new StringReader(output))).getTracks().getFirst()
                    .getSegments().getFirst().getPoints();
            assertThat(points).hasSize(POINTS);
            for (int i = 0; i < POINTS; i++) {
                assertThat(points.get(i).getLatitude().doubleValue()).isEqualTo(47.0 + i * 0.000001);
                assertThat(points.get(i).getLongitude().doubleValue()).isEqualTo(8.0 + i * 0.000001);
                assertThat(points.get(i).getElevation().orElseThrow().doubleValue()).isEqualTo(500.0 + i % 100);
                assertThat(points.get(i).getTime().orElseThrow()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
            }
            verify(process).waitFor(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
            verify(process, never()).destroyForcibly();
            assertThat(Files.readAllBytes(input)).isEqualTo(original);
        }
    }

    @Test
    void interactiveConversionKeepsItsOneMinuteLimit() throws Exception {
        Process process = process(syntheticGpx());
        when(process.waitFor(anyLong(), any())).thenReturn(false);
        try (var construction = builder(process)) {
            assertThatThrownBy(() -> new TrackFileConverterService().convertToGpx(directory.resolve("source.tcx"), SupportedTrackFormat.TCX))
                    .isInstanceOf(IOException.class).hasMessageContaining("timed out");
            verify(process).waitFor(Duration.ofMinutes(1).toMillis(), TimeUnit.MILLISECONDS);
            verify(process).destroyForcibly();
        }
    }

    @Test
    void configuredBackgroundLimitIsUsedAndTimeoutReturnsNoPartialResult() throws Exception {
        var converter = new TrackFileConverterService();
        ReflectionTestUtils.setField(converter, "backgroundConversionTimeout", Duration.ofMinutes(20));
        converter.validateTimeout();
        Process process = process("<gpx><trk>");
        when(process.waitFor(anyLong(), any())).thenReturn(false);
        try (var construction = builder(process)) {
            assertThatThrownBy(() -> converter.convertToGpxForImport(directory.resolve("source.fit"), SupportedTrackFormat.FIT))
                    .isInstanceOf(IOException.class).hasMessageContaining("PT20M");
            verify(process).waitFor(Duration.ofMinutes(20).toMillis(), TimeUnit.MILLISECONDS);
            verify(process).destroyForcibly();
        }
    }

    @Test
    void cancellationStillTerminatesTheProcessAndRestoresInterruption() throws Exception {
        Process process = process("<gpx/>");
        when(process.waitFor(anyLong(), any())).thenThrow(new InterruptedException("Synthetic cancellation"));
        try (var construction = builder(process)) {
            assertThatThrownBy(() -> new TrackFileConverterService().convertToGpxForImport(directory.resolve("source.fit"), SupportedTrackFormat.FIT))
                    .isInstanceOf(IOException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(process).destroyForcibly();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidBackgroundLimitsFailValidation() {
        var converter = new TrackFileConverterService();
        for (Duration timeout : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1)}) {
            ReflectionTestUtils.setField(converter, "backgroundConversionTimeout", timeout);
            assertThatThrownBy(converter::validateTimeout).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Process process(String output) {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.exitValue()).thenReturn(0);
        return process;
    }

    private static MockedConstruction<ProcessBuilder> builder(Process process) {
        return mockConstruction(ProcessBuilder.class, (mock, context) -> {
            when(mock.redirectErrorStream(false)).thenReturn(mock);
            when(mock.start()).thenReturn(process);
        });
    }

    private static String syntheticGpx() {
        var xml = new StringBuilder("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"synthetic\"><trk><trkseg>");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < POINTS; i++) {
            xml.append("<trkpt lat=\"").append(47.0 + i * 0.000001).append("\" lon=\"").append(8.0 + i * 0.000001)
                    .append("\"><ele>").append(500.0 + i % 100).append("</ele><time>").append(start.plusSeconds(i)).append("</time></trkpt>");
        }
        return xml.append("</trkseg></trk></gpx>").toString();
    }
}
