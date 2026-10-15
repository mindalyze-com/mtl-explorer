package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class ReleaseFeedClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_FEED_BYTES = 256 * 1024;
    private static final int SUPPORTED_SCHEMA = 1;
    private static final String DIGEST_PATTERN = "sha256:[a-f0-9]{64}";
    private static final String BUILD_PATTERN = "[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}";
    private static final Duration COMPONENT_TIMEOUT = Duration.ofSeconds(3);
    static final Set<String> COMPONENT_IDS = Set.of("app", "brouter", "location-search", "map-server");
    private static final Set<String> CHANNELS = Set.of("stable", "beta", "alpha");
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Duration requestTimeout;

    public ReleaseFeedClient() {
        this(REQUEST_TIMEOUT);
    }

    ReleaseFeedClient(Duration requestTimeout) {
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Feed fetch(String url) throws IOException {
        return parse(fetchBytes(url, requestTimeout));
    }

    public String fetchBuildId(String url) throws IOException {
        var root = mapper.readTree(fetchBytes(url, COMPONENT_TIMEOUT));
        String build = root.path("versionInfo").path("image").path("version").asText(null);
        if (build == null || !build.matches(BUILD_PATTERN)) throw new IOException("Component status has no image build identity");
        return build;
    }

    private byte[] fetchBytes(String url, Duration timeout) throws IOException {
        URI uri = safeUrl(url);
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build();
        var pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
        try {
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new IOException("Update discovery endpoint returned HTTP " + response.statusCode());
            return response.body();
        } catch (TimeoutException exception) {
            throw new IOException("Update discovery request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Update discovery request interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Update discovery request failed: " + exception.getCause().getClass().getSimpleName(), exception.getCause());
        } finally {
            pending.cancel(true);
        }
    }

    @PreDestroy
    public void close() {
        client.shutdownNow();
    }

    /**
     * Bound memory before collecting the body, including responses without Content-Length.
     */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytes;
        private boolean failed;

        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            bytes += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (bytes > MAX_FEED_BYTES) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("Update discovery response exceeds size limit"));
            } else delegate.onNext(buffers);
        }

        public void onError(Throwable throwable) {
            if (!failed) delegate.onError(throwable);
        }

        public void onComplete() {
            if (!failed) delegate.onComplete();
        }
    }

    static URI safeUrl(String value) {
        if (value == null) throw new IllegalArgumentException("Update discovery URL is required");
        URI uri = URI.create(value);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Update discovery URL must be HTTP(S) without credentials");
        return uri;
    }

    Feed parse(byte[] bytes) throws IOException {
        JsonNode root = mapper.readTree(bytes);
        if (root == null || !root.isObject() || !root.path("schemaVersion").isIntegralNumber()
            || !root.path("schemaVersion").canConvertToInt()
            || root.path("schemaVersion").intValue() != SUPPORTED_SCHEMA
            || !root.path("channels").isObject() || !root.path("releases").isObject())
            throw new IllegalArgumentException("Unsupported release feed schema");

        Map<String, Release> releases = new HashMap<>();
        var entries = root.path("releases").fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            try {
                JsonNode node = entry.getValue();
                if (node.path("components") instanceof ObjectNode components) {
                    // Ignore extensions before deserialization, including unknown artifact formats.
                    components.retain(COMPONENT_IDS);
                }
                Release release = mapper.treeToValue(node, Release.class);
                validateRelease(entry.getKey(), release);
                releases.put(entry.getKey(), release);
            } catch (IOException | IllegalArgumentException exception) {
                log.warn("Skipping MTL Explorer release entry: type={}, message=invalid published metadata",
                        exception.getClass().getSimpleName());
                log.debug("Invalid MTL Explorer release entry", exception);
            }
        }

        // Quarantine every release with an ambiguous identity; never choose a binding by entry order.
        Map<String, String> identities = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        releases.values().forEach(release -> release.artifacts().forEach((id, artifact) -> {
            String key = id + ":" + artifact.buildId;
            String binding = artifact.image + "@" + artifact.digest;
            String previous = identities.putIfAbsent(key, binding);
            if (previous != null && !previous.equals(binding)) conflicts.add(key);
        }));
        if (!conflicts.isEmpty()) {
            releases.values().removeIf(release -> release.artifacts().entrySet().stream()
                    .anyMatch(entry -> conflicts.contains(entry.getKey() + ":" + entry.getValue().buildId)));
            log.warn("Skipped MTL Explorer releases with conflicting component bindings: identities={}", conflicts.size());
        }
        Map<String, String> channels = new HashMap<>();
        var pointers = root.path("channels").fields();
        while (pointers.hasNext()) {
            var entry = pointers.next();
            if (!CHANNELS.contains(entry.getKey())) continue;
            String version = entry.getValue().isTextual() ? entry.getValue().textValue() : null;
            if (version == null || !releases.containsKey(version)
                || ("stable".equals(entry.getKey()) && version.contains("-"))) {
                log.warn("Skipping MTL Explorer release channel: channel={}, message=invalid or unavailable target", entry.getKey());
                continue;
            }
            channels.put(entry.getKey(), version);
        }
        return new Feed(SUPPORTED_SCHEMA, Map.copyOf(channels), Map.copyOf(releases));
    }

    private static void validateRelease(String version, Release release) {
        if (!ReleaseVersion.valid(version) || release == null || release.buildId == null
            || !release.buildId.matches(BUILD_PATTERN) || release.digest == null || !release.digest.matches(DIGEST_PATTERN)
            || release.image == null || !release.image.endsWith(":" + release.buildId)
            || release.summary == null || release.summary.length() > 160
            || release.publishedOn == null)
            throw new IllegalArgumentException("Invalid published release metadata");
        safeUrl(release.releaseNotesUrl);
        safeUrl(release.updateGuideUrl);
        for (var component : release.artifacts().entrySet()) {
            Artifact artifact = component.getValue();
            if (artifact == null || artifact.buildId == null
                || !artifact.buildId.matches(BUILD_PATTERN) || artifact.digest == null || !artifact.digest.matches(DIGEST_PATTERN)
                || artifact.image == null || !artifact.image.endsWith(":" + artifact.buildId)
                || (artifact.compatibleBuildIds != null && artifact.compatibleBuildIds.stream()
                    .anyMatch(build -> build == null || !build.matches(BUILD_PATTERN))))
                throw new IllegalArgumentException("Invalid release component artifact");
        }
        Artifact app = release.artifacts().get("app");
        if (app == null || !java.util.Objects.equals(app.buildId, release.buildId)
            || !java.util.Objects.equals(app.image, release.image) || !java.util.Objects.equals(app.digest, release.digest))
            throw new IllegalArgumentException("Release application artifact does not match bundle");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feed(int schemaVersion, Map<String, String> channels, Map<String, Release> releases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Release(String buildId, String image, String digest, String summary, String releaseNotesUrl,
                          String updateGuideUrl, LocalDate publishedOn, Map<String, Artifact> components) {
        public Release(String buildId, String image, String digest, String summary, String notes, String guide, LocalDate date) {
            this(buildId, image, digest, summary, notes, guide, date, null);
        }

        public Map<String, Artifact> artifacts() {
            return components == null ? Map.of("app", new Artifact(buildId, image, digest, null)) : components;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artifact(String buildId, String image, String digest, List<String> compatibleBuildIds) {
    }
}
