package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaKindResolverTest {

    @Test
    void usesTheSharedBroadFormatRegistry() {
        assertThat(MediaKindResolver.resolve("archive.MXF")).isEqualTo(TrackMediaDto.MEDIA_KIND.VIDEO);
        assertThat(MediaKindResolver.resolve("photo.AVIF")).isEqualTo(TrackMediaDto.MEDIA_KIND.IMAGE);

        String sql = MediaKindResolver.sqlCaseExpression("indexed.name");
        assertThat(sql).contains("mkv", "mxf", "webm");
        assertThat(sql).doesNotContain("avif");
    }
}
