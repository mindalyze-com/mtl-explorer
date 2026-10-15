package com.x8ing.mtl.server.mtlserver.googlehealth.config;

import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthPaths;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Data
@Component
@Slf4j
@ConfigurationProperties(prefix = "mtl.google-health")
public class ConnectorProperties {

    private static final String DEFAULT_API_BASE_URL = "https://health.googleapis.com";
    private static final String DEFAULT_AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private boolean enabled = true;
    private String apiBaseUrl = DEFAULT_API_BASE_URL;
    private String authorizationUrl = DEFAULT_AUTHORIZATION_URL;
    private String tokenUrl = DEFAULT_TOKEN_URL;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private Duration syncInterval = Duration.ofMinutes(30);
    private Period recentWindow = Period.ofDays(7);
    private Duration recentFastWindow = Duration.ofHours(24);
    private Duration recentFullInterval = Duration.ofHours(24);
    private Duration cycleDeadlineMargin = Duration.ofMinutes(1);
    private double historyBudgetFraction = 0.5;
    private int workFailureLimit = 4;
    private Duration workRetryMaxDelay = Duration.ofHours(6);
    private Duration historyStreamQuantum = Duration.ofMinutes(1);
    private LocalDate historyStartDate = LocalDate.of(2010, 1, 1);
    private int maxChunkRows = 100_000;
    private long maxChunkBytes = 64L * 1024L * 1024L;
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration databaseTransactionTimeout = Duration.ofMinutes(5);
    private int maxRetryAttempts = 5;
    private int maxPagesPerWindow = 1_000;
    private long maxJsonBytes = 32L * 1024L * 1024L;
    private long maxTcxBytes = 50L * 1024L * 1024L;
    private String routeDirectory = "google-health";
    private String stagingDirectory;

    @PostConstruct
    void logConfigurationState() {
        validateSynchronization();
        switch (configurationState()) {
            case DISABLED -> log.info(
                    "Google Health connector is disabled; MTL Explorer will run without Google Health jobs or remote requests");
            case MISSING_CONFIGURATION -> log.warn(
                    "Google Health connector is enabled but not configured; connection and sync are unavailable, missingOrInvalid={}",
                    configurationIssues());
            case CONFIGURED -> log.info(
                    "Google Health connector configured: oauthMode={}, redirectUri={}, syncInterval={}, recentWindow={}, "
                    + "historyStartDate={}, routeDirectory={}, requestTimeout={}, databaseTransactionTimeout={}, maxRetryAttempts={}, maxPagesPerWindow={}, "
                    + "workFailureLimit={}, workRetryMaxDelay={}",
                    oauthMode(), resolvedRedirectUri(), syncInterval, recentWindow, historyStartDate, routeDirectory,
                    requestTimeout, databaseTransactionTimeout, maxRetryAttempts, maxPagesPerWindow, workFailureLimit, workRetryMaxDelay);
        }
    }

