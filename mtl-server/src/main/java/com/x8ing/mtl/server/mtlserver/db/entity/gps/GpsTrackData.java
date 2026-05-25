package com.x8ing.mtl.server.mtlserver.db.entity.gps;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.x8ing.mtl.server.mtlserver.web.global.LineStringSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonPropertyOrder({
        "id",
        "gpsTrackId",
        "createDate",
        "trackType",
        "precisionInMeter",
        "numberOfPhysicalPoints",
        "track",
        "gpsTrackEvents"
})
public class GpsTrackData {

    /**
     * Default resolutions of simplified tracks
     */
    public static final BigDecimal PRECISION_RAW = new BigDecimal(0);
    public static final BigDecimal PRECISION_1M = new BigDecimal(1);
    public static final BigDecimal PRECISION_5M = new BigDecimal(5);
    public static final BigDecimal PRECISION_10M = new BigDecimal(10);
    public static final BigDecimal PRECISION_50M = new BigDecimal(50);
    public static final BigDecimal PRECISION_100M = new BigDecimal(100);
    public static final BigDecimal PRECISION_500M = new BigDecimal(500);
    public static final BigDecimal PRECISION_1000M = new BigDecimal(1000);

    public enum TRACK_TYPE {
        /**
         * Original GPS data as-is (no smoothing, no outlier removal).
         */
        RAW,
        /**
         * Kalman-smoothed + outlier-removed. Authoritative source for all per-point metrics.
         */
        RAW_OUTLIER_CLEANED,
        /**
         * Geometry-optimised (Douglas-Peucker) simplification used for map
         * polyline rendering, geo SQL filters and exploration score. Sampled
         * at {@link #precisionInMeter} (1..1000 m). NOT suitable for time-
         * series charts — on straight sections DP drops most points so the
         * surviving samples are no longer temporally uniform.
         */
        SIMPLIFIED_SHAPE
    }

    public enum TRACK_DETAILS_STATUS {
        PENDING, POPULATED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "gpsTrackId", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
//    private GpsTrack gpsTrack;

    private Long gpsTrackId;

    private Date createDate = new Date();

    @Enumerated(EnumType.STRING)
    private TRACK_TYPE trackType;

    private BigDecimal precisionInMeter;

    /**
     * Number of physical vertices in this variant's {@link #track} geometry,
     * maintained by this entity before insert/update.
     */
    @Column(name = "number_of_physical_points")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Number of physical vertices in this track-data variant's geometry.")
    private Integer numberOfPhysicalPoints;

    //@Type(type = "org.hibernate.spatial.GeometryType")
    @JsonSerialize(using = LineStringSerializer.class)
    @ArraySchema(schema = @Schema(type = "array", implementation = Double.class),
            arraySchema = @Schema(description = "Coordinate arrays [[lng, lat, elevation], ...]. Custom serialized by LineStringSerializer for bandwidth efficiency."))
    private LineString track;

    @Builder.Default
    @Transient
    private List<GpsTrackEvent> gpsTrackEvents = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void updateNumberOfPhysicalPoints() {
        numberOfPhysicalPoints = track == null ? null : track.getNumPoints();
    }

}
