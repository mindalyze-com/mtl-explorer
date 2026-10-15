package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxTimestampParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class GpxTimestampParserTest {
    @ParameterizedTest
    @CsvSource({
            "2026-01-01T10:00:00Z, 2026-01-01T10:00:00Z, false",
            "2026-01-01T10:00:00+02:00, 2026-01-01T08:00:00Z, false",
            "2026-01-01T10:00:00-05:30, 2026-01-01T15:30:00Z, false",
            "2026-01-01T10:00:00+14:00, 2025-12-31T20:00:00Z, false",
            "2026-01-01T10:00:00-14:00, 2026-01-02T00:00:00Z, false",
            "2026-01-01T10:00:00.12Z, 2026-01-01T10:00:00.120Z, false",
            "2026-01-01T10:00:00.123456789Z, 2026-01-01T10:00:00.123456789Z, false",
            "2026-01-01T10:00:00, 2026-01-01T10:00:00Z, true",
            "2026-01-01T10:00:00.00, 2026-01-01T10:00:00Z, true",
            "2026-01-01T10:00:00.12, 2026-01-01T10:00:00.120Z, true",
            "2026-01-01t10:00:00z, 2026-01-01T10:00:00Z, false",
            "2024-02-29T10:00:00Z, 2024-02-29T10:00:00Z, false",
            "2026-01-01T24:00:00Z, 2026-01-02T00:00:00Z, false",
            "2026-01-01T24:00:00.000, 2026-01-02T00:00:00Z, true",
            "2026-01-01T24:00:00+02:00, 2026-01-01T22:00:00Z, false"
    })
    void validTimesHaveOneStrictInterpretation(String source, String expected, boolean assumedUtc) {
        var parsed = GpxTimestampParser.parse(source);
        assertEquals(Instant.parse(expected), parsed.timestamp());
        assertEquals(assumedUtc, parsed.assumedUtc());
        assertEquals(parsed, GpxTimestampParser.parse(" \n" + source + "\t "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-02-30T10:00:00Z", "2026-02-29T10:00:00", "2026-13-01T10:00:00Z",
            "2026-01-01T25:00:00Z", "2026-01-01T10:61:00Z", "2026-01-01T10:00:61Z",
            "2026-01-01T24:00:01Z", "2026-01-01T24:00:00.001Z", "2016-12-31T23:59:60Z",
            "2026-01-01T10:00:00+0200", "2026-01-01T10:00:00+02", "2026-01-01T10:00:00+02:00:30",
            "2026-01-01T10:00:00+14:01", "2026-01-01T10:00:00-15:00",
            "2026-01-01T10:00Z", "2026-01-01 10:00:00Z", "2026-01-01T10:00:00.123456789123Z",
            "0000-01-01T10:00:00Z", "+10000-01-01T10:00:00Z", "9999-12-31T24:00:00Z", "not-a-date"
    })
    void invalidTimesCannotBeRolledIntoAnotherDate(String source) {
        assertThrows(DateTimeParseException.class, () -> GpxTimestampParser.parse(source));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void absentTimesRemainAbsent(String source) {
        var parsed = GpxTimestampParser.parse(source);
        assertNull(parsed.timestamp());
        assertFalse(parsed.assumedUtc());
    }

    @Test
    void missingOffsetsNeverUseTheServerTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String timezone : new String[]{"UTC", "Europe/Zurich", "Pacific/Honolulu"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(timezone));
                assertEquals(Instant.parse("2026-07-01T10:00:00Z"),
                        GpxTimestampParser.parse("2026-07-01T10:00:00.00").timestamp());
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
