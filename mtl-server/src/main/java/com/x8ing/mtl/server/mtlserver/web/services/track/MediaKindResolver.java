package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.SupportedMediaFormat;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto;

/** Keeps media-kind classification consistent across timelines and statistics. */
public final class MediaKindResolver {

    private MediaKindResolver() {
    }

    public static TrackMediaDto.MEDIA_KIND resolve(String fileName) {
        return SupportedMediaFormat.isVideoFileName(fileName)
                ? TrackMediaDto.MEDIA_KIND.VIDEO
                : TrackMediaDto.MEDIA_KIND.IMAGE;
    }

    public static String sqlCaseExpression(String fileNameColumn) {
        return "CASE WHEN LOWER(" + fileNameColumn + ") ~ '[.]("
               + SupportedMediaFormat.videoExtensionSqlAlternation()
               + ")$' THEN 'VIDEO' ELSE 'IMAGE' END";
    }
}
