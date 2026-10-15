package com.x8ing.mtl.server.mtlserver.googlehealth.client;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.AccessTokenProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.ExerciseTcxProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncBudget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.node.ObjectNode;

import java.io.CharConversionException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ApiClient implements GoogleHealthProvider, ExerciseTcxProvider {

    private static final int PAGE_SIZE = 10_000;
    private static final int SESSION_PAGE_SIZE = 25;
    private static final int SERVER_ERROR_STATUS = 500;
    private static final long MAX_ERROR_BODY_BYTES = 16 * 1024L;
    private static final int MAX_PAGE_TOKEN_LENGTH = 8 * 1024;
    private static final int MAX_DIAGNOSTIC_CODES = 12;
    private static final Pattern DIAGNOSTIC_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,95}");
    private static final String MISSING_OAUTH_SCOPE = "MISSING_OAUTH_SCOPE";
    private static final String LEGACY_MISSING_OAUTH_SCOPE = "ACCESS_TOKEN_SCOPE_INSUFFICIENT";
    private static final String DETAILED_REASONS_FIELD = "detailedReasons";
    private static final Set<String> MISSING_SCOPE_REASONS = Set.of(
            MISSING_OAUTH_SCOPE,
            LEGACY_MISSING_OAUTH_SCOPE);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Pattern EXERCISE_RESOURCE = Pattern.compile(
            "^users/[^/]+/dataTypes/exercise/dataPoints/[A-Za-z0-9_-]+$");

    private final OkHttpClient httpClient;
    private final ConnectorProperties properties;
    private final AccessTokenProvider accessTokenProvider;
    private final ObjectMapper objectMapper;
    private final ObjectReader dataPointReader;
    private MirrorRepository limiterRepository;
    private long nextAttemptNanos;
    private Instant cooldownUntil;
    private long attempts, retries, rateLimits, pages, bytes, probes, routeAttempts;
    private String requestPurpose = "UNSPECIFIED";

    public synchronized Counters counters() {
        return new Counters(attempts, retries, rateLimits, pages, bytes, probes, routeAttempts);
    }

    public void purpose(String purpose) {
        requestPurpose = purpose;
    }

    @Autowired
    public void configureLimiter(MirrorRepository repository) {
        limiterRepository = repository;
    }

    public synchronized void beginCycle() {
        if (limiterRepository != null) cooldownUntil = limiterRepository.cooldown();
        attempts = 0;
        retries = 0;
        rateLimits = 0;
        pages = 0;
        bytes = 0;
        probes = 0;
        routeAttempts = 0;
    }

    public boolean coolingDown() {
        return cooldownUntil != null && cooldownUntil.isAfter(Instant.now());
    }

    public void countProbe() {
        probes++;
    }

    private synchronized void acquireAttempt() {
        if (coolingDown()) throw new QuotaPause();
        if (limiterRepository != null) {
            long remaining = nextAttemptNanos - System.nanoTime();
            if (remaining > 0) {
                SyncBudget.require(Duration.ofNanos(remaining).plusMillis(1));
                pause(Duration.ofNanos(remaining));
            }
            nextAttemptNanos = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        }
        SyncBudget.require(Duration.ofMillis(100));
        attempts++;
    }


    public ApiClient(@Qualifier("googleHealthHttpClient") OkHttpClient httpClient,
                     ConnectorProperties properties,
                     AccessTokenProvider accessTokenProvider,
                     ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.accessTokenProvider = accessTokenProvider;
        this.objectMapper = objectMapper;
        this.dataPointReader = objectMapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public Page listRaw(DataTypePolicy.Policy policy, Instant start, Instant end, String pageToken) {
        String filterField = filterField(policy);
        String startLiteral = filterLiteral(policy, start);
        String endLiteral = filterLiteral(policy, end);
        String filter = "%s >= \"%s\" AND %s < \"%s\"".formatted(
                filterField, startLiteral, filterField, endLiteral);
        HttpUrl.Builder url = baseDataPointsUrl(policy.dataType()).newBuilder()
                .addQueryParameter("filter", filter)
                .addQueryParameter("pageSize", String.valueOf(pageSize(policy.dataType())));
        addPageToken(url, pageToken);
        return executePage(url.build(), "dataPoints", pageSize(policy.dataType()));
    }

    public Page reconcile(DataTypePolicy.Policy policy,
                          Instant start,
                          Instant end,
                          String pageToken) {
        String filterField = filterField(policy);
        String filter = "%s >= \"%s\" AND %s < \"%s\"".formatted(
                filterField, DateTimeFormatter.ISO_INSTANT.format(start),
                filterField, DateTimeFormatter.ISO_INSTANT.format(end));
        HttpUrl.Builder url = HttpUrl.get(properties.getApiBaseUrl()).newBuilder()
                .addPathSegments("v4/users/me/dataTypes/" + policy.dataType() + "/dataPoints:reconcile")
                .addQueryParameter("filter", filter)
                .addQueryParameter("dataSourceFamily", ALL_SOURCES)
                .addQueryParameter("pageSize", String.valueOf(PAGE_SIZE));
        addPageToken(url, pageToken);
        return executePage(url.build(), "dataPoints", PAGE_SIZE);
    }

    public Page dailyRollUp(DataTypePolicy.Policy policy,
                            LocalDate start,
                            LocalDate end,
                            String pageToken) {
        HttpUrl url = HttpUrl.get(properties.getApiBaseUrl()).newBuilder()
                .addPathSegments("v4/users/me/dataTypes/" + policy.dataType() + "/dataPoints:dailyRollUp")
                .build();
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode range = request.putObject("range");
        putCivilDateTime(range.putObject("start"), start);
        putCivilDateTime(range.putObject("end"), end);
        request.put("windowSizeDays", 1);
        request.put("pageSize", policy.maxWindowDays());
        request.put("dataSourceFamily", ALL_SOURCES);
        if (pageToken != null && !pageToken.isBlank()) {
            request.put("pageToken", pageToken);
        }
        RequestBody body = RequestBody.create(request.toString(), JSON);
        return executePage(
                new Request.Builder().url(url).post(body),
                "rollupDataPoints",
                policy.maxWindowDays());
    }

    public void downloadExerciseTcx(String resourceName, Path destination) throws IOException {
        if (resourceName == null || !EXERCISE_RESOURCE.matcher(resourceName).matches()) {
            throw new IllegalArgumentException("Invalid Google Health exercise resource name");
        }
        HttpUrl url = HttpUrl.get(properties.getApiBaseUrl()).newBuilder()
                .addPathSegments("v4/" + resourceName + ":exportExerciseTcx")
                .addQueryParameter("alt", "media")
                .build();
        try (Response response = execute(() -> new Request.Builder().url(url).get())) {
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Google Health returned an empty TCX response");
            }
            long declaredLength = body.contentLength();
            if (declaredLength > properties.getMaxTcxBytes()) {
                throw new IllegalArgumentException("Google Health TCX exceeds the configured size limit");
            }
            try (InputStream input = body.byteStream(); OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    bytes += read;
                    if (total > properties.getMaxTcxBytes()) {
                        throw new IllegalArgumentException("Google Health TCX exceeds the configured size limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
        }
    }

    private Page executePage(HttpUrl url, String arrayField, int maxPoints) {
        return executePage(new Request.Builder().url(url).get(), arrayField, maxPoints);
    }

    private Page executePage(Request.Builder request, String arrayField, int maxPoints) {
        try (Response response = execute(() -> request)) {
            ResponseBody body = response.body();
            if (body == null) {
                throw invalidResponse("empty body");
            }
            long maxBytes = properties.getMaxJsonBytes();
            if (maxBytes <= 0) {
                throw new IllegalStateException("Google Health max-json-bytes must be positive");
            }
            if (body.contentLength() > maxBytes) {
                throw invalidResponse("JSON exceeds the configured size limit");
            }
            try (BoundedJsonInputStream input = new BoundedJsonInputStream(body.byteStream(), maxBytes);
                 JsonParser parser = objectMapper.createParser(input)) {
                Page page = readPage(parser, arrayField, maxPoints);
                pages++;
                return page;
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (JacksonException | IOException exception) {
            // Parser diagnostics can include fragments of the private response.
            // Keep the type, but do not propagate those messages or causes to job logs.
            Throwable cause = exception;
            boolean transportFailure = false;
            while (cause != null) {
                if (cause instanceof JsonSizeLimitException) {
                    log.warn("Could not read Google Health JSON response: errorType={}, reason=size limit",
                            exception.getClass().getSimpleName());
                    throw invalidResponse("JSON exceeds the configured size limit");
                }
                transportFailure |= cause instanceof IOException && !(cause instanceof CharConversionException);
                cause = cause.getCause();
            }
            if (transportFailure) {
                log.warn("Could not read Google Health JSON response: errorType={}, reason=transport",
                        exception.getClass().getSimpleName());
                throw new ApiException("Google Health request timed out or failed", 0, true, ApiException.Reason.TRANSPORT);
            }
            log.warn("Could not read Google Health JSON response: errorType={}, reason=invalid JSON",
                    exception.getClass().getSimpleName());
            throw invalidResponse("malformed or incomplete JSON");
        }
    }

    private Page readPage(JsonParser parser, String arrayField, int maxPoints) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw invalidResponse("root is not an object");
        }
        List<JsonNode> points = new ArrayList<>();
        String nextPageToken = null;
        boolean collectionSeen = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                throw invalidResponse("missing property name");
            }
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (arrayField.equals(name)) {
                if (collectionSeen) {
                    throw invalidResponse("duplicate collection");
                }
                collectionSeen = true;
                // Empty repeated ProtoJSON fields can be omitted or null.
                if (value == JsonToken.VALUE_NULL) continue;
                if (value != JsonToken.START_ARRAY) {
                    throw invalidResponse("collection is not an array");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) {
                        throw invalidResponse("collection contains a non-object item");
                    }
                    if (points.size() >= Math.max(1, maxPoints)) {
                        throw invalidResponse("page exceeds the requested result limit");
                    }
                    points.add(dataPointReader.readTree(parser));
                }
            } else if ("nextPageToken".equals(name)) {
                if (value != JsonToken.VALUE_NULL && value != JsonToken.VALUE_STRING) {
                    throw invalidResponse("page token is not a string");
                }
                nextPageToken = value == JsonToken.VALUE_NULL ? null : parser.getString();
                if (nextPageToken != null && nextPageToken.length() > MAX_PAGE_TOKEN_LENGTH) {
                    throw invalidResponse("page token exceeds the size limit");
                }
            } else {
                if ("error".equals(name)) throw invalidResponse("error payload in a successful response");
                parser.skipChildren();
            }
        }
        if (parser.nextToken() != null) {
            throw invalidResponse("trailing JSON content");
        }
        return new Page(List.copyOf(points), nextPageToken);
    }

    private final class BoundedJsonInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        private BoundedJsonInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) checkSize(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            long remaining = maximumBytes - bytesRead;
            int requested = (int) Math.min(length, remaining >= Integer.MAX_VALUE ? length : remaining + 1);
            int count = in.read(buffer, offset, requested);
            if (count > 0) checkSize(count);
            return count;
        }

        private void checkSize(int count) throws IOException {
            bytesRead += count;
            bytes += count;
            if (bytesRead > maximumBytes) throw new JsonSizeLimitException();
        }
    }

    private static final class JsonSizeLimitException extends IOException {
        private JsonSizeLimitException() {
            super("Google Health JSON exceeds the configured size limit");
        }
    }

    private static ApiException invalidResponse(String detail) {
        return new ApiException(
                "Google Health returned an invalid response (" + detail + ")",
                0,
                true,
                ApiException.Reason.INVALID_RESPONSE);
    }

    private Response execute(RequestFactory requestFactory) {
        int maxAttempts = Math.max(1, properties.getMaxRetryAttempts());
        boolean authRecoveryUsed = false;
        for (int attempt = 0; attempt < maxAttempts + 1; attempt++) {
            Request request = requestFactory.builder()
                    .header("Authorization", "Bearer " + accessTokenProvider.accessToken())
                    .header("Accept", "application/json")
                    .build();
            try {
                acquireAttempt();
                if (attempt > 0) retries++;
                if (request.url().encodedPath().endsWith(":exportExerciseTcx")) routeAttempts++;
                Call call = httpClient.newCall(request);
                long remaining = SyncBudget.remainingNanos();
                if (remaining <= 0) throw new SyncBudget.Yield();
                if (remaining != Long.MAX_VALUE)
                    call.timeout().timeout(Math.min(properties.getRequestTimeout().toNanos(), remaining), TimeUnit.NANOSECONDS);
                long requestStarted = System.nanoTime();
                Response response = call.execute();
                log.info("Google Health outbound attempt: purpose={}, method={}, path={}, status={}, headersDurationMs={}",
                        requestPurpose, request.method(), safeRequestPath(request), response.code(), (System.nanoTime() - requestStarted) / 1_000_000);
                if (response.isSuccessful()) {
                    if (attempt > 0) {
                        log.info("Google Health API request recovered after retry: method={}, path={}, attempts={}",
                                request.method(), safeRequestPath(request), attempt + 1);
                    }
                    return response;
                }
                int status;
                Duration retryAfter;
                String errorBody;
                try (response) {
                    status = response.code();
                    retryAfter = retryAfter(response.header("Retry-After"));
                    errorBody = readErrorBody(response.body());
                }
                if (status == 429 && limiterRepository != null) {
                    rateLimits++;
                    Duration delay = ApiRetryPolicy.delayForAttempt(attempt, retryAfter);
                    cooldownUntil = Instant.now().plus(delay);
                    limiterRepository.saveCooldown(cooldownUntil);
                    throw new QuotaPause();
                }
                boolean missingScope = status == 403 && isMissingScopeError(errorBody);
                boolean staleAuthorization = status == 401 || missingScope;
                ApiException.Reason reason = reasonFor(status, errorBody, missingScope);
                boolean retriable = ApiRetryPolicy.isRetriableStatus(status);
                // Do not log response messages, request queries/bodies, or opaque tokens:
                // Google can echo credentials and health data in validation errors.
                log.warn("Google Health API request rejected: method={}, path={}, status={}, reason={}, "
                         + "retriable={}, attempt={}, providerDetails={}",
                        request.method(), safeRequestPath(request), status, reason, retriable,
                        attempt + 1, errorDiagnostics(errorBody));
                if (staleAuthorization && !authRecoveryUsed) {
                    accessTokenProvider.invalidate();
                    authRecoveryUsed = true;
                    log.info("Retrying Google Health API request once with a fresh access token: "
                             + "method={}, path={}, status={}",
                            request.method(), safeRequestPath(request), status);
                    continue;
                }
                int allowedAttempts = maxAttempts + (authRecoveryUsed ? 1 : 0);
                if (!retriable || attempt + 1 >= allowedAttempts) {
                    throw new ApiException(safeStatusMessage(status, reason), status, retriable, reason);
                }
                Duration delay = ApiRetryPolicy.delayForAttempt(attempt, retryAfter);
                log.warn("Retrying Google Health API request: method={}, path={}, status={}, "
                         + "attempt={}/{}, delayMs={}",
                        request.method(), safeRequestPath(request), status,
                        attempt + 1, allowedAttempts, delay.toMillis());
                SyncBudget.require(delay.plusMillis(100));
                pause(delay);
            } catch (ApiException exception) {
                throw exception;
            } catch (IOException exception) {
                int allowedAttempts = maxAttempts + (authRecoveryUsed ? 1 : 0);
                if (attempt + 1 >= allowedAttempts) {
                    log.warn("Google Health API transport attempts exhausted: method={}, path={}, "
                             + "attempts={}, errorType={}, error={}", request.method(), safeRequestPath(request),
                            attempt + 1, exception.getClass().getSimpleName(), safeError(exception));
                    throw new ApiException("Google Health request timed out or failed", 0, true, exception);
                }
                Duration delay = ApiRetryPolicy.delayForAttempt(attempt, null);
                log.warn("Retrying Google Health API request after an I/O failure: method={}, path={}, "
                         + "attempt={}/{}, delayMs={}, errorType={}, error={}",
                        request.method(), safeRequestPath(request),
                        attempt + 1, allowedAttempts, delay.toMillis(),
                        exception.getClass().getSimpleName(), safeError(exception));
                SyncBudget.require(delay.plusMillis(100));
                pause(delay);
            }
        }
        throw new ApiException("Google Health request failed", 0, false, ApiException.Reason.OTHER);
    }

    private static String safeRequestPath(Request request) {
        return request.url().encodedPath()
                .replaceAll("/users/[^/]+", "/users/{user}")
                .replaceAll("/dataPoints/[^/:]+", "/dataPoints/{point}");
    }

    private String errorDiagnostics(String errorBody) {
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            if (root == null || !root.path("error").isObject()) return "unavailable";
            JsonNode error = root.path("error");
            Set<String> codes = new LinkedHashSet<>();
            addDiagnosticCodes(codes, error.get("status"));
            JsonNode details = error.path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    addDiagnosticCodes(codes, detail.get("reason"));
                    addDiagnosticCodes(codes, detail.path("metadata").get(DETAILED_REASONS_FIELD));
                }
            }
            return codes.isEmpty() ? "unavailable" : codes.toString();
        } catch (RuntimeException exception) {
            log.warn("Could not inspect Google Health error diagnostics: errorType={}, error=invalid JSON",
                    exception.getClass().getSimpleName());
            return "invalid JSON";
        }
    }

    private static void addDiagnosticCodes(Set<String> codes, JsonNode value) {
        if (value == null || codes.size() >= MAX_DIAGNOSTIC_CODES) return;
        if (value.isArray()) {
            for (JsonNode item : value) addDiagnosticCodes(codes, item);
        } else if (value.isTextual()) {
            // detailedReasons can be either an array or a bracketed comma-separated string.
            for (String part : value.asText().replace("[", "").replace("]", "").split(",")) {
                String code = part.strip();
                if (DIAGNOSTIC_CODE.matcher(code).matches() && codes.size() < MAX_DIAGNOSTIC_CODES) {
                    codes.add(code);
                }
            }
        }
    }

    private HttpUrl baseDataPointsUrl(String dataType) {
        return HttpUrl.get(properties.getApiBaseUrl()).newBuilder()
                .addPathSegments("v4/users/me/dataTypes/" + dataType + "/dataPoints")
                .build();
    }

    private static String filterField(DataTypePolicy.Policy policy) {
        String prefix = DataTypePolicy.filterFieldName(policy.dataType());
        return switch (policy.recordType()) {
            case INTERVAL -> prefix + ".interval.start_time";
            case SESSION_START -> prefix + ".interval.civil_start_time";
            case SESSION_END -> prefix + ".interval.end_time";
            case DAILY -> prefix + ".date";
            case SAMPLE -> prefix + ".sample_time.physical_time";
        };
    }

    private static String filterLiteral(DataTypePolicy.Policy policy, Instant instant) {
        return switch (policy.windowTimeDomain()) {
            case DATE -> instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
            case CIVIL -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
            case PHYSICAL -> DateTimeFormatter.ISO_INSTANT.format(instant);
        };
    }

    private static int pageSize(String dataType) {
        return dataType.equals("exercise") || dataType.equals("sleep") ? SESSION_PAGE_SIZE : PAGE_SIZE;
    }

    private static void putCivilDateTime(ObjectNode civilDateTime, LocalDate date) {
        ObjectNode dateNode = civilDateTime.putObject("date");
        dateNode.put("year", date.getYear());
        dateNode.put("month", date.getMonthValue());
        dateNode.put("day", date.getDayOfMonth());
        ObjectNode timeNode = civilDateTime.putObject("time");
        timeNode.put("hours", 0);
        timeNode.put("minutes", 0);
        timeNode.put("seconds", 0);
        timeNode.put("nanos", 0);
    }

    private static void addPageToken(HttpUrl.Builder url, String pageToken) {
        if (pageToken != null && !pageToken.isBlank()) {
            url.addQueryParameter("pageToken", pageToken);
        }
    }

    static Duration retryAfter(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException exception) {
            log.debug("Google Health Retry-After is not a delta-seconds value; trying HTTP-date: "
                      + "errorType={}, error={}",
                    exception.getClass().getSimpleName(), safeError(exception));
            try {
                Instant retryAt = ZonedDateTime.parse(
                        header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException dateException) {
                log.warn("Google Health returned an invalid Retry-After header; "
                         + "using bounded exponential backoff: errorType={}, error={}",
                        dateException.getClass().getSimpleName(), safeError(dateException));
                log.debug("Invalid Google Health Retry-After header exception", dateException);
                return null;
            }
        }
    }

    private static Duration retryAfter(String header) {
        return retryAfter(header, Instant.now());
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.info("Google Health API retry interrupted: errorType={}, error={}",
                    exception.getClass().getSimpleName(), safeError(exception));
            log.debug("Google Health API retry interruption", exception);
            throw new ApiException("Google Health retry was interrupted", 0, false, exception);
        }
    }

    private boolean isMissingScopeError(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            if (root == null || !root.isObject()) {
                return false;
            }
            JsonNode error = root.path("error");
            if (containsMissingScopeReason(error.get("status"))) {
                return true;
            }
            JsonNode details = error.path("details");
            if (!details.isArray()) {
                return false;
            }
            for (JsonNode detail : details) {
                if (containsMissingScopeReason(
                        detail.path("metadata").get(DETAILED_REASONS_FIELD))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException exception) {
            log.warn("Could not inspect a Google Health API error response for a missing scope; "
                     + "using generic status classification: errorType={}, error={}",
                    exception.getClass().getSimpleName(), "invalid JSON");
            return false;
        }
    }

    private static boolean containsMissingScopeReason(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (containsMissingScopeReason(item)) {
                    return true;
                }
            }
            return false;
        }
        if (!value.isTextual()) {
            return false;
        }
        String text = value.asText();
        return MISSING_SCOPE_REASONS.stream().anyMatch(reason ->
                Pattern.compile("(^|[^A-Z0-9_])" + Pattern.quote(reason) + "($|[^A-Z0-9_])")
                        .matcher(text)
                        .find());
    }

    private ApiException.Reason reasonFor(int status, String errorBody, boolean missingScope) {
        if (status == 400) {
            try {
                JsonNode error = objectMapper.readTree(errorBody);
                var codes = new LinkedHashSet<String>();
                JsonNode details = error.path("error").path("details");
                if (details.isArray()) for (JsonNode detail : details) {
                    addDiagnosticCodes(codes, detail.get("reason"));
                    addDiagnosticCodes(codes, detail.path("metadata").get(DETAILED_REASONS_FIELD));
                }
                if (codes.contains("INVALID_PAGE_TOKEN")) return ApiException.Reason.INVALID_PAGE_TOKEN;
            } catch (RuntimeException exception) {
                log.warn("Could not classify Google Health pagination error: type={}", exception.getClass().getSimpleName());
            }
            return ApiException.Reason.INVALID_REQUEST;
        }
        if (status == 401) return ApiException.Reason.AUTHORIZATION;
        if (status == 403 && missingScope) {
            return ApiException.Reason.MISSING_SCOPE;
        }
        if (status == 403 && (errorBody.contains("SERVICE_DISABLED")
                              || errorBody.contains("API has not been used"))) {
            return ApiException.Reason.SERVICE_DISABLED;
        }
        if (status == 403) return ApiException.Reason.POLICY_DENIED;
        if (status == 429) return ApiException.Reason.RATE_LIMIT;
        if (status >= SERVER_ERROR_STATUS) return ApiException.Reason.SERVER;
        return ApiException.Reason.OTHER;
    }

    private static String safeStatusMessage(int status, ApiException.Reason reason) {
        return switch (reason) {
            case INVALID_REQUEST, INVALID_PAGE_TOKEN -> "Google Health rejected the request";
            case AUTHORIZATION -> "Google Health authorization is no longer valid";
            case MISSING_SCOPE -> "The required Google Health category was not granted";
            case SERVICE_DISABLED -> "The Google Health API is not enabled for this OAuth project";
            case POLICY_DENIED -> "Google Health denied this request because of an account or project policy";
            case RATE_LIMIT -> "Google Health rate limit was reached";
            case SERVER -> "Google Health is temporarily unavailable";
            default -> switch (status) {
                case 400 -> "Google Health rejected the request";
                case 401 -> "Google Health authorization is no longer valid";
                case 403 -> "The required Google Health category was not granted";
                case 429 -> "Google Health rate limit was reached";
                default -> status >= SERVER_ERROR_STATUS ? "Google Health is temporarily unavailable" :
                        "Google Health request failed with status " + status;
            };
        };
    }

    private String readErrorBody(ResponseBody body) throws IOException {
        if (body == null) return "";
        byte[] consumed = body.byteStream().readNBytes((int) MAX_ERROR_BODY_BYTES);
        bytes += consumed.length;
        return new String(consumed, StandardCharsets.UTF_8);
    }

    private static String safeError(Throwable throwable) {
        return "Transport operation failed";
    }

    @FunctionalInterface
    private interface RequestFactory {
        Request.Builder builder();
    }

}
