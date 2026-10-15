package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthAdministrationService;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GHDataSourceSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GHTypeSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/google-health")
@Slf4j
public class HealthController {

    private static final String GOOGLE_HEALTH_SETUP_UI_PATH = "/admin/imports";
    private static final String GOOGLE_HEALTH_RESULT_PARAMETER = "googleHealth";
    private static final String GOOGLE_HEALTH_SETUP_PARAMETER = "googleHealthSetup";
    private static final String GOOGLE_HEALTH_SETUP_OPEN = "open";
    private static final String OAUTH_RESULT_CONNECTED = "connected";
    private static final String OAUTH_RESULT_DENIED = "denied";
    private static final String OAUTH_RESULT_EXPIRED = "expired";
    private static final String OAUTH_RESULT_ERROR = "error";
    private static final String DEMO_UNAVAILABLE =
            "Google Health is unavailable in demo mode";

    private final HealthQueryService webService;
    private final ConnectorProperties properties;
    private final MtlAppProperties appProperties;
    private final GoogleHealthAdministrationService administrationService;

    public HealthController(
            HealthQueryService webService,
            ConnectorProperties properties,
            MtlAppProperties appProperties,
            GoogleHealthAdministrationService administrationService) {
        this.webService = webService;
        this.properties = properties;
        this.appProperties = appProperties;
        this.administrationService = administrationService;
    }

    @Operation(operationId = "getGoogleHealthStatus")
    @GetMapping("/status")
    public GHStatusResponse status() {
        return webService.status();
    }

    @Operation(operationId = "getGoogleHealthSetup")
    @GetMapping("/setup")
    public GHSetupResponse setup() {
        requireGoogleHealthAllowed();
        String configuredRedirectUri = properties.getRedirectUri();
        if (configuredRedirectUri != null && configuredRedirectUri.isBlank()) configuredRedirectUri = null;
        return new GHSetupResponse(
                properties.configurationState(),
                properties.configurationIssues(),
                properties.oauthMode(),
                configuredRedirectUri,
                OAuthPaths.MANUAL_REDIRECT_URI,
                OAuthPaths.CALLBACK_PATH,
                OAuthScopes.REQUESTED_ORDERED);
    }

    @Operation(operationId = "startGoogleHealthOAuth")
    @PostMapping("/oauth/start")
    public GHOAuthStartResponse startOAuth(
            @RequestParam(defaultValue = "false") boolean reauthorize) {
        requireGoogleHealthAllowed();
        GoogleHealthAdministrationService.AuthorizationStart authorization =
                administrationService.startAuthorization(reauthorize);
        return new GHOAuthStartResponse(
                authorization.authorizationUrl(), properties.oauthMode(), authorization.intent());
    }

    @Operation(operationId = "completeGoogleHealthOAuthManually")
    @PostMapping("/oauth/complete")
    public GHOAuthCompleteResponse completeOAuthManually(@RequestBody GHOAuthCompleteRequest request) {
        requireGoogleHealthAllowed();
        try {
            var outcome = administrationService.completeManualAuthorization(
                    request == null ? null : request.redirectUrl());
            return completionResponse(outcome.result(), null);
        } catch (OAuthStateException exception) {
            log.warn("Google Health manual OAuth used an invalid or expired state");
            return completionResponse(OAuthCompletionResult.EXPIRED,
                    "The authorization attempt expired. Start it again.");
        } catch (OAuthException exception) {
            log.warn("Google Health manual OAuth did not complete: {}", safeError(exception));
            return completionResponse(OAuthCompletionResult.ERROR, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Google Health manual OAuth failed: {}", safeError(exception), exception);
            return completionResponse(OAuthCompletionResult.ERROR,
                    "Google authorization did not complete. Start it again.");
        }
    }

    @Operation(operationId = "completeGoogleHealthOAuth")
    @SecurityRequirements
    @GetMapping(OAuthPaths.CALLBACK_ENDPOINT)
    public ResponseEntity<Void> oauthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        requireGoogleHealthAllowed();
        if (error != null && !error.isBlank()) {
            log.warn("Google Health OAuth provider returned an error: {}", safeProviderError(error));
            boolean validState = administrationService.cancelAuthorization(state);
            String result = !validState
                    ? OAUTH_RESULT_EXPIRED
                    : "access_denied".equals(error) ? OAUTH_RESULT_DENIED : OAUTH_RESULT_ERROR;
            return redirect(request.getContextPath(), result);
        }
        try {
            administrationService.completeAuthorization(code, state);
            return redirect(request.getContextPath(), OAUTH_RESULT_CONNECTED);
        } catch (OAuthStateException exception) {
            log.warn("Google Health OAuth callback used an invalid or expired state");
            return redirect(request.getContextPath(), OAUTH_RESULT_EXPIRED);
        } catch (RuntimeException exception) {
            log.warn("Google Health OAuth callback failed: {}", safeError(exception), exception);
            return redirect(request.getContextPath(), OAUTH_RESULT_ERROR);
        }
    }

