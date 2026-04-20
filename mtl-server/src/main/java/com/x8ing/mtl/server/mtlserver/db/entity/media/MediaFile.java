package com.x8ing.mtl.server.mtlserver.db.entity.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.util.Date;

@Entity
@Table(name = "media_file")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // TODO: Why? Why not required on GpsTrack?
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

    @Column(name = "exif_gps_date")
    private Date exifGpsDate;

    @Column(name = "exif_date_image_taken")
    private Date exifDateImageTaken;

    @Column(name = "camera_make")
    private String cameraMake;

    @Column(name = "camera_model")
    private String cameraModel;
}
