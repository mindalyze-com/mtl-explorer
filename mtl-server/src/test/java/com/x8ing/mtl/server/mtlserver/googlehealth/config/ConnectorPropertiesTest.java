package com.x8ing.mtl.server.mtlserver.googlehealth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ConnectorPropertiesTest {

    @Test
    void startupSummaryNamesMissingSettingsWithoutLoggingConfiguredSecrets(CapturedOutput output) {
        ConnectorProperties disabled = new ConnectorProperties();
        disabled.setEnabled(false);
        disabled.logConfigurationState();

        ConnectorProperties missing = new ConnectorProperties();
        missing.logConfigurationState();

        ConnectorProperties configured = new ConnectorProperties();
        configured.setClientId("sensitive-client-id");
        configured.setClientSecret("sensitive-client-secret");
        configured.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        configured.logConfigurationState();

        assertThat(output)
                .contains("Google Health connector is disabled")
                .contains("Google Health connector is enabled but not configured")
                .contains("WARN")
                .contains("CLIENT_ID_MISSING", "CLIENT_SECRET_MISSING")
                .contains("Google Health connector configured")
                .doesNotContain("sensitive-client-id")
                .doesNotContain("sensitive-client-secret");
    }

    @Test
    void manualModeOnlyRequiresTheTwoGoogleOAuthCredentials() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.configurationIssues()).isEmpty();
        assertThat(properties.oauthMode()).isEqualTo(ConnectorProperties.OAuthMode.MANUAL);
        assertThat(properties.resolvedRedirectUri()).isEqualTo("http://127.0.0.1:1/mtl-explorer-oauth");
    }

    @Test
    void configuredRedirectSelectsProxyMode() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setRedirectUri("https://trails.example.org/mtl/api/google-health/oauth/callback");

        assertThat(properties.oauthMode()).isEqualTo(ConnectorProperties.OAuthMode.PROXY);
        assertThat(properties.resolvedRedirectUri())
                .isEqualTo("https://trails.example.org/mtl/api/google-health/oauth/callback");
    }

    @Test
    void reportsConfigurationIssuesInSetupOrder() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setRedirectUri("https://example.com/wrong-callback");

        assertThat(properties.configurationIssues()).containsExactly(
                ConnectorProperties.ConfigurationIssue.CLIENT_ID_MISSING,
                ConnectorProperties.ConfigurationIssue.CLIENT_SECRET_MISSING,
                ConnectorProperties.ConfigurationIssue.REDIRECT_URI_INVALID);
    }

    @Test
    void validatesGoogleRedirectUriRequirements() {
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "http://localhost:18080/mtl/api/google-health/oauth/callback")).isTrue();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "http://127.0.0.1:18080/mtl/api/google-health/oauth/callback")).isTrue();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "http://[::1]:18080/mtl/api/google-health/oauth/callback")).isTrue();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://trails.example.org/mtl/api/google-health/oauth/callback")).isTrue();

        assertThat(ConnectorProperties.hasValidRedirectUri(
                "http://trails.example.org/mtl/api/google-health/oauth/callback")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://192.168.1.20/mtl/api/google-health/oauth/callback")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://[2001:db8::1]/mtl/api/google-health/oauth/callback")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://user@trails.example.org/mtl/api/google-health/oauth/callback")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://trails.example.org/mtl/api/google-health/oauth/callback?next=/health")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://trails.example.org/mtl/api/google-health/oauth/callback#result")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://trails.example.org/mtl/oauth/callback")).isFalse();
        assertThat(ConnectorProperties.hasValidRedirectUri(
                "https://trails.example.org/mtl/api/google%2dhealth/oauth/callback")).isFalse();
    }

    @Test
    void bindsTheDocumentedDockerEnvironmentVariableNames() {
        var environment = new SystemEnvironmentPropertySource("google-health-test", Map.of(
                "MTL_GOOGLE_HEALTH_CLIENT_ID", "client.apps.googleusercontent.com",
                "MTL_GOOGLE_HEALTH_CLIENT_SECRET", "secret",
                "MTL_GOOGLE_HEALTH_DATABASE_TRANSACTION_TIMEOUT", "PT10M",
                "MTL_GOOGLE_HEALTH_REDIRECT_URI", "http://localhost:18080/mtl/api/google-health/oauth/callback",
                "MTL_GOOGLE_HEALTH_STAGING_DIRECTORY", "/app/google-health-staging"));

        ConnectorProperties properties = new Binder(ConfigurationPropertySources.from(environment))
                .bind("mtl.google-health", Bindable.of(ConnectorProperties.class))
                .orElseThrow(AssertionError::new);

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.getClientId()).isEqualTo("client.apps.googleusercontent.com");
        assertThat(properties.getStagingDirectory()).isEqualTo("/app/google-health-staging");
        assertThat(properties.getDatabaseTransactionTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void invalidRequestAndDatabaseLimitsFailBeforeSynchronization() {
        var properties = new ConnectorProperties();
        properties.setRequestTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validateSynchronization).hasMessageContaining("request-timeout");
        properties.setRequestTimeout(Duration.ofSeconds(30));
        for (Duration invalid : new Duration[]{null, Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds((long) Integer.MAX_VALUE + 1)}) {
            properties.setDatabaseTransactionTimeout(invalid);
            assertThatThrownBy(properties::validateSynchronization).hasMessageContaining("database-transaction-timeout");
        }
    }
}
