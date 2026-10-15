package com.x8ing.mtl.server.mtlserver.web.services.track;

import java.nio.file.Path;

/**
 * Emitted for each fallback attempt, including attempts whose conversion fails.
 */
public record MediaPreviewFallbackEvent(Path source, String fromProcessor, String toProcessor, String reason) {
}
