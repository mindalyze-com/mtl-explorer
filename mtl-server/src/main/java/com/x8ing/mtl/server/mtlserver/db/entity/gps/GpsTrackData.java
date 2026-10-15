package com.x8ing.mtl.server.mtlserver.db.entity.gps;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GpsObservationEvidence;
import com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingIssue;
import com.x8ing.mtl.server.mtlserver.gpx.RecordedPathGeometry;
import com.x8ing.mtl.server.mtlserver.web.global.LineStringSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.*;

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
        "recordingBreakBeforePointIndexes",
        "observationEvidence",
        "processingSettings",
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

    @JsonIgnore
    @Schema(hidden = true)
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry observedPath;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Integer> recordingBreakBeforePointIndexes = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<GpsObservationEvidence> observationEvidence = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, String> processingSettings = java.util.Map.of();

    @JsonIgnore
    @Schema(hidden = true)
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<GpsProcessingIssue> processingIssues = new ArrayList<>();

    @Builder.Default
    @Transient
    private List<GpsTrackEvent> gpsTrackEvents = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void updateNumberOfPhysicalPoints() {
        numberOfPhysicalPoints = track == null ? null : track.getNumPoints();
        observedPath = trackType == TRACK_TYPE.RAW_OUTLIER_CLEANED
                ? RecordedPathGeometry.candidates(track, recordingBreakBeforePointIndexes)
                : buildObservedPath(track, recordingBreakBeforePointIndexes);
    }

    public static Geometry buildObservedPath(LineString line, List<Integer> breakIndexes) {
        if (line == null) return null;
        Set<Integer> breaks = new HashSet<>(breakIndexes == null ? List.of() : breakIndexes);
        List<LineString> spans = new ArrayList<>();
        Coordinate[] coordinates = line.getCoordinates();
        for (int from = 0; from < coordinates.length; ) {
            int to = from + 1;
            while (to < coordinates.length && !breaks.contains(to)) to++;
            if (to - from >= 2) {
                Coordinate[] horizontal = new Coordinate[to - from];
                for (int i = from; i < to; i++) horizontal[i - from] = new org.locationtech.jts.geom.CoordinateXY(coordinates[i].getX(), coordinates[i].getY());
                spans.add(line.getFactory().createLineString(horizontal));
            }
            from = to;
        }
        Geometry result = line.getFactory().createMultiLineString(spans.toArray(LineString[]::new));
        result.setSRID(line.getSRID());
        return result;
    }
}
