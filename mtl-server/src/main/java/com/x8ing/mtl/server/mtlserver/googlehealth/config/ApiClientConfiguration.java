package com.x8ing.mtl.server.mtlserver.googlehealth.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiClientConfiguration {

    @Bean("googleHealthHttpClient")
    public OkHttpClient googleHealthHttpClient(ConnectorProperties properties) {
        return new OkHttpClient.Builder()
                .callTimeout(properties.getRequestTimeout())
                .connectTimeout(properties.getRequestTimeout())
                .readTimeout(properties.getRequestTimeout())
                .writeTimeout(properties.getRequestTimeout())
                .build();
    }
}
