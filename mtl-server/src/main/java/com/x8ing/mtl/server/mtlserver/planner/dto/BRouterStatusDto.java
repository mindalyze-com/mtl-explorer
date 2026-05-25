package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.info.VersionInfoDto;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "available",
        "brouterPort",
        "brouterRunning",
        "segmentsDir",
        "segmentsOnDisk",
        "segmentsQueued",
        "segmentsInProgress",
        "segmentsCompletedThisRun",
        "segmentsFailed",
        "segmentsKnown404",
        "segmentsValidationPhase",
        "segmentsValidated",
        "segmentsRepaired",
        "segmentsValidationWarnings",
        "segmentsValidationStartedAt",
        "segmentsValidationCompletedAt",
        "reason",
        "versionInfo"
})
public class BRouterStatusDto {

    private boolean available;

    private Integer brouterPort;

    private Boolean brouterRunning;

    private String segmentsDir;

    private Integer segmentsOnDisk;

    private Integer segmentsQueued;

    private List<String> segmentsInProgress = List.of();

    private List<String> segmentsCompletedThisRun = List.of();

    private Map<String, String> segmentsFailed = new LinkedHashMap<>();

    private Integer segmentsKnown404;

    private String segmentsValidationPhase;

    private Integer segmentsValidated;

    private List<String> segmentsRepaired = List.of();

    private List<String> segmentsValidationWarnings = List.of();

    private String segmentsValidationStartedAt;

    private String segmentsValidationCompletedAt;

    private String reason;

    private VersionInfoDto versionInfo;

    public static BRouterStatusDto unavailable(String reason) {
        BRouterStatusDto dto = new BRouterStatusDto();
        dto.setAvailable(false);
        dto.setReason(reason);
        return dto;
    }
}
