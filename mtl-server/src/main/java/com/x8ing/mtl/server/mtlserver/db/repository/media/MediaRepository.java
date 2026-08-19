package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<MediaFile, Long> {

    long countByIdIn(List<Long> ids);

    List<MediaFile> findAllByIndexedFileId(Long indexedFileId);


    @Modifying
    @Query("DELETE FROM MediaFile m WHERE m.indexedFile.id = :indexedFileId")
    int deleteByIndexedFileId(@Param("indexedFileId") Long indexedFileId);


    /**
     * "JOIN FETCH" to join on the DB and return both entities, as
     * JPA won't consider the EAGER fetch on the entity.
     */
    @Query("SELECT m FROM MediaFile m JOIN FETCH m.indexedFile WHERE m.exifGpsLocation is not null")
    List<MediaFile> findMediaWithLocationInfo();

    /**
     * Find resolved media points using the dedicated projection and its GIST index.
     * The EXIF fallback keeps original photo markers available while asynchronous
     * correlation is pending for initial or newly indexed media.
     */
    @Query(nativeQuery = true, value =
            "SELECT resolved.media_id, " +
            "       ROUND(CAST(ST_Y(resolved.location) AS numeric), 5) AS lat, " +
            "       ROUND(CAST(ST_X(resolved.location) AS numeric), 5) AS lng " +
            "FROM media_resolved_location resolved " +
            "WHERE resolved.location && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326) " +
            "UNION ALL " +
            "SELECT media.id, " +
            "       ROUND(CAST(ST_Y(media.exif_gps_location) AS numeric), 5) AS lat, " +
            "       ROUND(CAST(ST_X(media.exif_gps_location) AS numeric), 5) AS lng " +
            "FROM media_file media " +
            "WHERE media.exif_gps_location && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326) " +
            "  AND NOT EXISTS (SELECT 1 FROM media_resolved_location resolved WHERE resolved.media_id = media.id)")
    List<Object[]> findMediaInBoundsRaw(
            @Param("minLat") double minLat,
            @Param("minLng") double minLng,
            @Param("maxLat") double maxLat,
            @Param("maxLng") double maxLng);

}
