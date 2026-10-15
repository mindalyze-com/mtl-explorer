package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "version",
        "revision",
        "buildTime"
})
public class ImageVersionInfoDto {

    private String version;

    private String revision;

    private String buildTime;

    public static ImageVersionInfoDto of(String version, String revision, String buildTime) {
        ImageVersionInfoDto dto = new ImageVersionInfoDto();
        dto.setVersion(version);
        dto.setRevision(revision);
        dto.setBuildTime(buildTime);
        return dto;
    }
}
