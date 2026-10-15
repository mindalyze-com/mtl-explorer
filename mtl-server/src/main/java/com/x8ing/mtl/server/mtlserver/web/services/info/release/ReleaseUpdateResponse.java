package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonPropertyOrder({"status", "installationId", "channel", "installedReleaseVersion", "installedBuildId",
        "latestReleaseVersion", "latestBuildId", "installedComponentKey", "components", "summary", "releaseNotesUrl", "updateGuideUrl", "publishedOn",
        "checkedAt", "checking", "checkFailed"})
public record ReleaseUpdateResponse(Status status, String installationId, String channel,
                                    String installedReleaseVersion, String installedBuildId,
                                    String latestReleaseVersion, String latestBuildId, String installedComponentKey,
                                    List<ReleaseComponentUpdate> components, String summary,
                                    String releaseNotesUrl, String updateGuideUrl, LocalDate publishedOn,
                                    Instant checkedAt, boolean checking, boolean checkFailed) {
    public enum Status {UPDATE_AVAILABLE, UP_TO_DATE, UNKNOWN, CHECKING, UNAVAILABLE, DISABLED}
}
