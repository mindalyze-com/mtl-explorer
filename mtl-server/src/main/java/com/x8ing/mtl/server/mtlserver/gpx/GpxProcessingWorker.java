package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexedFileLookup;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.List;


/**
 * Dedicated worker bean for GPX file processing.
 * <p>
 * This bean is separate from GPXDirectoryWatcherService to ensure that Spring's
 * transaction proxy is properly invoked when methods are called from executor threads.
 * Direct calls on 'this' bypass the proxy and break transaction management.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@JsonPropertyOrder({
        "indexerRepository",
        "gpsStoreService",
        "converterService"
})
public class GpxProcessingWorker {

    private final IndexerRepository indexerRepository;
    private final GPXStoreService gpsStoreService;
    private final TrackFileConverterService converterService;

    /**
     * Process a new or changed GPX file.
     *
     * @param index   the index name (e.g., "GPS")
     * @param fileId  the indexed file ID
     * @param changed true if file was changed (requires delete first), false if new
     */
    @Transactional(noRollbackFor = GpxImportFailedException.class)
    public void processCreateOrChange(String index, long fileId, boolean changed) {
        TimingCollector timing = new TimingCollector();
        IndexedFile f = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "GPX process");
        if (f == null) {
            return;
        }
        // Domain operations only; let exceptions bubble up so observer can signal completion
        if (changed) {
            try {
                timing.time("delete old tracks", () -> gpsStoreService.deleteTracksForFile(f));
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete existing tracks for " + f.getFullPath(), e);
            }
        }

        // Detect format and convert non-GPX files to GPX XML in-memory via GPSBabel
        SupportedTrackFormat format = SupportedTrackFormat.fromPath(Paths.get(f.getFullPath()));
        List<GPXReader.LoadResult> loadResults;
        if (format != null && format.needsConversion()) {
            try {
                String gpxXml = timing.time("gpsbabel", () -> converterService.convertToGpx(Paths.get(f.getFullPath()), format));
                loadResults = gpsStoreService.readAndSave(f, gpxXml, timing);
            } catch (Exception e) {
                throw new RuntimeException("GPSBabel conversion failed for " + f.getFullPath(), e);
            }
        } else {
            loadResults = gpsStoreService.readAndSave(f, null, timing);
        }
        verifyLoadResults(loadResults);
    }

    /**
     * Process a deleted GPX file.
     *
     * @param index  the index name (e.g., "GPS")
     * @param fileId the indexed file ID
     */
    @Transactional
    public void processDelete(String index, long fileId) {
        IndexedFile f = IndexedFileLookup.findOrLog(indexerRepository, fileId, log, "GPX delete");
        if (f == null) {
            return;
        }
        // Domain operations only; let exceptions bubble up so observer can signal completion
        gpsStoreService.deleteWithAllDependencies(f);
    }

    private static void verifyLoadResults(List<GPXReader.LoadResult> loadResults) {
        if (loadResults == null || loadResults.isEmpty()) {
            throw new GpxImportFailedException("GPS import produced no result");
        }
        boolean failed = loadResults.stream()
                .anyMatch(result -> result == null
                                    || result.gpsTrack == null
                                    || GpsTrack.LOAD_STATUS.FAILED.equals(result.gpsTrack.getLoadStatus()));
        if (failed) {
            throw new GpxImportFailedException("GPS import finished with status FAILED");
        }
    }

    static final class GpxImportFailedException extends RuntimeException {
        GpxImportFailedException(String message) {
            super(message);
        }
    }
}
