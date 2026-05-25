package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * Item in the "My Plans" list — minimal fields for the planner sidebar.
 */
@Data
@JsonPropertyOrder({
        "id",
        "name",
        "description",
        "distanceM",
        "centerLat",
        "centerLng",
        "createDate",
        "profile"
})
public class PlannedTrackSummaryDto {
    private Long id;
    private String name;
    private String description;
    private double distanceM;
    private Double centerLat;
    private Double centerLng;
    private java.util.Date createDate;
    private String profile;
}
