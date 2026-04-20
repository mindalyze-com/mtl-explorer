package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;


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
    @Transactional
    public void processCreateOrChange(String index, long fileId, boolean changed) {
        IndexedFile f = indexerRepository.findById(fileId).orElse(null);
        if (f == null) {
            log.warn("GPX process: fileId={} disappeared", fileId);
            return;
        }
        // Domain operations only; let exceptions bubble up so observer can signal completion
        if (changed) {
            gpsStoreService.deleteTracksForFile(f);
        }

        // Detect format and convert non-GPX files to GPX XML in-memory via GPSBabel
        SupportedTrackFormat format = SupportedTrackFormat.fromPath(Paths.get(f.getFullPath()));
        if (format != null && format.needsConversion()) {
            try {
                String gpxXml = converterService.convertToGpx(Paths.get(f.getFullPath()), format);
                gpsStoreService.readAndSave(f, gpxXml);
            } catch (Exception e) {
                throw new RuntimeException("GPSBabel conversion failed for " + f.getFullPath(), e);
            }
        } else {
            gpsStoreService.readAndSave(f);
        }
    }

    /**
     * Process a deleted GPX file.
     *
     * @param index  the index name (e.g., "GPS")
     * @param fileId the indexed file ID
     */
    @Transactional
    public void processDelete(String index, long fileId) {
        IndexedFile f = indexerRepository.findById(fileId).orElse(null);
        if (f == null) {
            log.warn("GPX delete: fileId={} disappeared", fileId);
            return;
        }
        // Domain operations only; let exceptions bubble up so observer can signal completion
        gpsStoreService.deleteWithAllDependencies(f);
    }
}
