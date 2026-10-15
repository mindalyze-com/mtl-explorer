package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Semantic release precedence; Git revisions and image tags have no release precedence.
 */
final class ReleaseVersion {
    private static final Pattern VERSION = Pattern.compile(
            "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    private ReleaseVersion() {
    }

    static boolean valid(String version) {
        if (version == null) return false;
        var match = VERSION.matcher(version);
        if (!match.matches()) return false;
        if (match.group(4) != null) {
            for (String part : match.group(4).split("\\.")) {
                if (part.matches("\\d+") && part.length() > 1 && part.startsWith("0")) return false;
            }
        }
        return true;
    }

    static int compare(String first, String second) {
        if (!valid(first) || !valid(second)) throw new IllegalArgumentException("Invalid release version");
        var a = VERSION.matcher(first);
        var b = VERSION.matcher(second);
        a.matches();
        b.matches();
        for (int i = 1; i <= 3; i++) {
            int compared = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (compared != 0) return compared;
        }
        if (a.group(4) == null) return b.group(4) == null ? 0 : 1;
        if (b.group(4) == null) return -1;
        String[] ap = a.group(4).split("\\.");
        String[] bp = b.group(4).split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            boolean an = ap[i].matches("\\d+");
            boolean bn = bp[i].matches("\\d+");
            int compared = an && bn ? new BigInteger(ap[i]).compareTo(new BigInteger(bp[i]))
                    : an != bn ? (an ? -1 : 1) : ap[i].compareTo(bp[i]);
            if (compared != 0) return compared;
        }
        return Integer.compare(ap.length, bp.length);
    }
}
