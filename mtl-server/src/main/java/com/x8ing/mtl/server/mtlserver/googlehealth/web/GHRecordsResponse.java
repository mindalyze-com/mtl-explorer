package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import java.util.List;

public record GHRecordsResponse(
        List<GHRecordResponse> records,
        String nextCursor
) {
}
