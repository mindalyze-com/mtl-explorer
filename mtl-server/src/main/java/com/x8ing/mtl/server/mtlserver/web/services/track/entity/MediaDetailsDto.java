package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.web.services.track.MediaKindResolver;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "mediaKind",
        "fileName",
        "folderPath",
        "fileExtension",
        "fileSizeBytes",
        "lastModifiedAt",
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
        "audioCodec",
        "gpsAltitudeMeters"
})
public record MediaDetailsDto(
        long id,
        TrackMediaDto.MEDIA_KIND mediaKind,
        String fileName,
        String folderPath,
        String fileExtension,
        Long fileSizeBytes,
        Date lastModifiedAt,
        Date exifGpsDate,
        Date exifDateImageTaken,
        String cameraMake,
        String cameraModel,
        String lensModel,
        Integer widthPixels,
        Integer heightPixels,
        Double apertureFNumber,
        Double exposureTimeSeconds,
        Integer isoSpeed,
        Double focalLengthMm,
        Integer focalLength35Mm,
        Double durationSeconds,
        Double frameRate,
        String videoCodec,
        String audioCodec,
        Double gpsAltitudeMeters
) {
    public static MediaDetailsDto from(MediaFile media) {
        IndexedFile file = media.getIndexedFile();
        String fileName = file == null ? null : file.getName();
        return new MediaDetailsDto(
                media.getId(),
                MediaKindResolver.resolve(fileName),
                fileName,
                file == null ? null : file.getPath(),
                fileExtension(fileName),
                file == null ? null : file.getSize(),
                file == null ? null : file.getLastModifiedDate(),
                media.getExifGpsDate(),
                media.getExifDateImageTaken(),
                media.getCameraMake(),
                media.getCameraModel(),
                media.getLensModel(),
                media.getWidthPixels(),
                media.getHeightPixels(),
                media.getApertureFNumber(),
                media.getExposureTimeSeconds(),
                media.getIsoSpeed(),
                media.getFocalLengthMm(),
                media.getFocalLength35Mm(),
                media.getDurationSeconds(),
                media.getFrameRate(),
                media.getVideoCodec(),
                media.getAudioCodec(),
                media.getGpsAltitudeMeters());
    }

    private static String fileExtension(String fileName) {
        if (fileName == null) return null;
        int separator = fileName.lastIndexOf('.');
        if (separator < 0 || separator == fileName.length() - 1) return null;
        return fileName.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
