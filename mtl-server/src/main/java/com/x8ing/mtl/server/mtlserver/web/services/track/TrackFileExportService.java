package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "converterService",
        "gpsWatchDirectory"
})
public class TrackFileExportService {

    public static final String GPX_MEDIA_TYPE_VALUE = "application/gpx+xml";
    public static final MediaType GPX_MEDIA_TYPE = MediaType.parseMediaType(GPX_MEDIA_TYPE_VALUE);

    private static final String GPX_EXTENSION = ".gpx";
    private static final IndexedFile.IndexerStatus REMOVED = IndexedFile.IndexerStatus.REMOVED;
    private static final IndexedFile.IndexerStatus EXCLUDED = IndexedFile.IndexerStatus.EXCLUDED;

    private final GpsTrackRepository gpsTrackRepository;
    private final TrackFileConverterService converterService;
    private final Path gpsWatchDirectory;

    public TrackFileExportService(GpsTrackRepository gpsTrackRepository,
                                  TrackFileConverterService converterService,
                                  @Value("${mtl.gpx-watch-directory}") String gpsWatchDirectory) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.converterService = converterService;
        this.gpsWatchDirectory = Paths.get(gpsWatchDirectory);
    }

    public TrackFileDownload sourceFile(Long gpsTrackId) {
        SourceFile source = requireSourceFile(gpsTrackId);
        return sourceDownload(source);
    }

    public TrackFileDownload gpx(Long gpsTrackId) {
        SourceFile source = requireSourceFile(gpsTrackId);
        if (source.format() == SupportedTrackFormat.GPX) {
            return new TrackFileDownload(
                    toGpxFileName(source.fileName()),
                    GPX_MEDIA_TYPE,
                    source.size(),
                    source.lastModifiedMillis(),
                    buildEtag("gpx", source.indexedFileId(), source.size(), source.lastModifiedMillis()),
                    new FileSystemResource(source.path())
            );
        }

        String gpxXml;
        try {
            gpxXml = converterService.convertToGpx(source.path(), source.format());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Track source cannot be converted to GPX", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to convert track source to GPX", e);
        }

        byte[] bytes = gpxXml.getBytes(StandardCharsets.UTF_8);
        return new TrackFileDownload(
                toGpxFileName(source.fileName()),
                GPX_MEDIA_TYPE,
                bytes.length,
                source.lastModifiedMillis(),
                buildEtag("gpx", source.indexedFileId(), bytes.length, source.lastModifiedMillis()),
                new ByteArrayResource(bytes)
        );
    }

    private TrackFileDownload sourceDownload(SourceFile source) {
        return new TrackFileDownload(
                source.fileName(),
                MediaType.APPLICATION_OCTET_STREAM,
                source.size(),
                source.lastModifiedMillis(),
                buildEtag("source", source.indexedFileId(), source.size(), source.lastModifiedMillis()),
                new FileSystemResource(source.path())
        );
    }

    private SourceFile requireSourceFile(Long gpsTrackId) {
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId)
                .orElseThrow(() -> notFound("Track source file not found"));
        IndexedFile indexedFile = track.getIndexedFile();
        if (indexedFile == null) {
            throw notFound("Track has no indexed source file");
        }
        if (!GPXDirectoryWatcherService.INDEX_GPS.equals(indexedFile.getIndex())) {
            throw notFound("Track source file is not a GPS indexed file");
        }
        if (indexedFile.getIndexerStatus() == REMOVED || indexedFile.getIndexerStatus() == EXCLUDED) {
            throw notFound("Track source file is not available");
        }

        Path sourcePath = resolveIndexedPath(indexedFile);
        Path realSourcePath = realReadableFileInsideGpsRoot(sourcePath);
        SupportedTrackFormat format = SupportedTrackFormat.fromPath(realSourcePath);
        if (format == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported track source format");
        }

        try {
            return new SourceFile(
                    indexedFile.getId(),
                    realSourcePath,
                    indexedFile.getName(),
                    format,
                    Files.size(realSourcePath),
                    Files.getLastModifiedTime(realSourcePath).toMillis()
            );
        } catch (IOException e) {
            throw notFound("Track source file is not available", e);
        }
    }

    private Path resolveIndexedPath(IndexedFile indexedFile) {
        try {
            if (indexedFile.getFullPath() != null && !indexedFile.getFullPath().isBlank()) {
                return Paths.get(indexedFile.getFullPath()).normalize();
            }
            if (indexedFile.getBasePath() == null || indexedFile.getName() == null) {
                throw notFound("Track source file is not available");
            }
            if (indexedFile.getPath() == null || indexedFile.getPath().isBlank()) {
                return Paths.get(indexedFile.getBasePath(), indexedFile.getName()).normalize();
            }
            return Paths.get(indexedFile.getBasePath(), indexedFile.getPath(), indexedFile.getName()).normalize();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw notFound("Track source file is not available", e);
        }
    }

    private Path realReadableFileInsideGpsRoot(Path sourcePath) {
        try {
            Path root = gpsWatchDirectory.toRealPath();
            Path source = sourcePath.toRealPath();
            if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
                throw notFound("Track source file is not available");
            }
            return source;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw notFound("Track source file is not available", e);
        }
    }

    private static String toGpxFileName(String fileName) {
        String fallback = "track";
        String safeFileName = (fileName == null || fileName.isBlank()) ? fallback : fileName;
        String lowerFileName = safeFileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(GPX_EXTENSION)) {
            return safeFileName;
        }
        int dot = safeFileName.lastIndexOf('.');
        String baseName = dot > 0 ? safeFileName.substring(0, dot) : safeFileName;
        return baseName + GPX_EXTENSION;
    }

    private static String buildEtag(String kind, Long indexedFileId, long size, long lastModifiedMillis) {
        return "\"track-" + kind + '-' + indexedFileId + '-' + size + '-' + lastModifiedMillis + "\"";
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException notFound(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message, cause);
    }

    @JsonPropertyOrder({
            "indexedFileId",
            "path",
            "fileName",
            "format",
            "size",
            "lastModifiedMillis"
    })
    private record SourceFile(Long indexedFileId,
                              Path path,
                              String fileName,
                              SupportedTrackFormat format,
                              long size,
                              long lastModifiedMillis) {
    }

    @Getter
    @JsonPropertyOrder({
            "fileName",
            "mediaType",
            "contentLength",
            "lastModifiedMillis",
            "etag",
            "resource"
    })
    public static class TrackFileDownload {

        private final String fileName;
        private final MediaType mediaType;
        private final long contentLength;
        private final long lastModifiedMillis;
        private final String etag;
        private final Resource resource;

        public TrackFileDownload(String fileName,
                                 MediaType mediaType,
                                 long contentLength,
                                 long lastModifiedMillis,
                                 String etag,
                                 Resource resource) {
            this.fileName = fileName;
            this.mediaType = mediaType;
            this.contentLength = contentLength;
            this.lastModifiedMillis = lastModifiedMillis;
            this.etag = etag;
            this.resource = resource;
        }
    }
}
