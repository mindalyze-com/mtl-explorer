package com.x8ing.mtl.server.mtlserver.web.services.logs;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fingerprints the complete captured stack, excluding its exception header.
 */
final class StackFingerprint {
    private static final Pattern FRAME_START = Pattern.compile("^(?:at\\s+\\S.*|[^:@]*@.*)$");

    private StackFingerprint() {
    }

    static String of(String sanitizedStack) {
        if (sanitizedStack == null) return null;
        String frames = sanitizedStack.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .dropWhile(line -> !FRAME_START.matcher(line).matches())
                .collect(Collectors.joining("\n"));
        return frames.isEmpty() ? null : DigestUtils.sha256Hex(frames);
    }
}
