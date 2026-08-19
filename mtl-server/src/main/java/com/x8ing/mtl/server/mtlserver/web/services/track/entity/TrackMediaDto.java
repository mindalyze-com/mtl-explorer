package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@JsonPropertyOrder({
        "id",
        "fileName",
        "mediaKind",
        "capturedAt",
        "adjustedCapturedAt",
        "appliedCameraOffsetSeconds",
        "timeSource",
        "cameraMake",
        "cameraModel",
        "originalLat",
        "originalLng",
        "routeLat",
        "routeLng",
        "resolvedLat",
        "resolvedLng",
        "manualLat",
        "manualLng",
        "manualNote",
        "distanceInMeterSinceStart",
        "durationSinceStartSeconds",
        "trackPointIndex",
        "trackPointTimeDeltaSeconds",
        "positionOrigin",
        "estimatedPosition",
        "ambiguousMatch",
        "alternativeMatchCount",
        "preview"
})
public class TrackMediaDto {

    public enum MEDIA_KIND {
        IMAGE, VIDEO
    }

    public enum TIME_SOURCE {
        EXIF_GPS, EXIF_DATE_TAKEN
    }

    public enum POSITION_ORIGIN {
        EXIF_EMBEDDED, TRACK_INTERPOLATED, USER_ASSIGNED
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private String fileName;
    private MEDIA_KIND mediaKind;
    @Schema(description = "Unadjusted authoritative capture time: EXIF GPS time when available, otherwise DateTimeOriginal.")
    private Date capturedAt;
    @Schema(description = "Capture time used for activity matching after applying the camera offset when applicable.")
    private Date adjustedCapturedAt;
    @Schema(description = "Offset added to DateTimeOriginal in seconds. Zero when EXIF GPS time is authoritative.")
    private Integer appliedCameraOffsetSeconds;
    @Schema(description = "Metadata field used as the authoritative capture time.")
    private TIME_SOURCE timeSource;
    private String cameraMake;
    private String cameraModel;
    @Schema(description = "Original EXIF GPS latitude. Null when the media has no embedded GPS position.")
    private Double originalLat;
    @Schema(description = "Original EXIF GPS longitude. Null when the media has no embedded GPS position.")
    private Double originalLng;
    @Schema(description = "Latitude on the matched activity route at the adjusted capture time.")
    private Double routeLat;
    @Schema(description = "Longitude on the matched activity route at the adjusted capture time.")
    private Double routeLng;
    @Schema(description = "Latitude selected for display after applying user, EXIF, and track precedence.")
    private Double resolvedLat;
    @Schema(description = "Longitude selected for display after applying user, EXIF, and track precedence.")
    private Double resolvedLng;
    @Schema(description = "User-assigned latitude. Null when no manual position exists.")
    private Double manualLat;
    @Schema(description = "User-assigned longitude. Null when no manual position exists.")
    private Double manualLng;
    private String manualNote;
    private Double distanceInMeterSinceStart;
    private Double durationSinceStartSeconds;
    private Integer trackPointIndex;
    private Double trackPointTimeDeltaSeconds;
    @Schema(description = "Origin of the resolved display position.")
    private POSITION_ORIGIN positionOrigin;
    @Schema(description = "True only when the media position is inferred from the activity route.")
    private Boolean estimatedPosition;
    @Schema(description = "True when more than one eligible activity overlapped the adjusted capture time.")
    private Boolean ambiguousMatch;
    private Integer alternativeMatchCount;
    @Schema(description = "True when this row is an unsaved camera-offset preview.")
    private Boolean preview;
}
