package com.x8ing.mtl.server.mtlserver.web.services.track;

import tools.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeQuality;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class VideoTranscodeSessionServiceTest {

    private static final long MEBIBYTE = 1024L * 1024L;
    private static final Pattern SEGMENT_REFERENCE = Pattern.compile("(segment-[0-9]{6}\\.m4s)");

    @TempDir
    Path tempDirectory;

    private VideoTranscodeSessionService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void deduplicatesActiveSessionsEnforcesConcurrencyAndDeletesOnCancel() throws Exception {
        TestCommands commands = testCommands(true);
        service = newService(commands, Clock.systemUTC());
        Path firstSource = source("first.mov");
        Path secondSource = source("second.mov");

        VideoTranscodeSessionService.CreateResult first = service.create(1L, firstSource, VideoTranscodeQuality.P480);
        VideoTranscodeSessionService.CreateResult reused = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> service.create(1L, firstSource, VideoTranscodeQuality.P480));

        assertThat(reused.reused()).isTrue();
        assertThat(reused.session().sessionId()).isEqualTo(first.session().sessionId());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThatThrownBy(() -> service.create(2L, secondSource, VideoTranscodeQuality.P480))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)));

        UUID firstSessionId = UUID.fromString(first.session().sessionId());
        awaitStatus(firstSessionId, status -> status.playlistReady(), Duration.ofSeconds(5));
        service.cancel(firstSessionId);

        assertThat(service.get(firstSessionId).state()).isEqualTo(VideoTranscodeSessionState.CANCELLED);
        assertThatThrownBy(() -> service.getOutput(firstSessionId, VideoTranscodeSessionService.PLAYLIST_FILE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE));

        VideoTranscodeSessionService.CreateResult second = service.create(2L, secondSource, VideoTranscodeQuality.P480);
        assertThat(second.session().sessionId()).isNotEqualTo(first.session().sessionId());
    }

    @Test
    void returnsTooManyRequestsWhenTheSharedProcessSlotIsBusy() throws Exception {
        TestCommands commands = testCommands(false);
        MediaProcessProperties mediaProcessProperties = new MediaProcessProperties();
        mediaProcessProperties.setAcquireTimeout(Duration.ZERO);
        MediaProcessLimiter limiter = new MediaProcessLimiter(mediaProcessProperties);
        service = new VideoTranscodeSessionService(
                properties(commands),
                new ObjectMapper(),
                limiter,
                Clock.systemUTC());
        service.initialize();

        try (MediaProcessLimiter.Permit ignored = limiter.acquire()) {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThatThrownBy(() -> service.create(
                            1L, source("busy.mov"), VideoTranscodeQuality.P480))
                            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                                assertThat(error.getReason()).contains("busy");
                            }));
        }
    }

    @Test
    void startupRemovesOrphansAndDisconnectGraceStopsAnActiveJob() throws Exception {
        TestCommands commands = testCommands(true);
        Path transcodeRoot = tempDirectory.resolve("sessions");
        Path orphan = transcodeRoot.resolve("orphan");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("old.m4s"), "old");
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T10:00:00Z"));

        service = newService(commands, clock);

        assertThat(orphan).doesNotExist();
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("disconnect.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.playlistReady(), Duration.ofSeconds(5));

        clock.advance(Duration.ofMinutes(6));
        service.enforceLimitsNow();

        VideoTranscodeSessionDto expired = service.get(sessionId);
        assertThat(expired.state()).isEqualTo(VideoTranscodeSessionState.FAILED);
        assertThat(expired.message()).contains("disconnected");
        assertThat(transcodeRoot.resolve(sessionId.toString())).doesNotExist();
    }

    @Test
    void runtimeLimitStopsAndCleansAnActiveJob() throws Exception {
        TestCommands commands = testCommands(true);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T10:00:00Z"));
        VideoTranscodeProperties properties = properties(commands);
        properties.setDisconnectGrace(Duration.ofHours(2));
        properties.setMaxRuntime(Duration.ofHours(1));
        service = initializedService(properties, clock);
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("runtime.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.playlistReady(), Duration.ofSeconds(5));

        clock.advance(Duration.ofMinutes(61));
        service.enforceLimitsNow();

        VideoTranscodeSessionDto stopped = service.get(sessionId);
        assertThat(stopped.state()).isEqualTo(VideoTranscodeSessionState.FAILED);
        assertThat(stopped.message()).contains("runtime limit");
        assertThat(tempDirectory.resolve("sessions").resolve(sessionId.toString())).doesNotExist();
    }

    @Test
    void actualByteLimitStopsAndCleansAnActiveJob() throws Exception {
        TestCommands commands = testCommands(true);
        VideoTranscodeProperties properties = properties(commands);
        service = initializedService(properties, Clock.systemUTC());
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("byte-limit.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.playlistReady(), Duration.ofSeconds(5));

        properties.setMaxSessionBytes(1L);
        service.enforceLimitsNow();

        VideoTranscodeSessionDto stopped = service.get(sessionId);
        assertThat(stopped.state()).isEqualTo(VideoTranscodeSessionState.FAILED);
        assertThat(stopped.message()).contains("size limit");
        assertThat(tempDirectory.resolve("sessions").resolve(sessionId.toString())).doesNotExist();
    }

    @Test
    void globalActualByteLimitStopsAnActiveJobWhenNothingCompletedCanBeEvicted() throws Exception {
        TestCommands commands = testCommands(true);
        VideoTranscodeProperties properties = properties(commands);
        service = initializedService(properties, Clock.systemUTC());
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("global-byte-limit.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.playlistReady(), Duration.ofSeconds(5));

        properties.setMaxTotalBytes(1L);
        service.enforceLimitsNow();

        VideoTranscodeSessionDto stopped = service.get(sessionId);
        assertThat(stopped.state()).isEqualTo(VideoTranscodeSessionState.FAILED);
        assertThat(stopped.message()).contains("global temporary video size limit");
        assertThat(tempDirectory.resolve("sessions").resolve(sessionId.toString())).doesNotExist();
    }

    @Test
    void completedSessionExpiresAfterItsIdleTtl() throws Exception {
        TestCommands commands = testCommands(false);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T10:00:00Z"));
        service = newService(commands, clock);
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("completed-expiry.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.state() == VideoTranscodeSessionState.COMPLETED, Duration.ofSeconds(5));

        clock.advance(Duration.ofMinutes(31));
        service.enforceLimitsNow();

        assertThatThrownBy(() -> service.get(sessionId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(tempDirectory.resolve("sessions")).isEmptyDirectory();
    }

    @Test
    void retainsOnlyOneCompletedSession() throws Exception {
        TestCommands commands = testCommands(false);
        service = newService(commands, Clock.systemUTC());

        VideoTranscodeSessionService.CreateResult first = service.create(
                1L, source("first-complete.mov"), VideoTranscodeQuality.P480);
        UUID firstId = UUID.fromString(first.session().sessionId());
        awaitStatus(firstId, status -> status.state() == VideoTranscodeSessionState.COMPLETED, Duration.ofSeconds(5));

        VideoTranscodeSessionService.CreateResult second = service.create(
                2L, source("second-complete.mov"), VideoTranscodeQuality.P480);
        UUID secondId = UUID.fromString(second.session().sessionId());
        awaitStatus(secondId, status -> status.state() == VideoTranscodeSessionState.COMPLETED, Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.get(firstId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.get(secondId).playlistReady()).isTrue();
    }

    @Test
    void rejectsAProfileWhoseEstimatedOutputExceedsTheSessionQuota() throws Exception {
        TestCommands commands = testCommands(false);
        VideoTranscodeProperties properties = properties(commands);
        properties.setMaxSessionBytes(11L * MEBIBYTE);
        properties.setMaxTotalBytes(32L * MEBIBYTE);
        service = initializedService(properties, Clock.systemUTC());

        assertThatThrownBy(() -> service.create(1L, source("too-large.mov"), VideoTranscodeQuality.P480))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(error.getReason()).contains("lower quality");
                });
        assertThat(tempDirectory.resolve("sessions")).isEmptyDirectory();
    }

    @Test
    void evictsCompletedOutputBeforeApplyingALoweredGlobalQuotaToActiveWork() throws Exception {
        TestCommands commands = testCommands(false);
        VideoTranscodeProperties properties = properties(commands);
        service = initializedService(properties, Clock.systemUTC());
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("completed-quota.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        awaitStatus(sessionId, status -> status.state() == VideoTranscodeSessionState.COMPLETED, Duration.ofSeconds(5));

        properties.setMaxTotalBytes(1L);
        service.enforceLimitsNow();

        assertThatThrownBy(() -> service.get(sessionId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(tempDirectory.resolve("sessions")).isEmptyDirectory();
    }

    @Test
    void commandUsesSpeedFirstBoundedBrowserCompatibleEncoding() {
        VideoTranscodeSessionService.SourceInfo source = new VideoTranscodeSessionService.SourceInfo(
                60d, 10_000_000L, "prores", "yuv422p10le", 2160, 9_000_000L, "pcm_s16le", 1_000_000L);
        VideoTranscodeSessionService.EncodingPlan plan = VideoTranscodeSessionService.EncodingPlan.forSource(
                source, VideoTranscodeQuality.P720);

        List<String> command = VideoTranscodeSessionService.buildFfmpegCommand(
                "ffmpeg",
                Path.of("source.mov"),
                Path.of("session"),
                VideoTranscodeQuality.P720,
                source,
                plan,
                1,
                4);

        assertThat(command).containsSubsequence("-threads", "1", "-i");
        assertThat(command).containsSubsequence("-filter_threads", "1");
        assertThat(command).containsSubsequence("-filter_complex_threads", "1");
        assertThat(command).containsSubsequence("-c:v", "libx264", "-preset", "veryfast");
        assertThat(command).containsSubsequence("-maxrate", "2500000", "-bufsize", "5000000");
        assertThat(command).containsSubsequence("-vf", "scale=w=-2:h='min(720,ih)'", "-pix_fmt", "yuv420p");
        assertThat(command).containsSubsequence("-c:a", "aac", "-b:a", "128000", "-ac", "2");
        assertThat(command).containsSubsequence("-hls_segment_type", "fmp4");
        assertThat(command).containsSubsequence("-hls_flags", "independent_segments+temp_file");

        VideoTranscodeSessionService.SourceInfo compatible4k = new VideoTranscodeSessionService.SourceInfo(
                60d, 10_000_000L, "h264", "yuv420p", 2160, 9_800_000L, "aac", 192_000L);
        VideoTranscodeSessionService.EncodingPlan automaticPlan = VideoTranscodeSessionService.EncodingPlan.forSource(
                compatible4k, VideoTranscodeQuality.AUTO);
        assertThat(automaticPlan.copyVideo()).isTrue();
        assertThat(automaticPlan.copyAudio()).isTrue();
    }

    @Test
    void realFfmpegPipelineConvertsSyntheticProresAndSupportsReuseAndCleanup() throws Exception {
        Assumptions.assumeTrue(commandAvailable("ffmpeg") && commandAvailable("ffprobe"),
                "ffmpeg and ffprobe are required for the focused media pipeline test");
        Path source = tempDirectory.resolve("synthetic-prores.mov");
        Process generator = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=size=480x270:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=48000",
                "-t", "3",
                "-c:v", "prores_ks", "-profile:v", "0", "-pix_fmt", "yuv422p10le",
                "-c:a", "pcm_s16le",
                "-metadata", "creation_time=2026-08-17T08:09:30Z",
                source.toString())
                .redirectErrorStream(true)
                .start();
        byte[] generatorOutput = generator.getInputStream().readAllBytes();
        assertThat(generator.waitFor()).as(new String(generatorOutput, StandardCharsets.UTF_8)).isZero();

        VideoTranscodeProperties properties = new VideoTranscodeProperties();
        properties.setTempDirectory(tempDirectory.resolve("real-sessions"));
        properties.setSegmentSeconds(1);
        properties.setMonitorInterval(Duration.ofSeconds(1));
        service = initializedService(properties, Clock.systemUTC());

        VideoTranscodeSessionService.CreateResult created = service.create(71L, source, VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());
        VideoTranscodeSessionDto completed = awaitStatus(
                sessionId,
                status -> status.state() == VideoTranscodeSessionState.COMPLETED,
                Duration.ofSeconds(20));

        assertThat(completed.playlistReady()).isTrue();
        assertThat(completed.encodedSeconds()).isGreaterThan(0d);
        Path playlistPath = service.getOutput(sessionId, VideoTranscodeSessionService.PLAYLIST_FILE).path();
        String playlist = Files.readString(playlistPath);
        assertThat(playlist).contains("#EXTM3U", VideoTranscodeSessionService.INIT_FILE, "#EXT-X-ENDLIST");
        Matcher segmentMatcher = SEGMENT_REFERENCE.matcher(playlist);
        assertThat(segmentMatcher.find()).isTrue();
        assertThat(service.getOutput(sessionId, segmentMatcher.group(1)).contentLength()).isPositive();

        VideoTranscodeSessionService.CreateResult reused = service.create(71L, source, VideoTranscodeQuality.P480);
        assertThat(reused.reused()).isTrue();
        assertThat(reused.session().sessionId()).isEqualTo(created.session().sessionId());

        service.cancel(sessionId);
        assertThat(tempDirectory.resolve("real-sessions").resolve(sessionId.toString())).doesNotExist();
        assertThat(service.get(sessionId).state()).isEqualTo(VideoTranscodeSessionState.CANCELLED);
    }

    @Test
    void outputNamesAreStrictlyAllowlisted() throws Exception {
        TestCommands commands = testCommands(true);
        service = newService(commands, Clock.systemUTC());
        VideoTranscodeSessionService.CreateResult created = service.create(
                1L, source("safe.mov"), VideoTranscodeQuality.P480);
        UUID sessionId = UUID.fromString(created.session().sessionId());

        assertThatThrownBy(() -> service.getOutput(sessionId, "../source.mov"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getOutput(sessionId, "ffmpeg.log"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private VideoTranscodeSessionService newService(TestCommands commands, Clock clock) {
        return initializedService(properties(commands), clock);
    }

    private VideoTranscodeSessionService initializedService(VideoTranscodeProperties properties, Clock clock) {
        VideoTranscodeSessionService result = new VideoTranscodeSessionService(
                properties,
                new ObjectMapper(),
                new MediaProcessLimiter(new MediaProcessProperties()),
                clock);
        result.initialize();
        return result;
    }

    private VideoTranscodeProperties properties(TestCommands commands) {
        VideoTranscodeProperties properties = new VideoTranscodeProperties();
        properties.setTempDirectory(tempDirectory.resolve("sessions"));
        properties.setFfprobeCommand(commands.ffprobe().toString());
        properties.setFfmpegCommand(commands.ffmpeg().toString());
        properties.setMaxSessionBytes(64L * MEBIBYTE);
        properties.setMaxTotalBytes(128L * MEBIBYTE);
        properties.setDisconnectGrace(Duration.ofMinutes(5));
        properties.setCompletedExpiry(Duration.ofMinutes(30));
        properties.setMaxRuntime(Duration.ofHours(1));
        properties.setMonitorInterval(Duration.ofHours(1));
        properties.setSegmentSeconds(1);
        return properties;
    }

    private TestCommands testCommands(boolean keepRunning) throws IOException {
        Path commandDirectory = tempDirectory.resolve("commands-" + UUID.randomUUID());
        Files.createDirectories(commandDirectory);
        Path ffprobe = executable(commandDirectory.resolve("ffprobe"), """
                #!/bin/sh
                printf '%s' '{"streams":[{"codec_type":"video","codec_name":"prores","pix_fmt":"yuv422p10le","height":720,"bit_rate":"900000"},{"codec_type":"audio","codec_name":"pcm_s16le","bit_rate":"100000"}],"format":{"duration":"60.0","bit_rate":"1000000"}}'
                """);
        String sleep = keepRunning ? "sleep 30\n" : "";
        Path ffmpeg = executable(commandDirectory.resolve("ffmpeg"), """
                #!/bin/sh
                printf '%s\n' '#EXTM3U' '#EXT-X-MAP:URI="init.mp4"' '#EXTINF:1.0,' 'segment-000000.m4s' '#EXT-X-ENDLIST' > playlist.m3u8
                printf 'init' > init.mp4
                printf 'segment' > segment-000000.m4s
                printf 'out_time_us=1000000\nspeed=2.0x\nprogress=continue\n'
                """ + sleep);
        return new TestCommands(ffprobe, ffmpeg);
    }

    private Path source(String name) throws IOException {
        Path source = tempDirectory.resolve(name);
        Files.writeString(source, "synthetic source; metadata comes from the test ffprobe command");
        return source;
    }

    private static Path executable(Path path, String content) throws IOException {
        Files.writeString(path, content);
        assertThat(path.toFile().setExecutable(true)).isTrue();
        return path.toAbsolutePath();
    }

    private VideoTranscodeSessionDto awaitStatus(UUID sessionId,
                                                 Predicate<VideoTranscodeSessionDto> condition,
                                                 Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        VideoTranscodeSessionDto latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest = service.get(sessionId);
            if (condition.test(latest)) return latest;
            Thread.sleep(25L);
        }
        throw new AssertionError("Session did not reach expected status: " + latest);
    }

    private static boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private record TestCommands(Path ffprobe, Path ffmpeg) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
