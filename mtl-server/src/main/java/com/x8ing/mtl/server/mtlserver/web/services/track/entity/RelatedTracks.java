package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

import java.util.List;

@Data
public class RelatedTracks {

    List<RelatedTrackInfo> previousTracksInTime;

    List<RelatedTrackInfo> nextTracksInTime;

    List<RelatedTrackInfo> duplicates;

    List<RelatedTrackInfo> segmentSiblings;

}
