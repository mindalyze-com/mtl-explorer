package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthAdministrationService;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class HealthControllerTest {

    private OAuthService oauthService;
    private HealthQueryService webService;
    private ConnectorProperties properties;
    private MtlAppProperties appProperties;
    private SyncCoordinator syncCoordinator;
    private MirrorRepository repository;
    private ExerciseRouteService exerciseRouteService;
    private AccessTokenProvider accessTokenProvider;
    private DataSourceResolver dataSourceResolver;
    private GoogleHealthMirrorCache mirrorCache;
    private GoogleHealthAdministrationService administrationService;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        oauthService = mock(OAuthService.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(oauthService).runWithAuthorizationInvalidated(any());
        webService = mock(HealthQueryService.class);
        properties = new ConnectorProperties();
        appProperties = new MtlAppProperties();
        syncCoordinator = mock(SyncCoordinator.class);
        repository = mock(MirrorRepository.class);
        exerciseRouteService = mock(ExerciseRouteService.class);
        accessTokenProvider = mock(AccessTokenProvider.class);
        dataSourceResolver = mock(DataSourceResolver.class);
        mirrorCache = mock(GoogleHealthMirrorCache.class);
        when(syncCoordinator.runExclusivelyAfterCancelling(any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        administrationService = new GoogleHealthAdministrationService(
                properties, oauthService, syncCoordinator, repository, exerciseRouteService,
                accessTokenProvider, dataSourceResolver, mirrorCache);
        controller = new HealthController(webService, properties, appProperties, administrationService);
    }

    @Test
    void callbackReturnsToSetupWizardAtTheConfiguredPublicAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/mtl");
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setRedirectUri("https://trails.example.org/proxy/mtl/api/google-health/oauth/callback");
        when(oauthService.completeAuthorizationWithOutcome("code", "state"))
                .thenReturn(completed(false));

        var response = controller.oauthCallback("code", "state", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("https://trails.example.org/proxy/mtl/admin/imports"
                           + "?googleHealthSetup=open&googleHealth=connected");
        verify(oauthService).completeAuthorizationWithOutcome("code", "state");
        verify(syncCoordinator).request(SyncCoordinator.JobKind.REGULAR);
    }

    @Test
    void callbackErrorReturnsToSetupWizardAndLogsTheFailureWithoutOAuthValues(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/mtl");
        doThrow(new IllegalArgumentException("diagnostic failure"))
                .when(oauthService).completeAuthorizationWithOutcome("sensitive-code", "sensitive-state");

        var response = controller.oauthCallback(
                "sensitive-code", "sensitive-state", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/mtl/admin/imports?googleHealthSetup=open&googleHealth=error");
        assertThat(output)
                .contains("Google Health OAuth callback failed: diagnostic failure")
                .contains("IllegalArgumentException: diagnostic failure")
                .doesNotContain("sensitive-code")
                .doesNotContain("sensitive-state");
        verify(syncCoordinator, never()).request(any());
    }

    @Test
    void callbackDistinguishesDeniedAndExpiredAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/mtl");
        when(oauthService.cancelAuthorization("valid-state")).thenReturn(true);
        when(oauthService.cancelAuthorization("expired-state")).thenReturn(false);

        var denied = controller.oauthCallback(null, "valid-state", "access_denied", request);
        var expiredDenial = controller.oauthCallback(null, "expired-state", "access_denied", request);
        doThrow(new OAuthStateException())
                .when(oauthService).completeAuthorizationWithOutcome("code", "expired-state");
        var expiredCallback = controller.oauthCallback("code", "expired-state", null, request);

        assertThat(denied.getHeaders().getFirst(HttpHeaders.LOCATION))
                .endsWith("googleHealth=denied");
        assertThat(expiredDenial.getHeaders().getFirst(HttpHeaders.LOCATION))
                .endsWith("googleHealth=expired");
        assertThat(expiredCallback.getHeaders().getFirst(HttpHeaders.LOCATION))
                .endsWith("googleHealth=expired");
        verify(syncCoordinator, never()).request(any());
    }

    @Test
    void callbackUsesTheLocalSetupAddressWhenManualModeIsConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/mtl");
        properties.setClientId("client");
        properties.setClientSecret("secret");
        when(oauthService.cancelAuthorization("state")).thenReturn(false);

        var response = controller.oauthCallback(null, "state", "access_denied", request);

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/mtl/admin/imports?googleHealthSetup=open&googleHealth=expired");
    }

    @Test
    void setupResponseContainsOnlySafeConfigurationDetails() {
        properties.setClientId("sensitive-client-id");
        properties.setClientSecret("sensitive-client-secret");
        properties.setRedirectUri("https://trails.example.org/mtl/api/google-health/oauth/callback");

        GHSetupResponse response = controller.setup();

        assertThat(response.configurationState())
                .isEqualTo(ConnectorProperties.ConfigurationState.CONFIGURED);
        assertThat(response.configurationIssues()).isEmpty();
        assertThat(response.oauthMode()).isEqualTo(ConnectorProperties.OAuthMode.PROXY);
        assertThat(response.configuredRedirectUri())
                .isEqualTo("https://trails.example.org/mtl/api/google-health/oauth/callback");
        assertThat(response.manualRedirectUri()).isEqualTo("http://127.0.0.1:1/mtl-explorer-oauth");
        assertThat(response.callbackPath()).isEqualTo("/api/google-health/oauth/callback");
        assertThat(response.requestedScopes()).hasSize(4);
        assertThat(response.toString())
                .doesNotContain("sensitive-client-id", "sensitive-client-secret", "AAAAAAAA");
    }

    @Test
    void manualCompletionRequestsInitialSyncWithoutLoggingThePastedUrl(CapturedOutput output) {
        properties.setClientId("client");
        properties.setClientSecret("secret");
        when(oauthService.completeManualAuthorizationWithOutcome(
                "http://127.0.0.1:1/mtl-explorer-oauth?code=sensitive&state=state"))
                .thenReturn(completed(false));

        GHOAuthCompleteResponse response = controller.completeOAuthManually(
                new GHOAuthCompleteRequest(
                        "http://127.0.0.1:1/mtl-explorer-oauth?code=sensitive&state=state"));

        assertThat(response.result()).isEqualTo(OAuthCompletionResult.CONNECTED);
        assertThat(response.message()).isNull();
        verify(syncCoordinator).request(SyncCoordinator.JobKind.REGULAR);
        assertThat(output).doesNotContain("code=sensitive", "state=state");
    }

    @Test
    void manualCompletionQueuesStoredRoutesWithoutReloadingHistory() {
        properties.setClientId("client");
        properties.setClientSecret("secret");
        when(oauthService.completeManualAuthorizationWithOutcome("completed"))
                .thenReturn(completed(true));

        GHOAuthCompleteResponse response = controller.completeOAuthManually(
                new GHOAuthCompleteRequest("completed"));

        assertThat(response.result()).isEqualTo(OAuthCompletionResult.CONNECTED);
        verify(syncCoordinator).request(SyncCoordinator.JobKind.REGULAR);
    }

    @Test
    void manualCompletionReturnsActionableResultsForDenialExpiryAndInvalidPaste() {
        properties.setClientId("client");
        properties.setClientSecret("secret");
        when(oauthService.completeManualAuthorizationWithOutcome("denied"))
                .thenReturn(new OAuthService.CompletionOutcome(
                        OAuthCompletionResult.DENIED, Set.of(), false));
        doThrow(new OAuthStateException())
                .when(oauthService).completeManualAuthorizationWithOutcome("expired");
        doThrow(new OAuthException("Paste the complete address from the browser", false))
                .when(oauthService).completeManualAuthorizationWithOutcome("invalid");

        assertThat(controller.completeOAuthManually(new GHOAuthCompleteRequest("denied")).result())
                .isEqualTo(OAuthCompletionResult.DENIED);
        assertThat(controller.completeOAuthManually(new GHOAuthCompleteRequest("expired")).result())
                .isEqualTo(OAuthCompletionResult.EXPIRED);
        GHOAuthCompleteResponse invalid = controller.completeOAuthManually(new GHOAuthCompleteRequest("invalid"));
        assertThat(invalid.result()).isEqualTo(OAuthCompletionResult.ERROR);
        assertThat(invalid.message()).isEqualTo("Paste the complete address from the browser");
        verify(syncCoordinator, never()).request(any());
    }

    @Test
    void disconnectStopsSyncRemovesCredentialsAndInvalidatesCachedAccessToken() {
        var response = controller.disconnect();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(syncCoordinator).runExclusivelyAfterCancelling(any(), any());
        verify(repository).disconnect();
        verify(accessTokenProvider).invalidate();
    }

    @Test
    void localMirrorDeletionRequiresExactConfirmation() {
        Assertions.assertThatThrownBy(
                        () -> controller.deleteMirror(new GHDeleteRequest("delete")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verify(exerciseRouteService, Mockito.never()).removeAllManagedRoutes();
        verify(repository, Mockito.never()).deleteMirrorRows();
    }

    @Test
    void localMirrorDeletionStopsSyncBeforeRemovingManagedData() {
        var response = controller.deleteMirror(new GHDeleteRequest("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(syncCoordinator).runExclusivelyAfterCancelling(any(), any());
        verify(repository).disconnect();
        verify(accessTokenProvider).invalidate();
        verify(exerciseRouteService).removeAllManagedRoutes();
        verify(exerciseRouteService).removeOrphanedManagedRoutes();
        verify(repository).deleteMirrorRows();
        verify(dataSourceResolver).clearCache();
        verify(mirrorCache).clear();
    }

    @Test
    void localMirrorDeletionRequiresTheConnectionToBeDisconnected() {
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user", Set.of(), "encrypted", AccountConnection.ConnectionState.CONNECTED,
                null, null, null, null, null)));

        Assertions.assertThatThrownBy(
                        () -> controller.deleteMirror(new GHDeleteRequest("DELETE")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Disconnect Google Health before deleting the local mirror");
                });

        verify(syncCoordinator, never()).runExclusivelyAfterCancelling(any(), any());
        verify(repository, never()).deleteMirrorRows();
    }

    @Test
    void disabledConnectorRejectsRemoteActionsWithoutQueuingWork() {
        properties.setEnabled(false);

        for (Runnable action : List.<Runnable>of(
                () -> controller.startOAuth(false),
                controller::syncNow,
                controller::reconcileAll)) {
            Assertions.assertThatThrownBy(action::run)
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        verify(oauthService, never()).createAuthorizationUrl(any());
        verify(syncCoordinator, never()).request(any());
        verify(repository, never()).findConnection();
    }

    @Test
    void demoModeRejectsConnectionAndMutationEndpointsWithoutSideEffects() {
        appProperties.setDemoMode(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/mtl");

        for (Runnable action : List.<Runnable>of(
                controller::setup,
                () -> controller.startOAuth(false),
                () -> controller.completeOAuthManually(new GHOAuthCompleteRequest(
                        "http://127.0.0.1:1/mtl-explorer-oauth?code=code&state=state")),
                () -> controller.oauthCallback("code", "state", null, request),
                controller::syncNow,
                controller::reconcileAll,
                controller::disconnect,
                () -> controller.deleteMirror(new GHDeleteRequest("DELETE")))) {
            Assertions.assertThatThrownBy(action::run)
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(exception.getReason())
                                .isEqualTo("Google Health is unavailable in demo mode");
                    });
        }

        verify(oauthService, never()).createAuthorizationUrl(any());
        verify(oauthService, never()).completeManualAuthorizationWithOutcome(any());
        verify(oauthService, never()).completeAuthorizationWithOutcome(any(), any());
        verify(syncCoordinator, never()).request(any());
        verify(syncCoordinator, never()).runExclusivelyAfterCancelling(any(), any());
        verify(repository, never()).findConnection();
        verify(repository, never()).disconnect();
        verify(repository, never()).deleteMirrorRows();
        verify(exerciseRouteService, never()).removeAllManagedRoutes();
        Mockito.verifyNoInteractions(webService);
    }

    private static OAuthService.CompletionOutcome completed(boolean fullReconciliationRequired) {
        return new OAuthService.CompletionOutcome(
                OAuthCompletionResult.CONNECTED,
                Set.of(),
                fullReconciliationRequired);
    }
}
