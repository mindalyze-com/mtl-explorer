package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDetailsDtoTest {

    @Test
    void mapsOnlyUserFacingFileAndTechnicalDetails() {
        Date modified = Date.from(Instant.parse("2026-08-18T10:15:30Z"));
        IndexedFile file = new IndexedFile();
        file.setName("TRAIL.CLIP.MOV");
        file.setPath("/Trips/Alps");
        file.setFullPath("/private/server/Trips/Alps/TRAIL.CLIP.MOV");
        file.setHash("private-hash");
        file.setSize(12_345_678L);
        file.setLastModifiedDate(modified);

        MediaFile media = new MediaFile();
        media.setId(400_018L);
        media.setIndexedFile(file);
        media.setWidthPixels(3840);
        media.setHeightPixels(2160);
        media.setDurationSeconds(84.25);
        media.setVideoCodec("hvc1");

        MediaDetailsDto result = MediaDetailsDto.from(media);

        assertThat(result.id()).isEqualTo(400_018L);
        assertThat(result.mediaKind()).isEqualTo(TrackMediaDto.MEDIA_KIND.VIDEO);
        assertThat(result.fileName()).isEqualTo("TRAIL.CLIP.MOV");
        assertThat(result.folderPath()).isEqualTo("/Trips/Alps");
        assertThat(result.fileExtension()).isEqualTo("mov");
        assertThat(result.fileSizeBytes()).isEqualTo(12_345_678L);
        assertThat(result.lastModifiedAt()).isEqualTo(modified);
        assertThat(result.widthPixels()).isEqualTo(3840);
        assertThat(result.heightPixels()).isEqualTo(2160);
        assertThat(result.durationSeconds()).isEqualTo(84.25);
        assertThat(result.videoCodec()).isEqualTo("hvc1");
    }
}
