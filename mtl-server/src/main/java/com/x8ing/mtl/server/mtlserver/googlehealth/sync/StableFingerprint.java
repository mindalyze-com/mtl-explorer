package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

public final class StableFingerprint {

    public static String of(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String ofCanonicalJson(JsonNode node) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(node, canonical);
        return of(canonical.toString());
    }

    private static void appendCanonical(JsonNode node, StringBuilder target) {
        if (node == null || node.isNull()) {
            target.append("null");
        } else if (node.isObject()) {
            target.append('{');
            boolean[] first = {true};
            node.properties().stream()
                    .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                    .forEach(entry -> {
                        if (!first[0]) target.append(',');
                        first[0] = false;
                        target.append(JsonEscaper.quote(entry.getKey())).append(':');
                        appendCanonical(entry.getValue(), target);
                    });
            target.append('}');
        } else if (node.isArray()) {
            target.append('[');
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) target.append(',');
                appendCanonical(node.get(index), target);
            }
            target.append(']');
        } else {
            target.append(node.toString());
        }
    }

    private static final class JsonEscaper {
        private static String quote(String value) {
            StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\b' -> escaped.append("\\b");
                    case '\f' -> escaped.append("\\f");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (character < 0x20) escaped.append("\\u%04x".formatted((int) character));
                        else escaped.append(character);
                    }
                }
            }
            return escaped.append('"').toString();
        }
    }

    private StableFingerprint() {
    }
}
