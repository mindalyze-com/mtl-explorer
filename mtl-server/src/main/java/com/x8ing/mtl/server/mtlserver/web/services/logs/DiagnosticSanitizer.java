package com.x8ing.mtl.server.mtlserver.web.services.logs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Only small, explicitly safe diagnostic context is retained.
 */
public final class DiagnosticSanitizer {
    private static final String REDACTED = "<redacted>";
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@?#]+@");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)\\b(authorization|cookie|password|passwd|token|secret|jwt|lat|lng|lon|longitude|latitude|coord|geometry|filename|trackId|mediaId|health)[\"']?\\s*[=:]\\s*(?:\"[^\"]*\"|'[^']*'|[^&\\s,;}]+)");
    private static final Pattern QUERY = Pattern.compile("((?:https?://|\\.{0,2}/)[^\\s?]+)\\?[^\\s)\"']*?(?=:\\d+(?::\\d+)?(?:[)\\s\"']|$)|[)\\s\"']|$)");
    private static final Set<String> CONTEXT_KEYS = Set.of("errorType", "code", "status", "statusText", "method",
            "routeName", "vueInfo", "source", "line", "column", "mode", "packageVersion", "component", "operation");

    private DiagnosticSanitizer() {
    }

    public static String text(String value) {
        if (value == null) return null;
        String clean = BEARER.matcher(value).replaceAll("Bearer " + REDACTED);
        clean = JWT.matcher(clean).replaceAll(REDACTED);
        clean = URL_CREDENTIALS.matcher(clean).replaceAll("$1" + REDACTED + "@");
        clean = QUERY.matcher(clean).replaceAll("$1?" + REDACTED);
        return ASSIGNMENT.matcher(clean).replaceAll("$1=" + REDACTED);
    }

    public static Map<String, Object> context(Map<String, Object> value) {
        if (value == null) return null;
        Map<String, Object> clean = new LinkedHashMap<>();
        value.forEach((key, entry) -> {
            if (!CONTEXT_KEYS.contains(key)) return;
            if (entry instanceof String string) clean.put(key, text(string));
            else if (entry == null || entry instanceof Number || entry instanceof Boolean) clean.put(key, entry);
        });
        return clean;
    }
}
