package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MediaIndexer {

    private final MediaRepository mediaRepository;

    public MediaIndexer(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void indexFile(IndexedFile indexedFile) {
        if (isSupportedFileType(indexedFile.getName())) {
            try {
                MediaFile mediaFile = new MediaFile();
                mediaFile.setIndexedFile(indexedFile);
                enrichExifData(mediaFile);
                mediaRepository.save(mediaFile);
                log.info("MediaIndexer: indexed file={} gpsLocation={}", indexedFile.getName(), mediaFile.getExifGpsLocation());
            } catch (Exception e0) {
                log.warn("MediaIndexer: could not index file={} error={}", indexedFile.getFullPath(), e0.toString(), e0);
            }
        } else {
            log.debug("MediaIndexer: skipping unsupported file type: {}", indexedFile.getName());
        }

    }


    private static MediaFile enrichExifData(MediaFile mediaFile) throws IOException, ImageProcessingException {

        Metadata metadata = null;
        Path file;
        try {
            file = Paths.get(mediaFile.getIndexedFile().getFullPath());
        } catch (InvalidPathException e) {
            throw new IOException("Path contains characters unmappable in the current file-system encoding. " +
                                  "Ensure the JVM is started with -Dsun.jnu.encoding=UTF-8. path=" +
                                  mediaFile.getIndexedFile().getFullPath(), e);
        }

        // try to read the meta data first...
        final int MAX_ATTEMPTS = 8;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file.toFile()))) {
                metadata = ImageMetadataReader.readMetadata(is);

                if (metadata != null) {
                    break;
                }
            } catch (Exception e0) {
                long sleep = Math.min(10L * (1L << (attempt - 1)), 1_000L);
                log.info("Could not read file yet. Might be locked. Sleep shortly. fileId={}, fileName={}, attempt={}, sleep={} ms, exception={}",
                        mediaFile.getIndexedFile().getId(),
                        mediaFile.getIndexedFile().getName(),
                        attempt,
                        sleep,
                        e0.toString());
                Utils.sleep(sleep);
            }
        }

        // Fetch date taken
        if (metadata != null) {

            try {
                // Fetch GPS coordinates
                GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

                if (gpsDirectory != null) {
                    Date gpsDate = gpsDirectory.getGpsDate();
                    mediaFile.setExifGpsDate(gpsDate);
                }

                if (gpsDirectory != null && gpsDirectory.getGeoLocation() != null) {
                    mediaFile.setExifGpsLocationLong(gpsDirectory.getGeoLocation().getLatitude());
                    mediaFile.setExifGpsLocationLat(gpsDirectory.getGeoLocation().getLongitude());
                    CoordinateXY c = new CoordinateXY(gpsDirectory.getGeoLocation().getLongitude(), gpsDirectory.getGeoLocation().getLatitude());
                    mediaFile.setExifGpsLocation(new GeometryFactory().createPoint(c));
                }
            } catch (Exception e0) {
                log.warn("Could not get GPS coordinates. Ignore. e=" + e0, e0);
            }


            try {
                ExifSubIFDDirectory exifSubIFDDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (exifSubIFDDirectory != null) {
                    Date date = exifSubIFDDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    if (date == null) {
                        date = exifSubIFDDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME);
                        if (date == null) {
                            date = exifSubIFDDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                        }
                    }
                    mediaFile.setExifDateImageTaken(date);
                }
            } catch (Exception e0) {
                log.warn("Could not get date taken. Ignore. e=" + e0, e0);
            }


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
        }

        return mediaFile;
    }


    private static boolean isSupportedFileType(String fileName) {
        List<String> supportedFileExtensions = Arrays.asList(
                // JPEG / TIFF
                "jpg", "jpeg", "tiff", "tif",
                // iPhone / modern mobile
                "heic", "heif",
                // RAW formats
                "dng", "nef", "cr2", "cr3", "orf", "arw", "rw2", "rwl", "srw", "raf", "pef", "x3f",
                // Other image formats
                "psd", "png", "bmp", "gif", "ico", "pcx", "webp",
                // Video formats (metadata-extractor supports GPS from MP4/MOV/QuickTime)
                "mp4", "mov", "m4v", "3gp", "avi"
        );

        String fileExtension = getFileExtension(fileName);
        return supportedFileExtensions.contains(fileExtension.toLowerCase());
    }

    private static String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return "";  // Return empty string if there is no dot
        }
        return fileName.substring(lastIndexOfDot + 1);
    }
}
