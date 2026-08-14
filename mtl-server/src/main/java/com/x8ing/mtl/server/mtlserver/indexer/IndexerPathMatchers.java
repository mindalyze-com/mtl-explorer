package com.x8ing.mtl.server.mtlserver.indexer;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public final class IndexerPathMatchers {

    private static final List<PathMatcher> STANDARD_EXCLUSIONS = List.of(
            regex(".*\\.DS_Store$"),
            regex(".*@eaDir(/.*)?"),
            regex(".*/\\.[^/]+$"));

    private static final List<PathMatcher> MEDIA_EXCLUSIONS = withAdditionalExclusions(
            regex("(?i).*\\.git(/.*)?"),
            regex("(?i).*\\.svn(/.*)?"));

    private IndexerPathMatchers() {
    }

    public static List<PathMatcher> standardExclusions() {
        return STANDARD_EXCLUSIONS;
    }

    public static List<PathMatcher> mediaExclusions() {
        return MEDIA_EXCLUSIONS;
    }

    private static List<PathMatcher> withAdditionalExclusions(PathMatcher... additionalExclusions) {
        var exclusions = new ArrayList<>(List.of(additionalExclusions));
        exclusions.addAll(STANDARD_EXCLUSIONS);
        return List.copyOf(exclusions);
    }

    private static PathMatcher regex(String expression) {
        return FileSystems.getDefault().getPathMatcher("regex:" + expression);
    }
}
