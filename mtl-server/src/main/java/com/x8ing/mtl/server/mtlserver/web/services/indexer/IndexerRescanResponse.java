package com.x8ing.mtl.server.mtlserver.web.services.indexer;

public record IndexerRescanResponse(
        String index,
        IndexerRescanStatus status,
        String message
) {
}
