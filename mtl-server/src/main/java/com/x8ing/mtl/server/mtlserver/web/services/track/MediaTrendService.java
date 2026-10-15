package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPointDTO;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaTrendQueryRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaPageResult;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class MediaTrendService {

    public static final int DEFAULT_PAGE_SIZE = 60;
    public static final int MAX_PAGE_SIZE = 100;

    private final MediaTrendQueryRepository queryRepository;

    public MediaTrendService(MediaTrendQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public MediaTrendResponseDto getTrends(MediaTrendRequest request) {
        if (request == null) throw badRequest("Request body is required");
        MediaTrendGrouping grouping = required(request.grouping(), "grouping");
        MediaTrendScope scope = required(request.scope(), "scope");
        List<Long> trackIds = normalizedTrackIds(request.trackIds());
        return new MediaTrendResponseDto(scope, queryRepository.findTrendBuckets(grouping, scope, trackIds));
    }

    public MediaTrendItemPageDto getItems(MediaTrendItemsRequest request) {
        if (request == null) throw badRequest("Request body is required");
        MediaTrendGrouping grouping = required(request.grouping(), "grouping");
        MediaTrendScope scope = required(request.scope(), "scope");
        String bucketKey = request.bucketKey();
        if (bucketKey == null || bucketKey.isBlank() || !grouping.isValidBucketKey(bucketKey)) {
            throw badRequest("bucketKey is invalid for the requested grouping");
        }
        if (MediaTrendGrouping.UNDATED_BUCKET_KEY.equals(bucketKey)) {
            throw badRequest("Media without a capture time is excluded");
        }

        MediaTrendKindFilter kind = request.kind() == null ? MediaTrendKindFilter.ALL : request.kind();
        List<Long> trackIds = normalizedTrackIds(request.trackIds());
        int page = request.page() == null ? 0 : request.page();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        if (page < 0) throw badRequest("page must not be negative");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }

        long offset = Math.multiplyExact((long) page, pageSize);
        TrackMediaPageResult<MediaTrendItemDto> result = queryRepository.findItems(
                grouping, scope, bucketKey, kind, trackIds, pageSize, offset);
        int totalPages = result.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) result.totalElements() / pageSize);
        return new MediaTrendItemPageDto(
                result.items(), page, pageSize, result.totalElements(), totalPages);
    }

    public List<MediaPointDTO> getInBounds(MediaBoundsRequest request) {
        if (request == null) throw badRequest("Request body is required");
        required(request.scope(), "scope");
        if (!Double.isFinite(request.minLat()) || !Double.isFinite(request.maxLat())
            || !Double.isFinite(request.minLng()) || !Double.isFinite(request.maxLng())
            || request.minLat() > request.maxLat() || request.minLng() > request.maxLng()
            || request.minLat() < -90 || request.maxLat() > 90) {
            throw badRequest("Map bounds are invalid");
        }
        return queryRepository.findInBounds(request, normalizedTrackIds(request.trackIds()));
    }

    private static List<Long> normalizedTrackIds(List<Long> trackIds) {
        if (trackIds == null) throw badRequest("trackIds is required for media filtering");
        if (trackIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("trackIds must contain only positive ids");
        }
        return List.copyOf(new LinkedHashSet<>(trackIds));
    }

    private static <T> T required(T value, String name) {
        if (value == null) throw badRequest(name + " is required");
        return value;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
