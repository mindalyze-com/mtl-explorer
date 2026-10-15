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

}
