package com.x8ing.mtl.server.mtlserver.googlehealth.client;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.AccessTokenProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncBudget;
import okhttp3.*;
import okio.Buffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiClientTest {

    @Test
    void budgetExpiringWhileCreatingCallNeverBecomesAnUnlimitedRequest() {
        var http = mock(OkHttpClient.class);
        var tokens = mock(AccessTokenProvider.class);
        when(tokens.accessToken()).thenReturn("synthetic");
        var call = mock(Call.class);
        var expired = new AtomicReference<SyncBudget>();
        when(http.newCall(any())).thenAnswer(invocation -> {
            expired.set(new SyncBudget(System.nanoTime() - 1));
            return call;
        });
        var client = new ApiClient(http, new ConnectorProperties(), tokens, new ObjectMapper());
        try (var parent = new SyncBudget(System.nanoTime() + Duration.ofSeconds(10).toNanos())) {
            try {
                Assertions.assertThatThrownBy(() -> client.listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                        .isInstanceOf(SyncBudget.Yield.class);
                verifyNoInteractions(call);
            } finally {
                if (expired.get() != null) expired.get().close();
            }
        }
    }

    @Test
    void successfulHttpStatusWithAnErrorPayloadCannotBecomeAnEmptyAnswer() {
        var requests = new AtomicInteger();
        AccessTokenProvider tokens = mock(AccessTokenProvider.class);
        when(tokens.accessToken()).thenReturn("synthetic");
        var client = clientWithResponses(requests, tokens, 200, "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}", 200, "{}");
        Assertions.assertThatThrownBy(() -> client.listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));
    }

    @Test
    void rateLimitCooldownIsDurableAndAResumedCycleMakesNoCalls() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("synthetic");
        ApiClient client = clientWithResponses(requests, tokenProvider, 429, "{}", 200, "{}");
        var repository = mock(MirrorRepository.class);
        client.configureLimiter(repository);
        client.beginCycle();
        Assertions.assertThatThrownBy(() -> client.listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null)).isInstanceOf(ApiClient.QuotaPause.class);
        var cooldown = ArgumentCaptor.forClass(Instant.class);
        verify(repository).saveCooldown(cooldown.capture());
        when(repository.cooldown()).thenReturn(cooldown.getValue());
        client.beginCycle();
        assertThat(client.coolingDown()).isTrue();
        Assertions.assertThatThrownBy(() -> client.listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null)).isInstanceOf(ApiClient.QuotaPause.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    private static final MediaType JSON = MediaType.get("application/json");
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-02T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsReviewedFiltersForEveryGoogleRecordShape() {
        RequestCapture capture = new RequestCapture("{\"dataPoints\":[],\"nextPageToken\":\"next\"}");
        ApiClient client = client(capture);

        ApiClient.Page exercisePage = client.listRaw(
                policy("exercise", DataTypePolicy.RecordType.SESSION_START), START, END, null);
        assertThat(capture.request().url().queryParameter("filter"))
                .isEqualTo("exercise.interval.civil_start_time >= \"2026-08-01T00:00:00\" "
                           + "AND exercise.interval.civil_start_time < \"2026-08-02T00:00:00\"");
        assertThat(capture.request().url().queryParameter("pageSize")).isEqualTo("25");
        assertThat(capture.request().header("Authorization")).isEqualTo("Bearer access-token");
        assertThat(exercisePage.nextPageToken()).isEqualTo("next");

        client.listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null);
        assertThat(capture.request().url().queryParameter("filter"))
                .startsWith("sleep.interval.end_time >=");

        client.listRaw(policy("active-energy-burned", DataTypePolicy.RecordType.INTERVAL), START, END, null);
        assertThat(capture.request().url().queryParameter("filter"))
                .startsWith("active_energy_burned.interval.start_time >=");

        client.listRaw(policy("oxygen-saturation", DataTypePolicy.RecordType.SAMPLE), START, END, null);
        assertThat(capture.request().url().queryParameter("filter"))
                .startsWith("oxygen_saturation.sample_time.physical_time >=");

        client.listRaw(policy("daily-vo2-max", DataTypePolicy.RecordType.DAILY), START, END, null);
        assertThat(capture.request().url().queryParameter("filter"))
                .startsWith("daily_vo2_max.date >= \"2026-08-01\"");
    }

    @Test
    void sendsAndParsesDailyRollupPagination() throws IOException {
        RequestCapture capture = new RequestCapture("""
                {"rollupDataPoints":[{"steps":{"count":"0"}}],"nextPageToken":"page-2"}
                """);
        ApiClient client = client(capture);

        DataTypePolicy.Policy stepsPolicy = new DataTypePolicy.Policy(
                "steps", DataTypePolicy.Mode.DAILY_ROLLUP, DataTypePolicy.RecordType.DAILY,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
        ApiClient.Page page = client.dailyRollUp(
                stepsPolicy, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), "page-1");

        Buffer requestBuffer = new Buffer();
        capture.request().body().writeTo(requestBuffer);
        JsonNode requestBody = new ObjectMapper().readTree(requestBuffer.readUtf8());
        assertThat(capture.request().method()).isEqualTo("POST");
        assertThat(capture.request().url().encodedPath())
                .isEqualTo("/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp");
        assertThat(requestBody.path("range").path("start").path("date").path("day").asInt()).isEqualTo(1);
        assertThat(requestBody.path("range").path("end").path("date").path("day").asInt()).isEqualTo(2);
        assertThat(requestBody.path("range").path("start").path("time").isObject()).isTrue();
        assertThat(requestBody.path("range").path("start").path("time").path("hours").asInt()).isZero();
        assertThat(requestBody.path("range").path("end").path("time").isObject()).isTrue();
        assertThat(requestBody.path("windowSizeDays").asInt()).isEqualTo(1);
        assertThat(requestBody.path("pageSize").asInt()).isEqualTo(90);
        assertThat(requestBody.path("pageToken").asText()).isEqualTo("page-1");
        assertThat(page.points()).hasSize(1);
        assertThat(page.nextPageToken()).isEqualTo("page-2");

        DataTypePolicy.Policy heartRatePolicy = new DataTypePolicy.Policy(
                "heart-rate", DataTypePolicy.Mode.DAILY_ROLLUP, DataTypePolicy.RecordType.DAILY,
                OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, 14);
        client.dailyRollUp(
                heartRatePolicy, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null);
        requestBuffer = new Buffer();
        capture.request().body().writeTo(requestBuffer);
        requestBody = new ObjectMapper().readTree(requestBuffer.readUtf8());
        assertThat(requestBody.path("pageSize").asInt()).isEqualTo(14);
    }

    @Test
    void reconcilesSupportedIntradayTypesAcrossAllSources() {
        RequestCapture capture = new RequestCapture("{\"dataPoints\":[],\"nextPageToken\":\"page-2\"}");
        ApiClient client = client(capture);
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "activity-level", DataTypePolicy.Mode.RECONCILED_INTERVAL,
                DataTypePolicy.RecordType.INTERVAL,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);

        ApiClient.Page page = client.reconcile(policy, START, END, "page-1");

        assertThat(capture.request().url().encodedPath())
                .isEqualTo("/v4/users/me/dataTypes/activity-level/dataPoints:reconcile");
        assertThat(capture.request().url().queryParameter("filter"))
                .startsWith("activity_level.interval.start_time >=");
        assertThat(capture.request().url().queryParameter("dataSourceFamily"))
                .isEqualTo(ApiClient.ALL_SOURCES);
        assertThat(capture.request().url().queryParameter("pageToken")).isEqualTo("page-1");
        assertThat(page.nextPageToken()).isEqualTo("page-2");
    }

    @Test
    void acceptsOmittedProtoJsonDefaultsAsAnEmptyCompletedPage() {
        ApiClient.Page page = client(new RequestCapture("{}"))
                .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null);

        assertThat(page.points()).isEmpty();
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void rejectsMalformedSuccessfulPageShapes() {
        Assertions.assertThatThrownBy(() -> client(new RequestCapture(""))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));

        Assertions.assertThatThrownBy(() -> client(new RequestCapture("[]"))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));

        Assertions.assertThatThrownBy(() -> client(
                        new RequestCapture("{\"dataPoints\":{}}"))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));

        Assertions.assertThatThrownBy(() -> client(
                        new RequestCapture("{\"dataPoints\":[42]}"))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));

        Assertions.assertThatThrownBy(() -> client(
                        new RequestCapture("{\"dataPoints\":[],\"nextPageToken\":42}"))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE));
    }

    @Test
    void rejectsAnOversizedPageToken() {
        String response = "{\"dataPoints\":[],\"nextPageToken\":\"" + "x".repeat(8 * 1024 + 1) + "\"}";

        Assertions.assertThatThrownBy(() -> client(new RequestCapture(response))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE);
                    assertThat(exception.getMessage()).contains("page token");
                });
    }

    @Test
    void rejectsAResponseThatExceedsTheRequestedPageSize() {
        StringBuilder response = new StringBuilder("{\"dataPoints\":[");
        for (int index = 0; index < 26; index++) {
            if (index > 0) response.append(',');
            response.append("{}");
        }
        response.append("]}");

        Assertions.assertThatThrownBy(() -> client(new RequestCapture(response.toString()))
                        .listRaw(policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE);
                    assertThat(exception.getMessage()).contains("result limit");
                });
    }

    @Test
    void rejectsAnExerciseExportLargerThanTheConfiguredLimit() {
        RequestCapture capture = new RequestCapture("oversized-tcx");
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(1);
        properties.setMaxTcxBytes(4L);
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("access-token");
        ApiClient client = new ApiClient(
                capture.client(), properties, tokenProvider, new ObjectMapper());

        Assertions.assertThatThrownBy(() -> client.downloadExerciseTcx(
                        "users/me/dataTypes/exercise/dataPoints/route-1",
                        temporaryDirectory.resolve("route.tcx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void retriesRateLimitsAndTransportFailuresWithinTheConfiguredAttemptBound() {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int attempt = requests.incrementAndGet();
                    if (attempt == 1) {
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(429)
                                .message("Rate limited")
                                .header("Retry-After", "0")
                                .body(ResponseBody.create("{}", JSON))
                                .build();
                    }
                    if (attempt == 2) {
                        throw new IOException("temporary transport failure");
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("{\"dataPoints\":[]}", JSON))
                            .build();
                })
                .build();
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(3);
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("access-token");
        ApiClient client = new ApiClient(
                httpClient, properties, tokenProvider, new ObjectMapper());

        ApiClient.Page page = client.listRaw(
                policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null);

        assertThat(page.points()).isEmpty();
        assertThat(requests).hasValue(3);
    }

    @Test
    void refreshesAuthorizationOnlyOnceAfterUnauthorizedResponse() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("stale-token", "fresh-token");
        ApiClient client = clientWithResponses(requests, tokenProvider, 401, "{}", 200, "{\"dataPoints\":[]}");

        assertThat(client.listRaw(
                policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null).points()).isEmpty();

        assertThat(requests).hasValue(2);
        verify(tokenProvider).invalidate();
    }

    @Test
    void keepsTheOneExtraAuthorizationAttemptInsideTheTransportRetryBound() {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int attempt = requests.incrementAndGet();
                    if (attempt == 2) throw new IOException("temporary transport failure");
                    int status = attempt == 1 ? 401 : 200;
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message(status == 200 ? "OK" : "Unauthorized")
                            .body(ResponseBody.create(status == 200 ? "{\"dataPoints\":[]}" : "{}", JSON))
                            .build();
                })
                .build();
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(2);
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("stale-token", "fresh-token");
        ApiClient client = new ApiClient(httpClient, properties, tokenProvider, new ObjectMapper());

        assertThat(client.listRaw(
                policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null).points()).isEmpty();

        assertThat(requests).hasValue(3);
        verify(tokenProvider).invalidate();
    }

    @Test
    void refreshesOnceForTheStructuredMissingScopeReason() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("stale-token", "fresh-token");
        ApiClient client = clientWithResponses(
                requests, tokenProvider,
                403, missingScopeResponse(),
                200, "{\"dataPoints\":[]}");

        assertThat(client.listRaw(
                policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null).points()).isEmpty();

        assertThat(requests).hasValue(2);
        verify(tokenProvider).invalidate();
    }

    @Test
    void reportsMissingScopeWhenTheExactScopeErrorRemainsAfterOneRefresh() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("stale-token", "fresh-token");
        String response = missingScopeResponse();
        ApiClient client = clientWithResponses(
                requests, tokenProvider,
                403, response,
                403, response);

        Assertions.assertThatThrownBy(() -> client.listRaw(
                        policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.MISSING_SCOPE));

        assertThat(requests).hasValue(2);
        verify(tokenProvider).invalidate();
    }

    @Test
    void doesNotRefreshForPolicyDeniedForbiddenResponse() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("access-token");
        ApiClient client = clientWithResponses(
                requests, tokenProvider, 403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}",
                200, "{\"dataPoints\":[]}");

        Assertions.assertThatThrownBy(() -> client.listRaw(
                        policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo(ApiException.Reason.POLICY_DENIED));

        assertThat(requests).hasValue(1);
        verify(tokenProvider, never()).invalidate();
    }

    @Test
    void keepsCompatibilityWithTheLegacyMissingScopeStatus() {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("stale-token", "fresh-token");
        ApiClient client = clientWithResponses(
                requests, tokenProvider,
                403, "{\"error\":{\"status\":\"ACCESS_TOKEN_SCOPE_INSUFFICIENT\"}}",
                200, "{\"dataPoints\":[]}");

        assertThat(client.listRaw(
                policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null).points()).isEmpty();

        assertThat(requests).hasValue(2);
        verify(tokenProvider).invalidate();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsProviderErrorCodesWithoutResponseMessagesOrTokens(CapturedOutput output) {
        AtomicInteger requests = new AtomicInteger();
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("private-access-token");
        ApiClient client = clientWithResponses(requests, tokenProvider, 400, """
                {"error":{"status":"INVALID_ARGUMENT","message":"private-health-data private-access-token",
                  "details":[{"reason":"INVALID_PAGE_TOKEN","metadata":{
                    "detailedReasons":"[INVALID_PAGE_TOKEN, REQUEST_PARAMETER_MISMATCH]",
                    "pageToken":"private-page-token"}},
                    {"reason":"private-sensitive-value","metadata":{
                      "detailedReasons":["INVALID_FILTER", "private-health-data"]}}]}}
                """, 200, "{}");

        Assertions.assertThatThrownBy(() -> client.listRaw(
                        policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, "private-page-token"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getReason()).isEqualTo(ApiException.Reason.INVALID_PAGE_TOKEN);
                    assertThat(exception.getMessage()).isEqualTo("Google Health rejected the request");
                });

        assertThat(output).contains("method=GET", "status=400", "reason=INVALID_PAGE_TOKEN",
                "INVALID_ARGUMENT", "INVALID_PAGE_TOKEN", "REQUEST_PARAMETER_MISMATCH", "INVALID_FILTER");
        assertThat(output).doesNotContain("private-access-token", "private-page-token", "private-health-data",
                "private-sensitive-value", "filter=");
        assertThat(requests).hasValue(1);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void malformedErrorBodyStillLogsStatusWithoutEchoingContent(CapturedOutput output) {
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("access-token");
        ApiClient client = clientWithResponses(new AtomicInteger(), tokenProvider,
                400, "not-json-private-data", 200, "{}");

        Assertions.assertThatThrownBy(() -> client.listRaw(
                        policy("sleep", DataTypePolicy.RecordType.SESSION_END), START, END, null))
                .isInstanceOf(ApiException.class);

        assertThat(output).contains("status=400", "providerDetails=invalid JSON", "errorType=");
        assertThat(output).doesNotContain("not-json-private-data");
    }

    @Test
    void parsesRetryAfterSecondsAndHttpDates() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");

        assertThat(ApiClient.retryAfter("17", now)).isEqualTo(Duration.ofSeconds(17));
        assertThat(ApiClient.retryAfter("Tue, 1 Sep 2026 12:00:23 GMT", now))
                .isEqualTo(Duration.ofSeconds(23));
        assertThat(ApiClient.retryAfter("Tue, 1 Sep 2026 11:59:59 GMT", now)).isZero();
    }

    private static ApiClient client(RequestCapture capture) {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(1);
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("access-token");
        return new ApiClient(capture.client(), properties, tokenProvider, new ObjectMapper());
    }

    private static ApiClient clientWithResponses(
            AtomicInteger requests,
            AccessTokenProvider tokenProvider,
            int firstStatus,
            String firstBody,
            int secondStatus,
            String secondBody) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int attempt = requests.incrementAndGet();
                    int status = attempt == 1 ? firstStatus : secondStatus;
                    String body = attempt == 1 ? firstBody : secondBody;
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message(status >= 200 && status < 300 ? "OK" : "Failed")
                            .body(ResponseBody.create(body, JSON))
                            .build();
                })
                .build();
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(1);
        return new ApiClient(httpClient, properties, tokenProvider, new ObjectMapper());
    }

    private static DataTypePolicy.Policy policy(
            String dataType, DataTypePolicy.RecordType recordType) {
        return new DataTypePolicy.Policy(
                dataType, DataTypePolicy.Mode.RAW, recordType,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
    }

    private static String missingScopeResponse() {
        return """
                {"error":{"status":"PERMISSION_DENIED","details":[
                  {"metadata":{"detailedReasons":"[MISSING_OAUTH_SCOPE]"}}
                ]}}
                """;
    }

    private static final class RequestCapture {
        private final AtomicReference<Request> request = new AtomicReference<>();
        private final OkHttpClient client;

        private RequestCapture(String responseJson) {
            client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request captured = chain.request();
                        request.set(captured);
                        return new Response.Builder()
                                .request(captured)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(responseJson, JSON))
                                .build();
                    })
                    .build();
        }

        private OkHttpClient client() {
            return client;
        }

        private Request request() {
            return request.get();
        }
    }
}
