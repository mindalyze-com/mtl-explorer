package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management;

@JsonPropertyOrder({"id", "label", "management", "installedBuildId", "targetBuildId", "status", "compatibility", "checkFailed"})
public record ReleaseComponentUpdate(String id, String label, Management management, String installedBuildId,
                                     String targetBuildId, Status status, Compatibility compatibility, boolean checkFailed) {
    public enum Status {UPDATE_AVAILABLE, CURRENT, UNKNOWN, PROVIDER_MANAGED, DISABLED, UNTRACKED}

    public enum Compatibility {SUPPORTED, UPDATE_REQUIRED, UNKNOWN}
}
