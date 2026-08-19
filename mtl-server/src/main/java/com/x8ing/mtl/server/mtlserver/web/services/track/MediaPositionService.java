package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationJob;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.ManualMediaLocationRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTimeCorrectionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class MediaPositionService {

    static final int MIN_OFFSET_SECONDS = -24 * 60 * 60;
    static final int MAX_OFFSET_SECONDS = 24 * 60 * 60;
    static final int MAX_BATCH_MEDIA_IDS = 5_000;

    private final MediaRepository mediaRepository;
    private final MediaCorrelationRepository correlationRepository;
    private final MediaCorrelationAtomicWorker correlationWorker;

    public MediaPositionService(
            MediaRepository mediaRepository,
            MediaCorrelationRepository correlationRepository,
            MediaCorrelationAtomicWorker correlationWorker) {
        this.mediaRepository = mediaRepository;
        this.correlationRepository = correlationRepository;
        this.correlationWorker = correlationWorker;
    }

    @Transactional
    public void setManualLocation(long mediaId, ManualMediaLocationRequest request) {
        requireMedia(mediaId);
        if (!Double.isFinite(request.latitude()) || request.latitude() < -90 || request.latitude() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(request.longitude()) || request.longitude() < -180 || request.longitude() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Longitude must be between -180 and 180");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.length() > 1_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location note is too long");
        }
        correlationRepository.upsertManualLocation(mediaId, request.latitude(), request.longitude(), note);
        correlationWorker.rebuildMediaIds(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
    }

    @Transactional
    public void clearManualLocation(long mediaId) {
        requireMedia(mediaId);
        if (correlationRepository.deleteManualLocation(mediaId) > 0) {
            correlationWorker.rebuildMediaIds(List.of(mediaId), MediaCorrelationJob.ALGORITHM_VERSION);
        }
    }

    @Transactional
    public void saveTimeCorrection(MediaTimeCorrectionRequest request) {
        if (request.mediaIds() == null || request.mediaIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one media id is required");
        }
        if (request.mediaIds().size() > MAX_BATCH_MEDIA_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many media ids in one correction request");
        }
        if (request.mediaIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media ids must not contain null");
        }
        if (request.offsetSeconds() < MIN_OFFSET_SECONDS || request.offsetSeconds() > MAX_OFFSET_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Camera offset must be within plus or minus 24 hours");
        }
        List<Long> mediaIds = new LinkedHashSet<>(request.mediaIds()).stream().toList();
        if (mediaRepository.countByIdIn(mediaIds) != mediaIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more media items were not found");
        }
        List<Long> updatedIds = correlationRepository.saveTimeCorrection(mediaIds, request.offsetSeconds());
        if (!updatedIds.isEmpty()) {
            correlationWorker.rebuildMediaIds(updatedIds, MediaCorrelationJob.ALGORITHM_VERSION);
        }
    }

    private void requireMedia(long mediaId) {
        if (!mediaRepository.existsById(mediaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
        }
    }
}
