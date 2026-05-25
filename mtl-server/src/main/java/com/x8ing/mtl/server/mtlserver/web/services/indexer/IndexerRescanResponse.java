package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "index",
        "status",
        "message"
})
public record IndexerRescanResponse(
        String index,
        IndexerRescanStatus status,
        String message
) {
}
