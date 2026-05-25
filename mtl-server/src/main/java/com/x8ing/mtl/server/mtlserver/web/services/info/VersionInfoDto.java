package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "image",
        "components",
        "data"
})
public class VersionInfoDto {

    private ImageVersionInfoDto image;

    private Map<String, String> components = new LinkedHashMap<>();

    private Map<String, String> data = new LinkedHashMap<>();
}
