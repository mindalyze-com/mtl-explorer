package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.similarity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "gpsTrackIdDuplicate",
        "similarity"
})
public class SimilarTrack {


    private Long gpsTrackIdDuplicate;

    private Double similarity;

}
