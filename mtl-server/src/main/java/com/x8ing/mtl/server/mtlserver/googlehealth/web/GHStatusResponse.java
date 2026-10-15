package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GHSyncState;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.RouteSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder({
        "configurationState",
        "connectionState",
        "authorizationAction",
        "grantedScopes",
        "grantedCategories",
        "connectedAt",
        "refreshTokenExpiresAt",
        "nextRegularSync",
        "lastError",
        "mirrorSummary",
        "mirrorSnapshotAt",
        "routeSummary",
        "job",
        "syncStates",
        "synchronization"
})
public record GHStatusResponse(
        ConnectorProperties.ConfigurationState configurationState,
        AccountConnection.ConnectionState connectionState,
        AuthorizationAction authorizationAction,
        Set<String> grantedScopes,
        Map<String, Boolean> grantedCategories,
        Instant connectedAt,
        Instant refreshTokenExpiresAt,
        Instant nextRegularSync,
        String lastError,
        GHMirrorSummary mirrorSummary,
        Instant mirrorSnapshotAt,
        RouteSummary routeSummary,
        SyncCoordinator.JobSnapshot job,
        List<GHSyncState> syncStates,
        GHSynchronizationStatus synchronization
) {
    public GHStatusResponse(ConnectorProperties.ConfigurationState configurationState,
                            AccountConnection.ConnectionState connectionState,
                            AuthorizationAction authorizationAction, Set<String> grantedScopes,
                            Map<String, Boolean> grantedCategories, Instant connectedAt, Instant refreshTokenExpiresAt,
                            Instant nextRegularSync, String lastError, GHMirrorSummary mirrorSummary,
                            SyncCoordinator.JobSnapshot job, List<GHSyncState> syncStates) {
        this(configurationState, connectionState, authorizationAction, grantedScopes, grantedCategories,
                connectedAt, refreshTokenExpiresAt, nextRegularSync, lastError, mirrorSummary, null,
                RouteSummary.empty(), job, syncStates, null);
    }

    public GHStatusResponse(ConnectorProperties.ConfigurationState configurationState,
                            AccountConnection.ConnectionState connectionState, AuthorizationAction authorizationAction,
                            Set<String> grantedScopes, Map<String, Boolean> grantedCategories, Instant connectedAt, Instant refreshTokenExpiresAt,
                            Instant nextRegularSync, String lastError, GHMirrorSummary mirrorSummary, Instant mirrorSnapshotAt,
                            RouteSummary routeSummary, SyncCoordinator.JobSnapshot job, List<GHSyncState> syncStates) {
        this(configurationState, connectionState, authorizationAction, grantedScopes, grantedCategories, connectedAt,
                refreshTokenExpiresAt, nextRegularSync, lastError, mirrorSummary, mirrorSnapshotAt, routeSummary, job, syncStates, null);
    }

    public enum AuthorizationAction {
        NONE,
        EXPIRING_SOON,
        REAUTHORIZE
    }
}
