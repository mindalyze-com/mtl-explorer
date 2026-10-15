package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class OAuthTokenClientTest {

    private static final MediaType JSON = MediaType.get("application/json");

    @Test
    void exchangesAuthorizationCodeWithPkceAndParsesGrantedScopes() {
        TokenEndpoint endpoint = new TokenEndpoint(200, """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "expires_in": 3600,
                  "refresh_token_expires_in": 604800,
                  "scope": "scope-one scope-two"
                }
                """);
        OAuthTokenClient client = client(endpoint);

        OAuthTokenClient.TokenResponse response =
                client.exchangeAuthorizationCode(
                        "authorization-code", "pkce-verifier", "http://127.0.0.1:1");

        assertThat(formValues(endpoint.request())).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "authorization-code",
                "client_id", "client-id",
                "client_secret", "client-secret",
                "redirect_uri", "http://127.0.0.1:1",
                "grant_type", "authorization_code",
                "code_verifier", "pkce-verifier"));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofHours(1));
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(7));
        assertThat(response.scopes()).containsExactlyInAnyOrder("scope-one", "scope-two");
    }

    @Test
    void refreshesWithOnlyTheRefreshGrantFields() {
        TokenEndpoint endpoint = new TokenEndpoint(200, """
                {"access_token":"refreshed-access","expires_in":120}
                """);
        OAuthTokenClient client = client(endpoint);

        OAuthTokenClient.TokenResponse response = client.refresh("stored-refresh-token");

        assertThat(formValues(endpoint.request())).containsExactlyInAnyOrderEntriesOf(Map.of(
                "client_id", "client-id",
                "client_secret", "client-secret",
                "refresh_token", "stored-refresh-token",
                "grant_type", "refresh_token"));
        assertThat(response.accessToken()).isEqualTo("refreshed-access");
        assertThat(response.refreshToken()).isNull();
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(2));
        assertThat(response.scopes()).isEmpty();
    }

    @Test
    void reportsPermanentTokenFailuresWithoutEchoingTheSensitiveResponse(CapturedOutput output) {
        TokenEndpoint endpoint = new TokenEndpoint(400, """
                {"error":"invalid_grant","error_description":"stored-refresh-token"}
                """);
        OAuthTokenClient client = client(endpoint);

        assertThatThrownBy(() -> client.refresh("stored-refresh-token"))
                .isInstanceOfSatisfying(OAuthException.class, exception -> {
                    assertThat(exception.isReconnectRequired()).isTrue();
                    assertThat(exception.getProviderErrorCode()).isEqualTo("invalid_grant");
                    assertThat(exception.getMessage())
                            .isEqualTo("Google authorization is no longer valid")
                            .doesNotContain("stored-refresh-token")
                            .doesNotContain("invalid_grant");
                });
        assertThat(output)
                .contains("providerErrorCode=invalid_grant")
                .doesNotContain("stored-refresh-token");
    }

    private static OAuthTokenClient client(TokenEndpoint endpoint) {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("http://localhost:18080/mtl/api/google-health/oauth/callback");
        properties.setTokenUrl("https://oauth.example.test/token");
        return new OAuthTokenClient(endpoint.client(), properties, new ObjectMapper());
    }

    private static Map<String, String> formValues(Request request) {
        assertThat(request).isNotNull();
        assertThat(request.body()).isInstanceOf(FormBody.class);
        FormBody form = (FormBody) request.body();
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < form.size(); index++) {
            values.put(form.name(index), form.value(index));
        }
        return values;
    }

    private static final class TokenEndpoint {
        private final AtomicReference<Request> request = new AtomicReference<>();
        private final OkHttpClient client;

        private TokenEndpoint(int status, String responseJson) {
            client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request captured = chain.request();
                        request.set(captured);
                        return new Response.Builder()
                                .request(captured)
                                .protocol(Protocol.HTTP_1_1)
                                .code(status)
                                .message(status >= 200 && status < 300 ? "OK" : "Error")
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
