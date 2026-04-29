package com.x8ing.mtl.server.mtlserver.db.entity.gps;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.util.Date;

@Entity
@Table(name = "gps_track_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpsTrackEvent {

    public enum EVENT_TYPE {
        STOP,
        PHOTO_STOP,
        DATA_ISSUE,
        GPS_GAP,
        MANUAL_NOTE
    }

    public enum SOURCE {
        DETECTED,
        USER,
        IMPORTED,
        SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gps_track_id", nullable = false)
    private Long gpsTrackId;

    @Column(name = "gps_track_data_id")
    private Long gpsTrackDataId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EVENT_TYPE eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private SOURCE source;

    @Column(name = "start_gps_track_data_point_id")
    private Long startGpsTrackDataPointId;

    @Column(name = "end_gps_track_data_point_id")
    private Long endGpsTrackDataPointId;

    @Column(name = "start_point_index")
    private Integer startPointIndex;

    @Column(name = "end_point_index")
    private Integer endPointIndex;

    @Column(name = "start_timestamp")
    private Date startTimestamp;

    @Column(name = "end_timestamp")
    private Date endTimestamp;

    @Column(name = "start_distance_in_meter")
    private Double startDistanceInMeter;

    @Column(name = "end_distance_in_meter")
    private Double endDistanceInMeter;

    @Column(name = "duration_in_sec")
    private Double durationInSec;

    @Column(name = "start_point_long_lat", columnDefinition = "geometry(Point,4326)")
    private Point startPointLongLat;

    @Column(name = "end_point_long_lat", columnDefinition = "geometry(Point,4326)")
    private Point endPointLongLat;

    @Column(name = "label", length = 512)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "confidence")
    private Double confidence;

    @Builder.Default
    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(name = "update_date")
    private Date updateDate;
}
