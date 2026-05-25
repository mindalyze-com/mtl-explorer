package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonPropertyOrder({
        "previousTracksInTime",
        "nextTracksInTime",
        "duplicates",
        "segmentSiblings"
})
public class RelatedTracks {

    List<RelatedTrackInfo> previousTracksInTime;

    List<RelatedTrackInfo> nextTracksInTime;

    List<RelatedTrackInfo> duplicates;

    List<RelatedTrackInfo> segmentSiblings;

}
