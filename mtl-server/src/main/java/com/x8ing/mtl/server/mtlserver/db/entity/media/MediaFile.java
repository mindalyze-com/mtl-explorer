package com.x8ing.mtl.server.mtlserver.db.entity.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.util.Date;

@Entity
@Table(name = "media_file")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // TODO: Why? Why not required on GpsTrack?
@JsonPropertyOrder({
        "id",
        "indexedFile",
        "creDate",
        "exifGpsLocationLong",
        "exifGpsLocationLat",
        "exifGpsLocation",
        "gpsAltitudeMeters",
        "exifGpsDate",
        "exifDateImageTaken",
        "cameraMake",
        "cameraModel",
        "lensModel",
        "widthPixels",
        "heightPixels",
        "apertureFNumber",
        "exposureTimeSeconds",
        "isoSpeed",
        "focalLengthMm",
        "focalLength35Mm",
        "durationSeconds",
        "frameRate",
        "videoCodec",
        "audioCodec"
})
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id", referencedColumnName = "id")
    private IndexedFile indexedFile;

    @Column(name = "cre_date")
    private Date creDate;

    @Column(name = "exif_gps_location_long")
    private Double exifGpsLocationLong;

    @Column(name = "exif_gps_location_lat")
    private Double exifGpsLocationLat;

    @Column(name = "exif_gps_location")
    private Geometry exifGpsLocation;

    @Column(name = "gps_altitude_meters")
    private Double gpsAltitudeMeters;

    @Column(name = "exif_gps_date")
    private Date exifGpsDate;

    @Column(name = "exif_date_image_taken")
    // Stores the best embedded capture time. The legacy column name also covers video-container metadata.
    private Date exifDateImageTaken;

    @Column(name = "camera_make")
    private String cameraMake;

    @Column(name = "camera_model")
    private String cameraModel;

    @Column(name = "lens_model")
    private String lensModel;

    @Column(name = "width_pixels")
    private Integer widthPixels;

    @Column(name = "height_pixels")
    private Integer heightPixels;

    @Column(name = "aperture_f_number")
    private Double apertureFNumber;

    @Column(name = "exposure_time_seconds")
    private Double exposureTimeSeconds;

    @Column(name = "iso_speed")
    private Integer isoSpeed;

    @Column(name = "focal_length_mm")
    private Double focalLengthMm;

    @Column(name = "focal_length_35mm")
    private Integer focalLength35Mm;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "frame_rate")
    private Double frameRate;

    @Column(name = "video_codec")
    private String videoCodec;

    @Column(name = "audio_codec")
    private String audioCodec;
}
