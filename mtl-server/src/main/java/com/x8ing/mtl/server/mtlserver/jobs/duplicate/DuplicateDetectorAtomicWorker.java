package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * For correct transaction handling, we need an own instance
 */
@Slf4j
@Service
@JsonPropertyOrder({
        "gpsTrackRepository"
})
public class DuplicateDetectorAtomicWorker {

    private static final Duration CONTINUATION_GAP_TOLERANCE = Duration.ofMinutes(2);

    private final GpsTrackRepository gpsTrackRepository;

    public DuplicateDetectorAtomicWorker(GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long trackId, Duration timeTolerance, Double distanceTolerance) {
        GpsTrack track = gpsTrackRepository.findById(trackId).orElseThrow();
        if (!track.getDuplicateStatus().equals(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)) {
            return;
        }

        Date startDate = track.getStartDate();
        Date d1 = null;
        Date d2 = null;

        if (startDate != null) {
            d1 = Date.from(startDate.toInstant().minus(timeTolerance));
            d2 = Date.from(startDate.toInstant().plus(timeTolerance));
        }

        // find all duplicates
        List<Long> similarTrackIds = gpsTrackRepository.findSimilarTracksWithinTimeRangeForTrack(d1, d2, distanceTolerance, track.getId());

        if (!similarTrackIds.isEmpty()) {
            List<GpsTrack> similarTracks = gpsTrackRepository.findAllById(similarTrackIds);

            // add our new one to the similar tracks, as we consider all candidates to become the new unique track
            // we do this, as sometimes we find a new similar track, but it was actually a better one as one we found before
            List<GpsTrack> similarTracksSorted = new ArrayList<>(
                    similarTracks.stream()
                            .filter(candidate -> shouldKeepAsDuplicateCandidate(track, candidate))
                            .toList()
            );
            // already found as similar! similarTracksSorted.add(track);

            if (!similarTracksSorted.isEmpty()) {
                // now evaluate which one is the best, by sorting them according to their "score".
                similarTracksSorted.sort(new DuplicateComparator());

                // get our new unique track. it's the first one
                GpsTrack uniqueTrack = similarTracksSorted.getFirst();
                uniqueTrack.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
                uniqueTrack.setDuplicateOf(null);

                // now mark all others as duplicates
                similarTracksSorted.stream().skip(1).forEach(gpsTrack -> {
                    gpsTrack.setDuplicateOf(uniqueTrack.getId());
                    gpsTrack.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE);
                    log.info("Marked GpsTrack {} ({}) to be an duplicate of {} ({})", gpsTrack.getIndexedFile().getName(), gpsTrack.getId(), uniqueTrack.getIndexedFile().getName(), uniqueTrack.getId());

                });

                // finally save them all
                gpsTrackRepository.saveAll(similarTracksSorted);
            } else {
                markUnique(track);
            }


        } else {
            // we did not find any similar tracks... we have a unique one. we mark it like that from now on
            markUnique(track);

        }
    }

    private boolean shouldKeepAsDuplicateCandidate(GpsTrack track, GpsTrack candidate) {
        if (track == null || candidate == null) {
            return false;
        }

        if (track.getId() != null && track.getId().equals(candidate.getId())) {
            return true;
        }

        if (isLikelyContinuation(track, candidate)) {
            log.info("Skipped duplicate candidate {} ({}) for {} ({}) because the tracks look like consecutive fragments within {}.",
                    candidate.getIndexedFile() != null ? candidate.getIndexedFile().getName() : null,
                    candidate.getId(),
                    track.getIndexedFile() != null ? track.getIndexedFile().getName() : null,
                    track.getId(),
                    CONTINUATION_GAP_TOLERANCE);
            return false;
        }

        return true;
    }

    private boolean isLikelyContinuation(GpsTrack track, GpsTrack candidate) {
        if (track.getStartDate() == null || track.getEndDate() == null
            || candidate.getStartDate() == null || candidate.getEndDate() == null) {
            return false;
        }

        Instant trackStart = track.getStartDate().toInstant();
        Instant trackEnd = track.getEndDate().toInstant();
        Instant candidateStart = candidate.getStartDate().toInstant();
        Instant candidateEnd = candidate.getEndDate().toInstant();

        Duration gap;
        if (!trackEnd.isAfter(candidateStart)) {
            gap = Duration.between(trackEnd, candidateStart);
        } else if (!candidateEnd.isAfter(trackStart)) {
            gap = Duration.between(candidateEnd, trackStart);
        } else {
            return false;
        }

        return !gap.isNegative() && gap.compareTo(CONTINUATION_GAP_TOLERANCE) <= 0;
    }

    private void markUnique(GpsTrack track) {
        track.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
        track.setDuplicateOf(null);
        gpsTrackRepository.save(track);
    }


}
