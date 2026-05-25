package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "displayName",
        "sourceLayer",
        "kind",
        "kindDetail",
        "minZoom",
        "maxZoom",
        "lat",
        "lon",
        "distanceMeters",
        "countryCode",
        "countryName",
        "admin1Code",
        "admin1Name",
        "admin1Level",
        "lang",
        "name"
})
public class LocationSearchResultDto {

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("source_layer")
    private String sourceLayer;

    private String kind;

    @JsonProperty("kind_detail")
    private String kindDetail;

    @JsonProperty("min_zoom")
    private Integer minZoom;

    @JsonProperty("max_zoom")
    private Integer maxZoom;

    private Double lat;

    private Double lon;

    @JsonProperty("distance_meters")
    private Long distanceMeters;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("country_name")
    private String countryName;

    @JsonProperty("admin1_code")
    private String admin1Code;

    @JsonProperty("admin1_name")
    private String admin1Name;

    @JsonProperty("admin1_level")
    private Integer admin1Level;

    private String lang;

    private String name;
}
