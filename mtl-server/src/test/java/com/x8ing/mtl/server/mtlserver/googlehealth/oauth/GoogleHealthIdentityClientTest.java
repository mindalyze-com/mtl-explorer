package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleHealthIdentityClientTest {

    private static final MediaType JSON = MediaType.get("application/json");

    @Test
    void resolvesTheStableGoogleHealthUserId() {
        AtomicReference<Request> request = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    request.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("{\"healthUserId\":\"health-user-123\"}", JSON))
                            .build();
                })
                .build();
        ConnectorProperties properties = new ConnectorProperties();
        properties.setApiBaseUrl("https://health.example.test");
        GoogleHealthIdentityClient client = new GoogleHealthIdentityClient(
                httpClient, properties, new ObjectMapper());

        assertThat(client.healthUserId("access-token")).isEqualTo("health-user-123");
        assertThat(request.get().url().encodedPath()).isEqualTo("/v4/users/me/identity");
        assertThat(request.get().header("Authorization")).isEqualTo("Bearer access-token");
    }
}
