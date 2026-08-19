package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.global.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@JsonPropertyOrder({
        "mediaRepository"
})
public class MediaIndexer {

    private static final int METADATA_READ_MAX_ATTEMPTS = 8;
    private static final long METADATA_READ_INITIAL_RETRY_DELAY_MS = 10L;
    private static final long METADATA_READ_MAX_RETRY_DELAY_MS = 1_000L;

    private final MediaRepository mediaRepository;

    public MediaIndexer(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void indexFile(IndexedFile indexedFile) {
        if (!SupportedMediaFormat.isSupportedFileName(indexedFile.getName())) {
            log.debug("MediaIndexer: skipping unsupported file type: {}", indexedFile.getName());
            return;
        }

        MediaFile mediaFile = extractMetadata(indexedFile);
        mediaRepository.save(mediaFile);
        log.info("MediaIndexer: indexed file={} gpsLocation={}", indexedFile.getName(), mediaFile.getExifGpsLocation());
    }

    /**
     * Refresh machine-extracted metadata without replacing the media row.
     * Keeping its ID preserves user corrections and other dependent records.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshFile(IndexedFile indexedFile) {
        if (!SupportedMediaFormat.isSupportedFileName(indexedFile.getName())) {
            log.debug("MediaIndexer: skipping unsupported file type: {}", indexedFile.getName());
            return;
        }

        List<MediaFile> existingMedia = mediaRepository.findAllByIndexedFileId(indexedFile.getId());
        if (existingMedia.isEmpty()) {
            indexFile(indexedFile);
            return;
        }
        if (existingMedia.size() != 1) {
            throw new IllegalStateException("Expected one media row for indexed file "
                                            + indexedFile.getId() + " but found " + existingMedia.size());
        }

        MediaFile extractedMedia = extractMetadata(indexedFile);
        MediaFile mediaFile = existingMedia.getFirst();
        applyExtractedMetadata(extractedMedia, mediaFile);
        mediaRepository.save(mediaFile);
        log.info("MediaIndexer: refreshed file={} mediaId={} gpsLocation={}",
                indexedFile.getName(), mediaFile.getId(), mediaFile.getExifGpsLocation());
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteFilesForIndexedFile(IndexedFile indexedFile) {
        if (indexedFile == null || indexedFile.getId() == null) {
            log.info("Nothing to delete because no indexed media file was provided.");
            return;
        }

        int deleted = mediaRepository.deleteByIndexedFileId(indexedFile.getId());
        log.info("MediaIndexer: deleted {} media row(s) for file={}", deleted, indexedFile.getName());
    }


    private static MediaFile extractMetadata(IndexedFile indexedFile) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setIndexedFile(indexedFile);
        if (!SupportedMediaFormat.supportsMetadataExtraction(indexedFile.getName())) {
            return mediaFile;
        }
        try {
            return enrichMetadata(mediaFile);
        } catch (IOException | ImageProcessingException e) {
            throw new IllegalStateException("Could not extract metadata from " + indexedFile.getFullPath(), e);
        }
    }

    private static void applyExtractedMetadata(MediaFile source, MediaFile target) {
        target.setExifGpsLocationLong(source.getExifGpsLocationLong());
        target.setExifGpsLocationLat(source.getExifGpsLocationLat());
        target.setExifGpsLocation(source.getExifGpsLocation());
        target.setGpsAltitudeMeters(source.getGpsAltitudeMeters());
        target.setExifGpsDate(source.getExifGpsDate());
        target.setExifDateImageTaken(source.getExifDateImageTaken());
        target.setCameraMake(source.getCameraMake());
        target.setCameraModel(source.getCameraModel());
        target.setLensModel(source.getLensModel());
        target.setWidthPixels(source.getWidthPixels());
        target.setHeightPixels(source.getHeightPixels());
        target.setApertureFNumber(source.getApertureFNumber());
        target.setExposureTimeSeconds(source.getExposureTimeSeconds());
        target.setIsoSpeed(source.getIsoSpeed());
        target.setFocalLengthMm(source.getFocalLengthMm());
        target.setFocalLength35Mm(source.getFocalLength35Mm());
        target.setDurationSeconds(source.getDurationSeconds());
        target.setFrameRate(source.getFrameRate());
        target.setVideoCodec(source.getVideoCodec());
        target.setAudioCodec(source.getAudioCodec());
    }

    private static MediaFile enrichMetadata(MediaFile mediaFile) throws IOException, ImageProcessingException {

        Metadata metadata = null;
        Exception lastReadFailure = null;
        Path file;
        try {
            file = Paths.get(mediaFile.getIndexedFile().getFullPath());
        } catch (InvalidPathException e) {
            throw new IOException("Path contains characters unmappable in the current file-system encoding. " +
                                  "Ensure the JVM is started with -Dsun.jnu.encoding=UTF-8. path=" +
                                  mediaFile.getIndexedFile().getFullPath(), e);
        }

        // try to read the meta data first...
        for (int attempt = 1; attempt <= METADATA_READ_MAX_ATTEMPTS; attempt++) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file.toFile()))) {
                metadata = ImageMetadataReader.readMetadata(is, Files.size(file), file.getFileName().toString());

                if (metadata != null) {
                    if ("avi".equalsIgnoreCase(getFileExtension(file.getFileName().toString()))) {
                        AviXmpMetadataReader.enrich(file, metadata);
                    }
                    break;
                }
            } catch (Exception e0) {
                lastReadFailure = e0;
                if (attempt == METADATA_READ_MAX_ATTEMPTS) {
                    break;
                }
                long sleep = Math.min(
                        METADATA_READ_INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)),
                        METADATA_READ_MAX_RETRY_DELAY_MS);
                log.info("Could not read file yet. Might be locked. Sleep shortly. fileId={}, fileName={}, attempt={}, sleep={} ms, exception={}",
                        mediaFile.getIndexedFile().getId(),
                        mediaFile.getIndexedFile().getName(),
                        attempt,
                        sleep,
                        e0.toString());
                Utils.sleep(sleep);
            }
        }

        if (metadata == null) {
            throw new IOException("Could not read media metadata after " + METADATA_READ_MAX_ATTEMPTS
                                  + " attempts: " + file, lastReadFailure);
        }

        try {
            // Fetch GPS coordinates
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            if (gpsDirectory != null) {
                Date gpsDate = gpsDirectory.getGpsDate();
                mediaFile.setExifGpsDate(gpsDate);
            }

            MediaGpsLocationResolver.Location location = MediaGpsLocationResolver.resolve(metadata);
            if (location != null) {
                setGpsLocation(mediaFile, location.latitude(), location.longitude());
            }
        } catch (Exception e0) {
            log.warn("Could not get GPS coordinates. Ignore. e=" + e0, e0);
        }

        mediaFile.setExifDateImageTaken(MediaCaptureDateResolver.resolve(metadata));

        // Fetch camera type
        try {
            ExifIFD0Directory exifIFD0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0Directory != null) {
                mediaFile.setCameraMake(StringUtils.trim(exifIFD0Directory.getString(ExifIFD0Directory.TAG_MAKE)));
                mediaFile.setCameraModel(StringUtils.trim(exifIFD0Directory.getString(ExifIFD0Directory.TAG_MODEL)));
            }
        } catch (Exception e0) {
            log.warn("Could not get camera info. Ignore. e=" + e0, e0);
        }

        MediaTechnicalMetadataResolver.TechnicalMetadata technical =
                MediaTechnicalMetadataResolver.resolve(metadata);
        mediaFile.setGpsAltitudeMeters(technical.gpsAltitudeMeters());
        mediaFile.setLensModel(technical.lensModel());
        mediaFile.setWidthPixels(technical.widthPixels());
        mediaFile.setHeightPixels(technical.heightPixels());
        mediaFile.setApertureFNumber(technical.apertureFNumber());
        mediaFile.setExposureTimeSeconds(technical.exposureTimeSeconds());
        mediaFile.setIsoSpeed(technical.isoSpeed());
        mediaFile.setFocalLengthMm(technical.focalLengthMm());
        mediaFile.setFocalLength35Mm(technical.focalLength35Mm());
        mediaFile.setDurationSeconds(technical.durationSeconds());
        mediaFile.setFrameRate(technical.frameRate());
        mediaFile.setVideoCodec(technical.videoCodec());
        mediaFile.setAudioCodec(technical.audioCodec());

        return mediaFile;
    }

    static void setGpsLocation(MediaFile mediaFile, double latitude, double longitude) {
        mediaFile.setExifGpsLocationLong(longitude);
        mediaFile.setExifGpsLocationLat(latitude);
        CoordinateXY coordinate = new CoordinateXY(longitude, latitude);
        mediaFile.setExifGpsLocation(new GeometryFactory().createPoint(coordinate));
    }

    private static String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return "";  // Return empty string if there is no dot
        }
        return fileName.substring(lastIndexOfDot + 1);
    }
}
