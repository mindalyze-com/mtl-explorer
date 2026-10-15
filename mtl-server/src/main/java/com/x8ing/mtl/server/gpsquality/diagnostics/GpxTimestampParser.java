package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared strict GPX clock parsing. An omitted offset follows the GPX UTC convention.
 */
public final class GpxTimestampParser {
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.UTC;
    private static final int MAX_OFFSET_SECONDS = 14 * 60 * 60;
    private static final int MIN_YEAR = 1;
    private static final int MAX_YEAR = 9999;
    private static final int HOUR_START_INDEX = 11;
    private static final int HOUR_END_INDEX = 13;
    private static final Pattern END_OF_DAY = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T24:00:00(?:\\.0{1,9})?(?:Z|[+-]\\d{2}:\\d{2})?",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.YEAR, 4).appendPattern("-MM-dd'T'HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final Parsed MISSING = new Parsed(null, false);

    private GpxTimestampParser() {
    }

    /**
     * The assumption is explicit; callers retain the original source text separately.
     */
    public record Parsed(Instant timestamp, boolean assumedUtc) {
    }

    public static Parsed parse(String source) {
        if (source == null || source.isBlank()) return MISSING;
        String value = source.trim();
        boolean endOfDay = END_OF_DAY.matcher(value).matches();
        String clock = endOfDay
                ? value.substring(0, HOUR_START_INDEX) + "00" + value.substring(HOUR_END_INDEX)
                : value;
        var parsed = FORMAT.parse(clock);
        boolean assumedUtc = !parsed.isSupported(ChronoField.OFFSET_SECONDS);
        ZoneOffset offset = assumedUtc ? DEFAULT_OFFSET : ZoneOffset.from(parsed);
        if (Math.abs(offset.getTotalSeconds()) > MAX_OFFSET_SECONDS)
            throw new DateTimeParseException("GPX timezone offset exceeds 14 hours.", value, 0);
        LocalDateTime dateTime = LocalDateTime.from(parsed);
        if (endOfDay) dateTime = dateTime.plusDays(1);
        if (dateTime.getYear() < MIN_YEAR || dateTime.getYear() > MAX_YEAR)
            throw new DateTimeParseException("GPX timestamp requires a year between 0001 and 9999.", value, 0);
        return new Parsed(dateTime.toInstant(offset), assumedUtc);
    }
}