    public void validateSynchronization() {
        if (recentWindow == null || recentWindow.getYears() != 0 || recentWindow.getMonths() != 0
            || recentWindow.getDays() < 2) {
            throw new IllegalArgumentException("recent-window must contain at least two whole days");
        }
        requirePositive(syncInterval, "sync-interval");
        requirePositive(recentFastWindow, "recent-fast-window");
        requirePositive(recentFullInterval, "recent-full-interval");
        requirePositive(cycleDeadlineMargin, "cycle-deadline-margin");
        requirePositive(historyStreamQuantum, "history-stream-quantum");
        requirePositive(workRetryMaxDelay, "work-retry-max-delay");
        requirePositive(requestTimeout, "request-timeout");
        requirePositive(databaseTransactionTimeout, "database-transaction-timeout");
        if (databaseTransactionTimeout.compareTo(Duration.ofSeconds(1)) < 0
            || databaseTransactionTimeout.compareTo(Duration.ofSeconds(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("database-transaction-timeout must be between one second and " + Integer.MAX_VALUE + " seconds");
        }
        if (recentFastWindow.compareTo(syncInterval) < 0
            || recentFastWindow.compareTo(Duration.ofDays(recentWindow.getDays())) > 0
            || cycleDeadlineMargin.compareTo(syncInterval) >= 0
            || !(historyBudgetFraction > 0 && historyBudgetFraction < 1)
            || workFailureLimit < 1 || workRetryMaxDelay.compareTo(syncInterval) < 0
            || maxChunkRows < 1 || maxChunkBytes < 1
            || maxPagesPerWindow < 1 || maxJsonBytes < 1 || maxTcxBytes < 1
            || maxRetryAttempts < 1 || historyStartDate == null) {
            throw new IllegalArgumentException("Invalid Google Health synchronization limits");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public boolean isConfigured() {
        return enabled && configurationIssues().isEmpty();
    }

    public ConfigurationState configurationState() {
        if (!enabled) {
            return ConfigurationState.DISABLED;
        }
        return isConfigured() ? ConfigurationState.CONFIGURED : ConfigurationState.MISSING_CONFIGURATION;
    }

    public List<ConfigurationIssue> configurationIssues() {
        if (!enabled) {
            return List.of();
        }
        List<ConfigurationIssue> issues = new ArrayList<>();
        if (!hasText(clientId)) issues.add(ConfigurationIssue.CLIENT_ID_MISSING);
        if (!hasText(clientSecret)) issues.add(ConfigurationIssue.CLIENT_SECRET_MISSING);
        if (hasText(redirectUri) && !hasValidRedirectUri(redirectUri)) {
            issues.add(ConfigurationIssue.REDIRECT_URI_INVALID);
        }
        return List.copyOf(issues);
    }

    public OAuthMode oauthMode() {
        return hasText(redirectUri) ? OAuthMode.PROXY : OAuthMode.MANUAL;
    }

    public String resolvedRedirectUri() {
        return oauthMode() == OAuthMode.PROXY ? redirectUri : OAuthPaths.MANUAL_REDIRECT_URI;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static boolean hasValidRedirectUri(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (scheme == null || host == null || path == null) return false;
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return false;
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return false;
            if (!path.endsWith(OAuthPaths.CALLBACK_PATH) || containsPathTraversal(path)) return false;

            String normalizedHost = stripIpv6Brackets(host.toLowerCase(Locale.ROOT));
            boolean loopback = isLoopbackHost(normalizedHost);
            if (scheme.equalsIgnoreCase("http") && !loopback) return false;
            return loopback || !isRawIpAddress(normalizedHost);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean containsPathTraversal(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/../") || normalized.endsWith("/..") || normalized.contains("%2e");
    }

    private static boolean isLoopbackHost(String host) {
        if (host.equals("localhost") || host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) return true;
        if (!looksLikeIpv4Address(host)) return false;
        String[] parts = host.split("\\.");
        return parts.length == 4 && parseIpv4Part(parts[0]) == 127 && validIpv4Parts(parts);
    }

    private static boolean isRawIpAddress(String host) {
        return host.contains(":") || (looksLikeIpv4Address(host) && validIpv4Parts(host.split("\\.")));
    }

    private static String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
    }

    private static boolean looksLikeIpv4Address(String host) {
        return host.matches("[0-9.]+");
    }

    private static boolean validIpv4Parts(String[] parts) {
        if (parts.length != 4) return false;
        for (String part : parts) {
            int value = parseIpv4Part(part);
            if (value < 0 || value > 255) return false;
        }
        return true;
    }

    private static int parseIpv4Part(String part) {
        try {
            return part.isEmpty() ? -1 : Integer.parseInt(part);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public enum ConfigurationState {
        DISABLED,
        MISSING_CONFIGURATION,
        CONFIGURED
    }

    public enum OAuthMode {
        MANUAL,
        PROXY
    }

    public enum ConfigurationIssue {
        CLIENT_ID_MISSING,
        CLIENT_SECRET_MISSING,
        REDIRECT_URI_INVALID
    }
}
