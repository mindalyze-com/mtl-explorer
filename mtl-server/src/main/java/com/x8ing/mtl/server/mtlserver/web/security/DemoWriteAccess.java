package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Blocks imports and source review changes in shared demos; other editing remains available. */
@Component
@RequiredArgsConstructor
public class DemoWriteAccess {
    public static final String READ_ONLY_MESSAGE = "Import and review changes are unavailable in this shared demo.";

    private final MtlAppProperties appProperties;

    public boolean blocksImportAndReview() {
        return appProperties.isDemoMode();
    }

    public void requireImportAndReviewAccess() {
        if (blocksImportAndReview()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, READ_ONLY_MESSAGE);
        }
    }
}
