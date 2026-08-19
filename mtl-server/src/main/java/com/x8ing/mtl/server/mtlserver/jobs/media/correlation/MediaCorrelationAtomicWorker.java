package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MediaCorrelationAtomicWorker {

    private final MediaCorrelationRepository repository;

    public MediaCorrelationAtomicWorker(MediaCorrelationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expandTrackWork(int batchSize) {
        return repository.expandTrackWork(batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuildMediaBatch(int batchSize, int algorithmVersion) {
        List<Long> mediaIds = repository.claimMediaWork(batchSize);
        try {
            repository.rebuildMedia(mediaIds, algorithmVersion);
        } catch (RuntimeException exception) {
            throw new MediaCorrelationBatchException(mediaIds, exception);
        }
        return mediaIds.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rebuildMediaId(long mediaId, int algorithmVersion) {
        if (!repository.claimSpecificMediaWork(mediaId)) {
            return false;
        }
        repository.rebuildMedia(List.of(mediaId), algorithmVersion);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferMediaFailure(long mediaId, String error, int retryDelaySeconds) {
        repository.deferMediaWork(mediaId, error, retryDelaySeconds);
    }

    @Transactional
    public void rebuildMediaIds(List<Long> mediaIds, int algorithmVersion) {
        repository.rebuildMedia(mediaIds, algorithmVersion);
    }
}
