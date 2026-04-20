package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedTrackInfo {

    private Long id;
    private String name;
    private String description;
    private Date startDate;
    private Integer sourceSegmentIndex;
    private Long sourceParentTrackId;

    public static RelatedTrackInfo from(GpsTrack track) {
        String name = track.getTrackName() != null && !track.getTrackName().isBlank()
                ? track.getTrackName()
                : track.getMetaName() != null && !track.getMetaName().isBlank()
                ? track.getMetaName()
                : "Track #" + track.getId();

        String description = track.getTrackDescription() != null && !track.getTrackDescription().isBlank()
                ? track.getTrackDescription()
                : track.getMetaDescription();

        RelatedTrackInfo info = new RelatedTrackInfo();
        info.setId(track.getId());
        info.setName(name);
        info.setDescription(description);
        info.setStartDate(track.getStartDate());
        info.setSourceSegmentIndex(track.getSourceSegmentIndex());
        info.setSourceParentTrackId(track.getSourceParentTrackId());
        return info;
    }
}
