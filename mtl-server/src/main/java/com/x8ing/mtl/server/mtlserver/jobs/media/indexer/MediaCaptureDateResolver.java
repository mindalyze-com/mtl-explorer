package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves the best embedded capture date across image and video metadata formats. */
final class MediaCaptureDateResolver {

    private static final long QUICK_TIME_EPOCH_MILLIS = Instant.parse("1904-01-01T00:00:00Z").toEpochMilli();
    private static final List<String> XMP_CAPTURE_DATE_PROPERTIES = List.of(
            "datetimeoriginal",
            "datecreated",
            "createdate"
    );
    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("EEE MMM d HH:mm:ss yyyy")
                    .toFormatter(Locale.ENGLISH)
    );

    private MediaCaptureDateResolver() {
    }

    static Date resolve(Metadata metadata) {
        Date captureDate = resolveExifDate(metadata);
        if (captureDate != null) return captureDate;

        captureDate = firstDate(
                metadata,
                QuickTimeMetadataDirectory.class,
                QuickTimeMetadataDirectory.TAG_CREATION_DATE);
        if (captureDate != null) return captureDate;

        captureDate = resolveAviDate(metadata);
        if (captureDate != null) return captureDate;

        captureDate = resolveXmpDate(metadata);
        if (captureDate != null) return captureDate;

        captureDate = validContainerDate(firstDate(metadata, Mp4Directory.class, Mp4Directory.TAG_CREATION_TIME));
        if (captureDate != null) return captureDate;

        return validContainerDate(firstDate(
                metadata,
                QuickTimeDirectory.class,
                QuickTimeDirectory.TAG_CREATION_TIME));
    }

    private static Date resolveExifDate(Metadata metadata) {
        for (ExifSubIFDDirectory directory : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
            Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (date == null) date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME);
            if (date == null) date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
            if (date != null) return date;
        }
        return null;
    }

    private static Date resolveAviDate(Metadata metadata) {
        for (AviDirectory directory : metadata.getDirectoriesOfType(AviDirectory.class)) {
            Date date = directory.getDate(AviDirectory.TAG_DATETIME_ORIGINAL);
            if (date != null) return date;

            date = parseMetadataDate(directory.getString(AviDirectory.TAG_DATETIME_ORIGINAL));
            if (date != null) return date;
        }
        return null;
    }

    private static Date resolveXmpDate(Metadata metadata) {
        for (String propertyName : XMP_CAPTURE_DATE_PROPERTIES) {
            for (XmpDirectory directory : metadata.getDirectoriesOfType(XmpDirectory.class)) {
                for (Map.Entry<String, String> property : directory.getXmpProperties().entrySet()) {
                    if (!normalizedPropertyName(property.getKey()).endsWith(propertyName)) continue;
                    Date date = parseMetadataDate(property.getValue());
                    if (date != null) return date;
                }
            }
        }
        return null;
    }

    private static String normalizedPropertyName(String propertyName) {
        return propertyName == null ? "" : propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static <T extends Directory> Date firstDate(Metadata metadata, Class<T> type, int tagType) {
        for (T directory : metadata.getDirectoriesOfType(type)) {
            Date date = directory.getDate(tagType);
            if (date != null) return date;
        }
        return null;
    }

    private static Date validContainerDate(Date date) {
        if (date == null || date.getTime() <= QUICK_TIME_EPOCH_MILLIS) return null;
        return date;
    }

    private static Date parseMetadataDate(String rawValue) {
        if (rawValue == null) return null;
        String value = rawValue.replace("\0", "").trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return null;

        try {
            return Date.from(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            // Try the other common embedded metadata formats below.
        }

        try {
            return Date.from(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        } catch (DateTimeParseException ignored) {
            // The value may be a local camera time without an offset.
        }

        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
                return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        return null;
    }
}
