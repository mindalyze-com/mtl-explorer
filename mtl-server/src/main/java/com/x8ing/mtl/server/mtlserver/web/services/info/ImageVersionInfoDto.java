package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "version",
        "buildTime"
})
public class ImageVersionInfoDto {

    private String version;

    private String buildTime;

    public static ImageVersionInfoDto of(String version, String buildTime) {
        ImageVersionInfoDto dto = new ImageVersionInfoDto();
        dto.setVersion(version);
        dto.setBuildTime(buildTime);
        return dto;
    }
}
