package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<MediaFile, Long> {


    /**
     * "JOIN FETCH" to join on the DB and return both entities, as
     * JPA won't consider the EAGER fetch on the entity.
     */
    @Query("SELECT m FROM MediaFile m JOIN FETCH m.indexedFile WHERE m.exifGpsLocation is not null")
    List<MediaFile> findMediaWithLocationInfo();

    /**
     * Find media points within a bounding box using the PostGIS GIST index on exif_gps_location.
     * Returns lightweight DTOs with only id, lat, lng.
     */
    @Query(nativeQuery = true, value =
            "SELECT m.id, ROUND(CAST(ST_Y(m.exif_gps_location) AS numeric), 5) as lat, ROUND(CAST(ST_X(m.exif_gps_location) AS numeric), 5) as lng " +
            "FROM media_file m " +
            "WHERE m.exif_gps_location && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)")
    List<Object[]> findMediaInBoundsRaw(
            @Param("minLat") double minLat,
            @Param("minLng") double minLng,
            @Param("maxLat") double maxLat,
            @Param("maxLng") double maxLng);

}
