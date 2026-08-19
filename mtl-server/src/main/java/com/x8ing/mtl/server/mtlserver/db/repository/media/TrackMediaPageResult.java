package com.x8ing.mtl.server.mtlserver.db.repository.media;

import java.util.List;

public record TrackMediaPageResult<T>(List<T> items, long totalElements) {
}