    @Operation(operationId = "syncGoogleHealthNow")
    @ApiResponse(responseCode = "202", description = "Sync job accepted")
    @PostMapping("/sync")
    public ResponseEntity<SyncCoordinator.JobRequest> syncNow() {
        requireGoogleHealthAllowed();
        return ResponseEntity.accepted().body(administrationService.requestRegularSync());
    }

    @Operation(operationId = "reconcileAllGoogleHealthData")
    @ApiResponse(responseCode = "202", description = "Full reconciliation job accepted")
    @PostMapping("/reconcile-all")
    public ResponseEntity<SyncCoordinator.JobRequest> reconcileAll() {
        requireGoogleHealthAllowed();
        return ResponseEntity.accepted().body(administrationService.requestFullReconciliation());
    }

    @Operation(operationId = "reloadGoogleHealthRange")
    @PostMapping("/reload")
    public ResponseEntity<SyncCoordinator.JobRequest> reloadRange(@RequestBody GHReloadRequest request) {
        requireGoogleHealthAllowed();
        return ResponseEntity.accepted().body(administrationService.requestReload(
                request == null ? null : request.startDate(), request == null ? null : request.endDate(), false));
    }

    @Operation(operationId = "retryFailedGoogleHealthRanges")
    @PostMapping("/retry-failed")
    public ResponseEntity<SyncCoordinator.JobRequest> retryFailed(@RequestBody(required = false) GHReloadRequest request) {
        requireGoogleHealthAllowed();
        return ResponseEntity.accepted().body(administrationService.requestReload(
                request == null ? null : request.startDate(), request == null ? null : request.endDate(), true));
    }

    @Operation(operationId = "disconnectGoogleHealth")
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        requireGoogleHealthAllowed();
        administrationService.disconnect();
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "deleteGoogleHealthLocalMirror")
    @DeleteMapping("/mirror")
    public ResponseEntity<Void> deleteMirror(@RequestBody GHDeleteRequest request) {
        requireGoogleHealthAllowed();
        administrationService.deleteMirror(request == null ? null : request.confirmation());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getGoogleHealthTypeSummaries")
    @GetMapping("/summaries")
    public List<GHTypeSummary> summaries() {
        return webService.summaries();
    }

    @Operation(operationId = "getGoogleHealthDataSources")
    @GetMapping("/sources")
    public List<GHDataSourceSummary> sources() {
        return webService.sources();
    }

    @Operation(operationId = "getGoogleHealthMetricSeries")
    @GetMapping("/metrics")
    public List<GHMetricSeries> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return webService.metrics(start, end);
    }

    private static LocalDate recordDate(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            log.warn("Invalid Google Health records date: errorType={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Record dates must use YYYY-MM-DD");
        }
    }

    @Operation(operationId = "getGoogleHealthRecords")
    @GetMapping("/records")
    public GHRecordsResponse records(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String representation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return webService.records(dataType, representation, start, end, recordDate(startDate), recordDate(endDate), timeZone,
                dataSourceId, cursor, pageSize);
    }

    @Operation(operationId = "getGoogleHealthRecord")
    @GetMapping("/records/{recordId}")
    public GHRecordResponse record(@PathVariable String recordId) {
        return webService.record(recordId);
    }

    private ResponseEntity<Void> redirect(String contextPath, String result) {
        String applicationBase = contextPath;
        if (properties.isConfigured() && properties.oauthMode() == ConnectorProperties.OAuthMode.PROXY) {
            String configuredCallback = properties.getRedirectUri();
            applicationBase = configuredCallback.substring(
                    0, configuredCallback.length() - OAuthPaths.CALLBACK_PATH.length());
        }
        String location = applicationBase + GOOGLE_HEALTH_SETUP_UI_PATH
                          + "?" + GOOGLE_HEALTH_SETUP_PARAMETER + "=" + GOOGLE_HEALTH_SETUP_OPEN
                          + "&" + GOOGLE_HEALTH_RESULT_PARAMETER + "=" + result;
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }

    private static GHOAuthCompleteResponse completionResponse(OAuthCompletionResult result, String message) {
        if (message == null && result == OAuthCompletionResult.DENIED) {
            message = "Google authorization was cancelled or denied.";
        } else if (message == null && result == OAuthCompletionResult.ERROR) {
            message = "Google authorization did not complete. Start it again.";
        }
        return new GHOAuthCompleteResponse(result, message);
    }

    private static String safeProviderError(String error) {
        String normalized = error.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private static String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private void requireGoogleHealthAllowed() {
        if (appProperties.isDemoMode()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, DEMO_UNAVAILABLE);
        }
    }

}
