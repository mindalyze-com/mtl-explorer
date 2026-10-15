package com.x8ing.mtl.server.mtlserver.googlehealth.client;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.AccessTokenProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.WorkRetryPolicy;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiClientResponseBoundsTest {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final Instant START = Instant.parse("2045-09-01T00:00:00Z");
    private static final DataTypePolicy.Policy POLICY = new DataTypePolicy.Policy(
            "sleep", DataTypePolicy.Mode.RAW, DataTypePolicy.RecordType.SESSION_END, OAuthScopes.SLEEP, 90);

    @Test
    void declaredOversizedBodiesAreRejectedWithoutReadingThem() {
        Buffer input = new Buffer().writeUtf8("{\"dataPoints\":[]}");
        ApiClient client = client(responseBody(input, 1_000), 64);

        assertThatThrownBy(() -> read(client)).isInstanceOfSatisfying(ApiException.class, failure ->
                assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse())
                .hasMessageContaining("JSON exceeds the configured size limit");
        assertThat(input.size()).isEqualTo(17L);
    }

    @Test
    void unknownLengthBodiesAreStoppedAtTheByteLimitIncludingSkippedMetadata() {
        Buffer input = new Buffer().writeUtf8("{\"dataPoints\":[],\"metadata\":\"" + "x".repeat(4_096) + "\"}");
        long originalBytes = input.size();
        ApiClient client = client(responseBody(input, -1), 64);

        assertThatThrownBy(() -> read(client)).isInstanceOfSatisfying(ApiException.class, failure ->
                assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse())
                .hasMessageContaining("JSON exceeds the configured size limit");
        assertThat(originalBytes - input.size()).isEqualTo(65L);
    }

    @Test
    void exactByteLimitIsAcceptedAndPaginationSurvivesStreaming() {
        Buffer input = new Buffer().writeUtf8("{\"dataPoints\":[{}],\"nextPageToken\":\"next\"}");
        ApiClient client = client(responseBody(input, -1), input.size());

        ApiClient.Page page = read(client);

        assertThat(page.points()).hasSize(1);
        assertThat(page.nextPageToken()).isEqualTo("next");
    }

    @Test
    void theCollectionLimitIsAppliedBeforeParsingTheExcessRecord() {
        String response = "{\"dataPoints\":[" + "{},".repeat(25) + "{\"private\":unparseable}]}";
        ApiClient client = client(ResponseBody.create(response, JSON), 4_096);

        assertThatThrownBy(() -> read(client)).isInstanceOfSatisfying(ApiException.class, failure ->
                assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse())
                .hasMessageContaining("page exceeds the requested result limit");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void malformedJsonDoesNotLeakProviderValuesThroughExceptionMessagesOrCauses(CapturedOutput output) {
        ApiClient client = client(ResponseBody.create("{\"dataPoints\":[{\"value\":private_health_value}]}", JSON), 4_096);

        assertThatThrownBy(() -> read(client)).isInstanceOfSatisfying(ApiException.class, failure ->
                assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse())
                .hasMessageContaining("malformed or incomplete JSON").hasNoCause();
        assertThat(output).contains("reason=invalid JSON").doesNotContain("private_health_value");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ExtendWith(OutputCaptureExtension.class)
    void malformedCharacterEncodingUsesBoundedRetriesWithoutLeakingProviderValues(boolean utf32, CapturedOutput output) {
        String prefix = "{\"dataPoints\":[{\"private_health_value\":\"";
        Buffer input = new Buffer();
        if (utf32) {
            input.writeInt(0x0000feff).write(prefix.getBytes(Charset.forName("UTF-32BE"))).writeInt(0x110000);
        } else {
            input.writeUtf8(prefix).writeByte(0xc3).writeByte('(');
        }

        assertThatThrownBy(() -> read(client(responseBody(input, input.size()), 4_096)))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE);
                    assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse();
                }).hasMessageContaining("malformed or incomplete JSON").hasNoCause();
        assertThat(output).contains("reason=invalid JSON").doesNotContain("private_health_value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"dataPoints\":[],\"dataPoints\":[]}", "{\"dataPoints\":42}"})
    void invalidCollectionsUseBoundedWorkRetries(String response) {
        assertThatThrownBy(() -> read(client(ResponseBody.create(response, JSON), 4_096)))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.getReason()).isEqualTo(ApiException.Reason.INVALID_RESPONSE);
                    assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse();
                });
    }

    @Test
    void oversizedPageTokensUseBoundedWorkRetries() {
        String response = "{\"dataPoints\":[],\"nextPageToken\":\"" + "x".repeat(8 * 1_024 + 1) + "\"}";
        assertThatThrownBy(() -> read(client(ResponseBody.create(response, JSON), 16_384)))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.getMessage()).contains("page token");
                    assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse();
                });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void interruptedResponseBodiesStayRetryableWithoutLeakingParserOrTransportDetails(CapturedOutput output) {
        BufferedSource input = Okio.buffer(new Source() {
            private final Buffer partial = new Buffer().writeUtf8("{\"dataPoints\":[{\"private_health_value\":");

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                if (partial.size() > 0) return partial.read(sink, byteCount);
                throw new SocketException("private_transport_detail");
            }

            @Override
            public Timeout timeout() {
                return Timeout.NONE;
            }

            @Override
            public void close() {
                partial.clear();
            }
        });
        assertThatThrownBy(() -> read(client(responseBody(input, -1), 4_096)))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.getReason()).isEqualTo(ApiException.Reason.TRANSPORT);
                    assertThat(WorkRetryPolicy.isRecoverable(failure)).isTrue();
                }).hasMessage("Google Health request timed out or failed").hasNoCause();
        assertThat(output).contains("reason=transport")
                .doesNotContain("private_health_value", "private_transport_detail");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothTcxSizeChecksUseBoundedWorkRetries(boolean unknownLength, @TempDir Path temporary) {
        Buffer input = new Buffer().writeUtf8("oversized-tcx");
        ConnectorProperties properties = new ConnectorProperties();
        properties.setMaxTcxBytes(4);
        ApiClient client = client(responseBody(input, unknownLength ? -1 : input.size()), properties);
        assertThatThrownBy(() -> client.downloadExerciseTcx("users/me/dataTypes/exercise/dataPoints/route-1", temporary.resolve("route.tcx")))
                .isInstanceOfSatisfying(IllegalArgumentException.class, failure ->
                        assertThat(WorkRetryPolicy.isRecoverable(failure)).isFalse())
                .hasMessageContaining("TCX exceeds the configured size limit");
    }

    @Test
    void aLongProviderCooldownCanStillBeCancelledWithoutIssuingAnotherRequest() {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            Thread.currentThread().interrupt();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(429).message("Rate limited").header("Retry-After", "300")
                    .body(ResponseBody.create("{}", JSON)).build();
        }).build();
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        properties.setMaxRetryAttempts(2);
        AccessTokenProvider tokens = mock(AccessTokenProvider.class);
        when(tokens.accessToken()).thenReturn("synthetic-access");
        ApiClient client = new ApiClient(http, properties, tokens, new ObjectMapper());
        try {
            assertThatThrownBy(() -> read(client)).isInstanceOf(CancellationException.class)
                    .hasMessage("Sync cancelled");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(requests.get()).isEqualTo(1);
            assertThat(ApiRetryPolicy.delayForAttempt(0, Duration.ofMinutes(5)))
                    .isEqualTo(Duration.ofMinutes(5));
        } finally {
            Thread.interrupted();
        }
    }

    private static ApiClient.Page read(ApiClient client) {
        return client.listRaw(POLICY, START, START.plus(Duration.ofDays(1)), null);
    }

    private static ApiClient client(ResponseBody responseBody, long maxBytes) {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setMaxJsonBytes(maxBytes);
        return client(responseBody, properties);
    }

    private static ApiClient client(ResponseBody responseBody, ConnectorProperties properties) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body(responseBody).build()).build();
        properties.setApiBaseUrl("https://health.example.test");
        AccessTokenProvider tokens = mock(AccessTokenProvider.class);
        when(tokens.accessToken()).thenReturn("synthetic-access");
        return new ApiClient(http, properties, tokens, new ObjectMapper());
    }

    private static ResponseBody responseBody(BufferedSource input, long declaredLength) {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public long contentLength() {
                return declaredLength;
            }

            @Override
            public BufferedSource source() {
                return input;
            }
        };
    }
}
