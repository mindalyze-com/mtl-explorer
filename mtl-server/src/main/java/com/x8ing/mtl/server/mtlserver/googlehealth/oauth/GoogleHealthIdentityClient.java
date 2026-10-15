package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@Slf4j
public class GoogleHealthIdentityClient {

    private final OkHttpClient httpClient;
    private final ConnectorProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleHealthIdentityClient(
            @Qualifier("googleHealthHttpClient") OkHttpClient httpClient,
            ConnectorProperties properties,
            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String healthUserId(String accessToken) {
        HttpUrl url = HttpUrl.get(properties.getApiBaseUrl()).newBuilder()
                .addPathSegments("v4/users/me/identity")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful()) {
                log.warn("Google Health identity request failed: status={}", response.code());
                throw new OAuthException(
                        "Google Health could not confirm the selected account", response.code() == 401);
            }
            JsonNode node = objectMapper.readTree(responseBody == null ? "{}" : responseBody.string());
            String healthUserId = node.path("healthUserId").asText(null);
            if (healthUserId == null || healthUserId.isBlank()) {
                throw new OAuthException("Google Health did not return an account identity", false);
            }
            return healthUserId;
        } catch (OAuthException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OAuthException("Could not confirm the Google Health account", false, exception);
        }
    }
}
