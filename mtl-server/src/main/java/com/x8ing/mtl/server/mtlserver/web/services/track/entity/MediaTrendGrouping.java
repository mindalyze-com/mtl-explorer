package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum MediaTrendGrouping {
    YEAR("year", "YYYY"),
    QUARTER("quarter", "YYYY-\"Q\"Q"),
    MONTH("month", "YYYY-MM"),
    WEEK("week", "IYYY-\"W\"IW"),
    DAY("day", "YYYY-MM-DD"),
    TOTAL(null, null);

    public static final String TOTAL_BUCKET_KEY = "TOTAL";
    public static final String UNDATED_BUCKET_KEY = "UNDATED";
    private static final Pattern QUARTER_KEY = Pattern.compile("(\\d{4})-Q([1-4])");
    private static final Pattern WEEK_KEY = Pattern.compile("(\\d{4})-W(\\d{2})");

    private final String dateTruncUnit;
    private final String labelFormat;

    MediaTrendGrouping(String dateTruncUnit, String labelFormat) {
        this.dateTruncUnit = dateTruncUnit;
        this.labelFormat = labelFormat;
    }

    public String dateTruncUnit() {
        return dateTruncUnit;
    }

    public String labelFormat() {
        return labelFormat;
    }

    public boolean isValidBucketKey(String bucketKey) {
        if (UNDATED_BUCKET_KEY.equals(bucketKey)) return true;
        if (this == TOTAL) return TOTAL_BUCKET_KEY.equals(bucketKey);
        try {
            return switch (this) {
                case YEAR -> parseYear(bucketKey);
                case QUARTER -> parseQuarter(bucketKey);
                case MONTH -> parseMonth(bucketKey);
                case WEEK -> parseWeek(bucketKey);
                case DAY -> parseDay(bucketKey);
                case TOTAL -> false;
            };
        } catch (DateTimeParseException | NumberFormatException exception) {
            return false;
        }
    }

    private static boolean parseYear(String value) {
        Year.parse(value);
        return true;
    }

    private static boolean parseQuarter(String value) {
        Matcher matcher = QUARTER_KEY.matcher(value);
        if (!matcher.matches()) return false;
        Year.of(Integer.parseInt(matcher.group(1)));
        return true;
    }

    private static boolean parseMonth(String value) {
        YearMonth.parse(value);
        return true;
    }

    private static boolean parseWeek(String value) {
        Matcher matcher = WEEK_KEY.matcher(value);
        if (!matcher.matches()) return false;
        LocalDate.parse(value + "-1", DateTimeFormatter.ISO_WEEK_DATE);
        return true;
    }

    private static boolean parseDay(String value) {
        LocalDate.parse(value);
        return true;
    }
}
