package com.x8ing.mtl.server.mtlserver.web.services.track;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeQuality;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Owns bounded, temporary HLS transcoding sessions. Browser connections may
 * detach without stopping FFmpeg; a later request for the same source revision
 * and quality reuses the session.
 */
@Service
public class VideoTranscodeSessionService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeSessionService.class);

    static final String PLAYLIST_FILE = "playlist.m3u8";
    static final String INIT_FILE = "init.mp4";
    static final String SEGMENT_PATTERN = "segment-%06d.m4s";
    static final String PLAYLIST_URL_PREFIX = "api/media/transcode-sessions/";
    static final long MIN_OUTPUT_OVERHEAD_BYTES = 10L * 1024L * 1024L;
    static final double ESTIMATE_SAFETY_FACTOR = 1.10d;

    private static final Pattern SAFE_OUTPUT_FILE = Pattern.compile(
            "(?:playlist\\.m3u8|init\\.mp4|segment-[0-9]{6}\\.m4s)");
    private static final int ERROR_MESSAGE_LIMIT = 1_000;

    private final VideoTranscodeProperties properties;
    private final ObjectMapper objectMapper;
    private final MediaProcessLimiter mediaProcessLimiter;
    private final Clock clock;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<SessionKey, UUID> reusableSessions = new HashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = Thread.ofPlatform().daemon(true).name("video-transcode-monitor").unstarted(runnable);
        return thread;
    });

    @Autowired
    public VideoTranscodeSessionService(VideoTranscodeProperties properties,
                                        ObjectMapper objectMapper,
                                        MediaProcessLimiter mediaProcessLimiter) {
        this(properties, objectMapper, mediaProcessLimiter, Clock.systemUTC());
    }

    VideoTranscodeSessionService(VideoTranscodeProperties properties,
                                 ObjectMapper objectMapper,
                                 MediaProcessLimiter mediaProcessLimiter,
                                 Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mediaProcessLimiter = mediaProcessLimiter;
        this.clock = clock;
    }

    @PostConstruct
    void initialize() {
        properties.validate();
        Path root = rootDirectory();
        try {
            Files.createDirectories(root);
            clearDirectoryContents(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare the video transcode temporary directory", e);
        }
        long intervalMillis = Math.max(100L, properties.getMonitorInterval().toMillis());
        monitor.scheduleWithFixedDelay(this::runMonitorSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        monitor.shutdownNow();
        List<Process> processes;
        synchronized (this) {
            sessions.values().stream()
                    .filter(session -> !isTerminal(session.state))
                    .forEach(session -> {
                        session.state = VideoTranscodeSessionState.CANCELLED;
                        session.message = "Server is stopping";
                        reusableSessions.remove(session.key);
                    });
            processes = sessions.values().stream()
                    .map(session -> session.process)
                    .filter(process -> process != null && process.isAlive())
                    .toList();
        }
        processes.forEach(Process::destroyForcibly);
        workers.shutdownNow();
        try {
            clearDirectoryContents(rootDirectory());
        } catch (IOException e) {
            log.warn("Could not completely clean the video transcode directory during shutdown", e);
        }
    }

    public CreateResult create(long mediaId, Path sourcePath, VideoTranscodeQuality quality) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Video transcoding is disabled");
        }
        Path source = validateSource(sourcePath);
        VideoTranscodeQuality selectedQuality = quality == null ? VideoTranscodeQuality.AUTO : quality;
        long sourceSize = fileSize(source);
        long sourceLastModified = lastModified(source);
        SessionKey key = new SessionKey(mediaId, source, sourceSize, sourceLastModified, selectedQuality);

        synchronized (this) {
            CreateResult existing = reuseOrRejectAtCapacity(key);
            if (existing != null) return existing;
        }

        SourceInfo sourceInfo = probe(source);

        synchronized (this) {
            CreateResult existing = reuseOrRejectAtCapacity(key);
            if (existing != null) return existing;

            Instant now = clock.instant();
            makeRoomForSessionRecord();
            EncodingPlan encodingPlan = EncodingPlan.forSource(sourceInfo, selectedQuality);
            long estimatedBytes = estimateOutputBytes(sourceSize, sourceInfo, encodingPlan, selectedQuality);
            if (estimatedBytes > properties.getMaxSessionBytes()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "The selected quality would exceed the temporary video size limit. Choose a lower quality.");
            }
            evictCompletedUntilFits(estimatedBytes);
            if (reservedAndCompletedBytes() + estimatedBytes > properties.getMaxTotalBytes()) {
                throw new ResponseStatusException(
                        HttpStatus.INSUFFICIENT_STORAGE,
                        "There is not enough temporary video space for this conversion.");
            }

            UUID sessionId = UUID.randomUUID();
            Path outputDirectory = rootDirectory().resolve(sessionId.toString());
            try {
                Files.createDirectory(outputDirectory);
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not create temporary video storage",
                        e);
            }

            Session session = new Session(
                    sessionId,
                    key,
                    mediaId,
                    source,
                    selectedQuality,
                    sourceInfo,
                    encodingPlan,
                    estimatedBytes,
                    outputDirectory,
                    now);
            sessions.put(sessionId, session);
            reusableSessions.put(key, sessionId);
            try {
                start(session);
            } catch (MediaProcessLimiter.MediaProcessBusyException e) {
                sessions.remove(sessionId);
                reusableSessions.remove(key);
                deleteDirectoryQuietly(outputDirectory);
                throw videoTranscoderBusy(e);
            } catch (IOException e) {
                sessions.remove(sessionId);
                reusableSessions.remove(key);
                deleteDirectoryQuietly(outputDirectory);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The video transcoder is unavailable",
                        e);
            }
            return new CreateResult(toDto(session, false), false);
        }
    }

    private CreateResult reuseOrRejectAtCapacity(SessionKey key) {
        Instant now = clock.instant();
        enforceLimits(now);
        Session reusable = findReusable(key);
        if (reusable != null) {
            reusable.lastAccess = now;
            return new CreateResult(toDto(reusable, true), true);
        }
        if (activeSessionCount() >= properties.getMaxActiveSessions()) {
            throw videoTranscoderBusy(null);
        }
        return null;
    }

    private static ResponseStatusException videoTranscoderBusy(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "The video transcoder is busy. Try again when the active conversion finishes.",
                cause);
    }

    public synchronized VideoTranscodeSessionDto get(UUID sessionId) {
        Session session = requireSession(sessionId);
        session.lastAccess = clock.instant();
        return toDto(session, false);
    }

    public synchronized OutputResource getOutput(UUID sessionId, String fileName) {
        Session session = requireSession(sessionId);
        if (!SAFE_OUTPUT_FILE.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video stream file not found");
        }
        if (session.state == VideoTranscodeSessionState.FAILED
            || session.state == VideoTranscodeSessionState.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.GONE, session.message);
        }
        Path path = session.outputDirectory.resolve(fileName).normalize();
        if (!path.getParent().equals(session.outputDirectory) || !Files.isRegularFile(path)) {
            if (PLAYLIST_FILE.equals(fileName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The compatible stream is still being prepared");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video stream file not found");
        }
        session.lastAccess = clock.instant();
        return new OutputResource(path, contentType(fileName), fileSize(path));
    }

    public synchronized void cancel(UUID sessionId) {
        Session session = requireSession(sessionId);
        if (!isTerminal(session.state)) {
            stopSession(session, VideoTranscodeSessionState.CANCELLED, "Compatible playback was cancelled");
        } else if (session.state == VideoTranscodeSessionState.COMPLETED) {
            session.state = VideoTranscodeSessionState.CANCELLED;
            session.message = "Compatible playback was cancelled";
            reusableSessions.remove(session.key);
            deleteDirectoryQuietly(session.outputDirectory);
        }
        session.lastAccess = clock.instant();
    }

    synchronized void enforceLimitsNow() {
        enforceLimits(clock.instant());
    }

    private void start(Session session) throws IOException {
        List<String> command = buildFfmpegCommand(
                properties.getFfmpegCommand(),
                session.source,
                session.outputDirectory,
                session.quality,
                session.sourceInfo,
                session.encodingPlan,
                properties.getMaxThreads(),
                properties.getSegmentSeconds());
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(session.outputDirectory.toFile());
        Process process = mediaProcessLimiter.start(processBuilder);
        session.process = process;
        session.state = VideoTranscodeSessionState.RUNNING;
        session.message = session.encodingPlan.description();
        workers.submit(() -> readProgress(session, process));
        workers.submit(() -> readErrors(session, process));
        workers.submit(() -> awaitCompletion(session, process));
        log.info("Started temporary video transcode session {} for media {} at {}",
                session.id, session.mediaId, session.quality);
    }

    private void readProgress(Session session, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                updateProgress(session, line.substring(0, separator), line.substring(separator + 1));
            }
        } catch (IOException e) {
            if (process.isAlive()) log.debug("Could not read FFmpeg progress for session {}", session.id, e);
        }
    }

    private void updateProgress(Session session, String key, String value) {
        try {
            if ("out_time_us".equals(key)) {
                session.encodedSeconds = Long.parseLong(value) / 1_000_000d;
            } else if ("speed".equals(key) && value.endsWith("x")) {
                session.transcodeSpeed = Double.parseDouble(value.substring(0, value.length() - 1).trim());
            }
        } catch (NumberFormatException ignored) {
            // FFmpeg may report N/A while it is starting.
        }
    }

    private void readErrors(Session session, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) session.lastError = truncate(line, ERROR_MESSAGE_LIMIT);
            }
        } catch (IOException e) {
            if (process.isAlive()) log.debug("Could not read FFmpeg error output for session {}", session.id, e);
        }
    }

    private void awaitCompletion(Session session, Process process) {
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return;
        }
        synchronized (this) {
            if (session.state == VideoTranscodeSessionState.CANCELLED
                || session.state == VideoTranscodeSessionState.FAILED) return;

            long bytesWritten = directorySize(session.outputDirectory);
            if (bytesWritten > properties.getMaxSessionBytes()) {
                fail(session, "The temporary video size limit was reached");
                return;
            }
            evictCompletedForActualQuota();
            if (totalActualBytes() > properties.getMaxTotalBytes()) {
                fail(session, "The global temporary video size limit was reached");
            } else if (exitCode == 0 && playlistReady(session)) {
                session.state = VideoTranscodeSessionState.COMPLETED;
                session.completedAt = clock.instant();
                session.message = "Compatible stream is ready";
                enforceCompletedLimit();
                log.info("Completed temporary video transcode session {} for media {} ({} bytes)",
                        session.id, session.mediaId, bytesWritten);
            } else {
                String detail = session.lastError == null ? "FFmpeg exited with code " + exitCode : session.lastError;
                fail(session, "Compatible video conversion failed: " + truncate(detail, ERROR_MESSAGE_LIMIT));
            }
        }
    }

    private synchronized void fail(Session session, String message) {
        if (isTerminal(session.state)) return;
        stopSession(session, VideoTranscodeSessionState.FAILED, message);
        log.warn("Temporary video transcode session {} failed: {}", session.id, message);
    }

    private void stopSession(Session session, VideoTranscodeSessionState state, String message) {
        session.state = state;
        session.message = message;
        reusableSessions.remove(session.key);
        Process process = session.process;
        if (process != null && process.isAlive()) process.destroyForcibly();
        deleteDirectoryQuietly(session.outputDirectory);
        if (process != null) process.onExit().thenRun(() -> deleteDirectoryQuietly(session.outputDirectory));
    }

    private void runMonitorSafely() {
        try {
            synchronized (this) {
                enforceLimits(clock.instant());
            }
        } catch (RuntimeException e) {
            log.error("Temporary video transcode monitor failed", e);
        }
    }

    private void enforceLimits(Instant now) {
        List<Session> snapshot = new ArrayList<>(sessions.values());
        for (Session session : snapshot) {
            if (session.state == VideoTranscodeSessionState.RUNNING
                || session.state == VideoTranscodeSessionState.STARTING) {
                if (Duration.between(session.startedAt, now).compareTo(properties.getMaxRuntime()) > 0) {
                    fail(session, "Compatible video conversion exceeded its runtime limit");
                } else if (Duration.between(session.lastAccess, now).compareTo(properties.getDisconnectGrace()) > 0) {
                    fail(session, "Compatible video conversion expired after the player disconnected");
                } else if (directorySize(session.outputDirectory) > properties.getMaxSessionBytes()) {
                    fail(session, "The temporary video size limit was reached");
                }
            } else if (Duration.between(session.lastAccess, now).compareTo(properties.getCompletedExpiry()) > 0) {
                removeSession(session);
            }
        }
        evictCompletedForActualQuota();
        if (totalActualBytes() > properties.getMaxTotalBytes()) {
            sessions.values().stream()
                    .filter(session -> !isTerminal(session.state))
                    .max(Comparator.comparingLong(session -> directorySize(session.outputDirectory)))
                    .ifPresent(session -> fail(session, "The global temporary video size limit was reached"));
        }
        enforceCompletedLimit();
    }

    private void evictCompletedForActualQuota() {
        while (totalActualBytes() > properties.getMaxTotalBytes()) {
            Optional<Session> oldestCompleted = sessions.values().stream()
                    .filter(session -> session.state == VideoTranscodeSessionState.COMPLETED)
                    .min(Comparator.comparing(session -> session.completedAt));
            if (oldestCompleted.isEmpty()) return;
            removeSession(oldestCompleted.get());
        }
    }

    private void makeRoomForSessionRecord() {
        while (sessions.size() >= properties.getMaxSessions()) {
            Optional<Session> oldestTerminal = sessions.values().stream()
                    .filter(session -> isTerminal(session.state))
                    .min(Comparator.comparing(session -> session.lastAccess));
            if (oldestTerminal.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many video playback sessions are open");
            }
            removeSession(oldestTerminal.get());
        }
    }

    private void enforceCompletedLimit() {
        while (completedSessionCount() > properties.getMaxCompletedSessions()) {
            sessions.values().stream()
                    .filter(session -> session.state == VideoTranscodeSessionState.COMPLETED)
                    .min(Comparator.comparing(session -> session.completedAt))
                    .ifPresent(this::removeSession);
        }
    }

    private void evictCompletedUntilFits(long requiredBytes) {
        while (reservedAndCompletedBytes() + requiredBytes > properties.getMaxTotalBytes()) {
            Optional<Session> oldest = sessions.values().stream()
                    .filter(session -> session.state == VideoTranscodeSessionState.COMPLETED)
                    .min(Comparator.comparing(session -> session.completedAt));
            if (oldest.isEmpty()) return;
            removeSession(oldest.get());
        }
    }

    private void removeSession(Session session) {
        sessions.remove(session.id);
        reusableSessions.remove(session.key, session.id);
        Process process = session.process;
        if (process != null && process.isAlive()) process.destroyForcibly();
        deleteDirectoryQuietly(session.outputDirectory);
    }

    private Session findReusable(SessionKey key) {
        UUID sessionId = reusableSessions.get(key);
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            reusableSessions.remove(key);
            return null;
        }
        if (session.state == VideoTranscodeSessionState.STARTING
            || session.state == VideoTranscodeSessionState.RUNNING
            || (session.state == VideoTranscodeSessionState.COMPLETED && playlistReady(session))) {
            return session;
        }
        reusableSessions.remove(key);
        return null;
    }

    private long reservedAndCompletedBytes() {
        return sessions.values().stream()
                .mapToLong(session -> switch (session.state) {
                    case STARTING, RUNNING -> session.estimatedBytes;
                    case COMPLETED -> directorySize(session.outputDirectory);
                    case FAILED, CANCELLED -> 0L;
                })
                .sum();
    }

    private long totalActualBytes() {
        return sessions.values().stream()
                .mapToLong(session -> directorySize(session.outputDirectory))
                .sum();
    }

    private long activeSessionCount() {
        return sessions.values().stream()
                .filter(session -> session.state == VideoTranscodeSessionState.STARTING
                                   || session.state == VideoTranscodeSessionState.RUNNING)
                .count();
    }

    private long completedSessionCount() {
        return sessions.values().stream()
                .filter(session -> session.state == VideoTranscodeSessionState.COMPLETED)
                .count();
    }

    private Session requireSession(UUID sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video playback session not found");
        return session;
    }

    private VideoTranscodeSessionDto toDto(Session session, boolean reused) {
        return new VideoTranscodeSessionDto(
                session.id.toString(),
                session.mediaId,
                session.quality,
                session.state,
                PLAYLIST_URL_PREFIX + session.id + '/' + PLAYLIST_FILE,
                playlistReady(session),
                session.encodedSeconds,
                session.sourceInfo.durationSeconds(),
                session.transcodeSpeed,
                directorySize(session.outputDirectory),
                session.message,
                reused);
    }

    private boolean playlistReady(Session session) {
        Path playlist = session.outputDirectory.resolve(PLAYLIST_FILE);
        if (!Files.isRegularFile(playlist)) return false;
        try (Stream<Path> files = Files.list(session.outputDirectory)) {
            return files.anyMatch(path -> path.getFileName().toString().matches("segment-[0-9]{6}\\.m4s"));
        } catch (IOException e) {
            return false;
        }
    }

    private SourceInfo probe(Path source) {
        List<String> command = List.of(
                properties.getFfprobeCommand(),
                "-v", "error",
                "-show_entries", "format=duration,bit_rate:stream=codec_type,codec_name,pix_fmt,height,bit_rate",
                "-of", "json",
                source.toString());
        Process process;
        try {
            process = mediaProcessLimiter.start(
                    new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD));
        } catch (MediaProcessLimiter.MediaProcessBusyException e) {
            throw videoTranscoderBusy(e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The video inspector is unavailable", e);
        }
        var outputFuture = workers.submit(() -> {
            try (var input = process.getInputStream()) {
                return input.readAllBytes();
            }
        });
        try {
            if (!process.waitFor(properties.getProbeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Video inspection timed out");
            }
            byte[] output = outputFuture.get();
            if (process.exitValue() != 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The media file is not a readable video");
            }
            return parseProbe(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Video inspection was interrupted", e);
        } catch (java.util.concurrent.ExecutionException | IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Video metadata could not be read", e);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private SourceInfo parseProbe(byte[] json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode format = root.path("format");
        double duration = parseDouble(format.path("duration").asText());
        long formatBitrate = parseLong(format.path("bit_rate").asText());
        String videoCodec = null;
        String pixelFormat = null;
        int height = 0;
        long videoBitrate = 0L;
        String audioCodec = null;
        long audioBitrate = 0L;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText()) && videoCodec == null) {
                videoCodec = stream.path("codec_name").asText(null);
                pixelFormat = stream.path("pix_fmt").asText(null);
                height = stream.path("height").asInt(0);
                videoBitrate = parseLong(stream.path("bit_rate").asText());
            } else if ("audio".equals(stream.path("codec_type").asText()) && audioCodec == null) {
                audioCodec = stream.path("codec_name").asText(null);
                audioBitrate = parseLong(stream.path("bit_rate").asText());
            }
        }
        if (videoCodec == null || duration <= 0d || !Double.isFinite(duration)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The media file has no usable video stream");
        }
        return new SourceInfo(
                duration,
                formatBitrate,
                videoCodec.toLowerCase(Locale.ROOT),
                pixelFormat == null ? "" : pixelFormat.toLowerCase(Locale.ROOT),
                height,
                videoBitrate,
                audioCodec == null ? null : audioCodec.toLowerCase(Locale.ROOT),
                audioBitrate);
    }

    static List<String> buildFfmpegCommand(String ffmpegCommand,
                                           Path source,
                                           Path outputDirectory,
                                           VideoTranscodeQuality quality,
                                           SourceInfo sourceInfo,
                                           EncodingPlan plan,
                                           int maxThreads,
                                           int segmentSeconds) {
        if (maxThreads < 1) throw new IllegalArgumentException("maxThreads must be positive");
        List<String> command = new ArrayList<>(List.of(
                ffmpegCommand,
                "-hide_banner",
                "-loglevel", "error",
                "-nostdin",
                "-y",
                "-threads", String.valueOf(maxThreads),
                "-i", source.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-filter_threads", String.valueOf(maxThreads),
                "-filter_complex_threads", String.valueOf(maxThreads)));

        if (plan.copyVideo()) {
            command.addAll(List.of("-c:v", "copy"));
        } else {
            String scale = "scale=w=-2:h='min(" + quality.maxHeight() + ",ih)'";
            command.addAll(List.of(
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-maxrate", quality.videoBitrate() + "",
                    "-bufsize", (quality.videoBitrate() * 2L) + "",
                    "-vf", scale,
                    "-pix_fmt", "yuv420p",
                    "-force_key_frames", "expr:gte(t,n_forced*" + segmentSeconds + ")"));
        }

        if (sourceInfo.audioCodec() == null) {
            command.add("-an");
        } else if (plan.copyAudio()) {
            command.addAll(List.of("-c:a", "copy"));
        } else {
            command.addAll(List.of(
                    "-c:a", "aac",
                    "-b:a", quality.audioBitrate() + "",
                    "-ac", "2"));
        }

        command.addAll(List.of(
                "-threads", String.valueOf(maxThreads),
                "-progress", "pipe:1",
                "-nostats",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentSeconds),
                "-hls_playlist_type", "event",
                "-hls_segment_type", "fmp4",
                "-hls_fmp4_init_filename", INIT_FILE,
                "-hls_segment_filename", outputDirectory.resolve(SEGMENT_PATTERN).toString(),
                "-hls_flags", "independent_segments+temp_file",
                outputDirectory.resolve(PLAYLIST_FILE).toString()));
        return List.copyOf(command);
    }

    private long estimateOutputBytes(long sourceSize,
                                     SourceInfo sourceInfo,
                                     EncodingPlan plan,
                                     VideoTranscodeQuality quality) {
        if (plan.copyVideo() && (sourceInfo.audioCodec() == null || plan.copyAudio())) {
            return addEstimateMargin(sourceSize);
        }
        long videoBitrate = plan.copyVideo()
                ? knownBitrate(sourceInfo.videoBitrate(), sourceInfo.formatBitrate(), quality.videoBitrate())
                : quality.videoBitrate();
        long audioBitrate = sourceInfo.audioCodec() == null
                ? 0L
                : plan.copyAudio()
                    ? knownBitrate(sourceInfo.audioBitrate(), 0L, quality.audioBitrate())
                    : quality.audioBitrate();
        double rawBytes = sourceInfo.durationSeconds() * (videoBitrate + audioBitrate) / 8d;
        if (!Double.isFinite(rawBytes) || rawBytes >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return addEstimateMargin((long) Math.ceil(rawBytes));
    }

    private static long knownBitrate(long streamBitrate, long formatBitrate, long fallback) {
        if (streamBitrate > 0L) return streamBitrate;
        if (formatBitrate > 0L) return formatBitrate;
        return fallback;
    }

    private static long addEstimateMargin(long bytes) {
        double withMargin = bytes * ESTIMATE_SAFETY_FACTOR + MIN_OUTPUT_OVERHEAD_BYTES;
        return withMargin >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(withMargin);
    }

    private Path rootDirectory() {
        return properties.getTempDirectory().toAbsolutePath().normalize();
    }

    private static Path validateSource(Path sourcePath) {
        if (sourcePath == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Media file path is missing");
        }
        Path source = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Media file is not readable");
        }
        return source;
    }

    private static long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) return 0L;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(VideoTranscodeSessionService::fileSize).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Media file metadata is unavailable", e);
        }
    }

    private static void clearDirectoryContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.toList()) deleteRecursively(child);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }

    private static void deleteDirectoryQuietly(Path directory) {
        try {
            deleteRecursively(directory);
        } catch (IOException e) {
            log.debug("Could not yet delete temporary video directory {}", directory, e);
        }
    }

    private static String contentType(String fileName) {
        if (fileName.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (fileName.endsWith(".m4s")) return "video/iso.segment";
        return "video/mp4";
    }

    private static boolean isTerminal(VideoTranscodeSessionState state) {
        return state == VideoTranscodeSessionState.COMPLETED
               || state == VideoTranscodeSessionState.FAILED
               || state == VideoTranscodeSessionState.CANCELLED;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record CreateResult(VideoTranscodeSessionDto session, boolean reused) {
    }

    public record OutputResource(Path path, String contentType, long contentLength) {
    }

    record SourceInfo(double durationSeconds,
                      long formatBitrate,
                      String videoCodec,
                      String pixelFormat,
                      int videoHeight,
                      long videoBitrate,
                      String audioCodec,
                      long audioBitrate) {
    }

    record EncodingPlan(boolean copyVideo, boolean copyAudio) {

        static EncodingPlan forSource(SourceInfo source, VideoTranscodeQuality quality) {
            boolean compatibleH264 = "h264".equals(source.videoCodec())
                                     && ("yuv420p".equals(source.pixelFormat())
                                         || "yuvj420p".equals(source.pixelFormat()))
                                     && source.videoHeight() > 0
                                     && (quality == VideoTranscodeQuality.AUTO
                                         || source.videoHeight() <= quality.maxHeight());
            boolean compatibleAudio = source.audioCodec() == null || "aac".equals(source.audioCodec());
            return new EncodingPlan(compatibleH264, compatibleAudio);
        }

        String description() {
            if (copyVideo && copyAudio) return "Preparing a browser-compatible stream without re-encoding";
            if (copyVideo) return "Preparing compatible audio without re-encoding the video";
            return "Creating a browser-compatible video stream";
        }
    }

    private record SessionKey(long mediaId,
                              Path source,
                              long sourceSize,
                              long sourceLastModified,
                              VideoTranscodeQuality quality) {
    }

    private static final class Session {
        private final UUID id;
        private final SessionKey key;
        private final long mediaId;
        private final Path source;
        private final VideoTranscodeQuality quality;
        private final SourceInfo sourceInfo;
        private final EncodingPlan encodingPlan;
        private final long estimatedBytes;
        private final Path outputDirectory;
        private final Instant startedAt;
        private volatile Instant lastAccess;
        private volatile Instant completedAt;
        private volatile VideoTranscodeSessionState state = VideoTranscodeSessionState.STARTING;
        private volatile Process process;
        private volatile double encodedSeconds;
        private volatile Double transcodeSpeed;
        private volatile String lastError;
        private volatile String message = "Starting compatible video conversion";

        private Session(UUID id,
                        SessionKey key,
                        long mediaId,
                        Path source,
                        VideoTranscodeQuality quality,
                        SourceInfo sourceInfo,
                        EncodingPlan encodingPlan,
                        long estimatedBytes,
                        Path outputDirectory,
                        Instant now) {
            this.id = id;
            this.key = key;
            this.mediaId = mediaId;
            this.source = source;
            this.quality = quality;
            this.sourceInfo = sourceInfo;
            this.encodingPlan = encodingPlan;
            this.estimatedBytes = estimatedBytes;
            this.outputDirectory = outputDirectory;
            this.startedAt = now;
            this.lastAccess = now;
        }
    }
}
