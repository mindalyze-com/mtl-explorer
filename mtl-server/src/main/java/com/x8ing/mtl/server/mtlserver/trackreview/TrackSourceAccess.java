package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralizes server path permissions; reading and previewing never modify a watched source.
 */
@Component
@Slf4j
public class TrackSourceAccess {
    private final IndexerRepository files;
    private final TrackFileConverterService converter;
    private final Path watchRoot;

    public TrackSourceAccess(IndexerRepository files, TrackFileConverterService converter,
                             @Value("${mtl.gpx-watch-directory}") String root) {
        this.files = files;
        this.converter = converter;
        this.watchRoot = Path.of(root).toAbsolutePath().normalize();
    }

    public record Source(IndexedFile file, Path path, byte[] bytes, String hash, SupportedTrackFormat format) {
    }

    public record SourceLocation(IndexedFile file, Path path) {
    }

    public IndexedFile indexedFile(long id) {
        IndexedFile file = files.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source file not found."));
        if (!GPXDirectoryWatcherService.INDEX_GPS.equals(file.getIndex()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source is not a GPS recording.");
        return file;
    }

    public Source read(long id) {
        return read(id, false);
    }

    /**
     * Larger sources retain a streamed revision hash but no in-memory source document.
     */
    public Source readForAssessment(long id) {
        return read(id, true);
    }

    public SourceLocation locate(long id) {
        IndexedFile file = indexedFile(id);
        try {
            Path path = Path.of(file.getFullPath()).toRealPath();
            if (!path.startsWith(watchRoot.toRealPath()) || !Files.isRegularFile(path))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Source must be a regular file inside the configured GPS directory.");
            return new SourceLocation(file, path);
        } catch (IOException e) {
            throw unreadable(id, e);
        }
    }

    private Source read(long id, boolean retainLargeRevision) {
        var location = locate(id);
        try {
            byte[] bytes = null;
            if (Files.size(location.path()) <= GpxDocument.MAX_SOURCE_BYTES) {
                try (var stream = Files.newInputStream(location.path())) {
                    bytes = stream.readNBytes(GpxDocument.MAX_SOURCE_BYTES + 1);
                }
                if (bytes.length > GpxDocument.MAX_SOURCE_BYTES) bytes = null;
            }
            if (bytes == null && !retainLargeRevision)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Source exceeds the 64 MiB review limit. Download the original for external review.");
            String hash;
            if (bytes != null) hash = GpxDocument.hash(bytes);
            else try (var stream = Files.newInputStream(location.path())) {
                hash = GpxDocument.hash(stream);
            }
            return new Source(location.file(), location.path(), bytes, hash, SupportedTrackFormat.fromPath(location.path()));
        } catch (IOException e) {
            throw unreadable(id, e);
        }
    }

    private ResponseStatusException unreadable(long id, IOException e) {
        log.warn("GPS source read failed: sourceId={} type={} message={}", id, e.getClass().getSimpleName(), e.getMessage());
        log.debug("GPS source read failure", e);
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source could not be read. Check the file and server read access.", e);
    }

    public byte[] gpx(Source source) {
        return gpx(source, false);
    }

    public byte[] gpxForImport(Source source) {
        return gpx(source, true);
    }

    private byte[] gpx(Source source, boolean backgroundImport) {
        if (source.bytes() == null) throw new GpxDocument.ReviewLimitExceeded("Source exceeds the 64 MiB review limit.");
        if (source.format() == SupportedTrackFormat.GPX) return source.bytes();
        if (source.format() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This source format cannot be converted to GPX.");
        try {
            String converted = backgroundImport ? converter.convertToGpxForImport(source.path(), source.format())
                    : converter.convertToGpx(source.path(), source.format());
            byte[] bytes = converted.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > GpxDocument.MAX_SOURCE_BYTES)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Converted source exceeds the 64 MiB review limit.");
            return bytes;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("GPS source conversion failed: sourceId={} type={} message={}", source.file().getId(), e.getClass().getSimpleName(), e.getMessage());
            log.debug("GPS source conversion failure", e);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The server could not convert this source to GPX. Download the original or check the server's converter setup.", e);
        }
    }

    public void requireUnchanged(Source source, String expectedHash) {
        if (expectedHash == null || !source.hash().equals(expectedHash))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The source changed. Reassess the current file before deciding.");
    }
}
