package com.x8ing.mtl.server.mtlserver.jobs.media.correlation;

import java.util.List;

final class MediaCorrelationBatchException extends RuntimeException {

    private final List<Long> mediaIds;

    MediaCorrelationBatchException(List<Long> mediaIds, RuntimeException cause) {
        super(cause);
        this.mediaIds = List.copyOf(mediaIds);
    }

    List<Long> mediaIds() {
        return mediaIds;
    }
}
