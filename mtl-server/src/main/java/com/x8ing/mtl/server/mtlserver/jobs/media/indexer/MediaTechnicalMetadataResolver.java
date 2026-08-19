package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.drew.metadata.heif.HeifDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.media.QuickTimeSoundDirectory;
import com.drew.metadata.mov.media.QuickTimeVideoDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import com.drew.metadata.photoshop.PsdHeaderDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;

/** Resolves display-oriented photo and video metadata from supported media containers. */
final class MediaTechnicalMetadataResolver {

    private static final int GPS_ALTITUDE_BELOW_SEA_LEVEL = 1;
    private static final int DURATION_PART_COUNT = 3;
    private static final double SECONDS_PER_MINUTE = 60.0;

    private MediaTechnicalMetadataResolver() {
    }

    static TechnicalMetadata resolve(Metadata metadata) {
        Dimensions dimensions = resolveVideoDimensions(metadata);
        if (dimensions == null) dimensions = resolveImageDimensions(metadata);

        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);

        return new TechnicalMetadata(
                dimensions == null ? null : dimensions.width(),
                dimensions == null ? null : dimensions.height(),
                positiveDouble(exif, ExifSubIFDDirectory.TAG_FNUMBER),
                positiveDouble(exif, ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                positiveInteger(exif, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                positiveDouble(exif, ExifSubIFDDirectory.TAG_FOCAL_LENGTH),
                positiveInteger(exif, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH),
                trimmedString(exif, ExifSubIFDDirectory.TAG_LENS_MODEL),
                resolveGpsAltitude(gps),
                resolveDurationSeconds(metadata),
                resolveFrameRate(metadata),
                resolveVideoCodec(metadata),
                resolveAudioCodec(metadata));
    }

    private static Dimensions resolveVideoDimensions(Metadata metadata) {
        Dimensions dimensions = dimensions(
                metadata.getFirstDirectoryOfType(Mp4VideoDirectory.class),
                Mp4VideoDirectory.TAG_WIDTH,
                Mp4VideoDirectory.TAG_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(QuickTimeVideoDirectory.class),
                QuickTimeVideoDirectory.TAG_WIDTH,
                QuickTimeVideoDirectory.TAG_HEIGHT);
        if (dimensions != null) return dimensions;

        return dimensions(
                metadata.getFirstDirectoryOfType(AviDirectory.class),
                AviDirectory.TAG_WIDTH,
                AviDirectory.TAG_HEIGHT);
    }

    private static Dimensions resolveImageDimensions(Metadata metadata) {
        Dimensions dimensions = dimensions(
                metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class),
                ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH,
                ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(ExifIFD0Directory.class),
                ExifIFD0Directory.TAG_IMAGE_WIDTH,
                ExifIFD0Directory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(JpegDirectory.class),
                JpegDirectory.TAG_IMAGE_WIDTH,
                JpegDirectory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(HeifDirectory.class),
                HeifDirectory.TAG_IMAGE_WIDTH,
                HeifDirectory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(WebpDirectory.class),
                WebpDirectory.TAG_IMAGE_WIDTH,
                WebpDirectory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        for (PngDirectory directory : metadata.getDirectoriesOfType(PngDirectory.class)) {
            dimensions = dimensions(directory, PngDirectory.TAG_IMAGE_WIDTH, PngDirectory.TAG_IMAGE_HEIGHT);
            if (dimensions != null) return dimensions;
        }

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(GifHeaderDirectory.class),
                GifHeaderDirectory.TAG_IMAGE_WIDTH,
                GifHeaderDirectory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        dimensions = dimensions(
                metadata.getFirstDirectoryOfType(BmpHeaderDirectory.class),
                BmpHeaderDirectory.TAG_IMAGE_WIDTH,
                BmpHeaderDirectory.TAG_IMAGE_HEIGHT);
        if (dimensions != null) return dimensions;

        return dimensions(
                metadata.getFirstDirectoryOfType(PsdHeaderDirectory.class),
                PsdHeaderDirectory.TAG_IMAGE_WIDTH,
                PsdHeaderDirectory.TAG_IMAGE_HEIGHT);
    }

    private static Double resolveDurationSeconds(Metadata metadata) {
        Double duration = positiveDouble(
                metadata.getFirstDirectoryOfType(Mp4Directory.class),
                Mp4Directory.TAG_DURATION_SECONDS);
        if (duration != null) return duration;

        duration = positiveDouble(
                metadata.getFirstDirectoryOfType(QuickTimeDirectory.class),
                QuickTimeDirectory.TAG_DURATION_SECONDS);
        if (duration != null) return duration;

        AviDirectory avi = metadata.getFirstDirectoryOfType(AviDirectory.class);
        return parseClockDuration(avi == null ? null : avi.getString(AviDirectory.TAG_DURATION));
    }

    private static Double resolveFrameRate(Metadata metadata) {
        Double frameRate = positiveDouble(
                metadata.getFirstDirectoryOfType(Mp4VideoDirectory.class),
                Mp4VideoDirectory.TAG_FRAME_RATE);
        if (frameRate != null) return frameRate;

        frameRate = positiveDouble(
                metadata.getFirstDirectoryOfType(QuickTimeVideoDirectory.class),
                QuickTimeVideoDirectory.TAG_FRAME_RATE);
        if (frameRate != null) return frameRate;

        return positiveDouble(
                metadata.getFirstDirectoryOfType(AviDirectory.class),
                AviDirectory.TAG_FRAMES_PER_SECOND);
    }

    private static String resolveVideoCodec(Metadata metadata) {
        String codec = trimmedString(
                metadata.getFirstDirectoryOfType(Mp4VideoDirectory.class),
                Mp4VideoDirectory.TAG_COMPRESSION_TYPE);
        if (codec != null) return codec;

        codec = trimmedString(
                metadata.getFirstDirectoryOfType(QuickTimeVideoDirectory.class),
                QuickTimeVideoDirectory.TAG_COMPRESSION_TYPE);
        if (codec != null) return codec;

        return trimmedString(
                metadata.getFirstDirectoryOfType(AviDirectory.class),
                AviDirectory.TAG_VIDEO_CODEC);
    }

    private static String resolveAudioCodec(Metadata metadata) {
        String codec = trimmedString(
                metadata.getFirstDirectoryOfType(Mp4SoundDirectory.class),
                Mp4SoundDirectory.TAG_AUDIO_FORMAT);
        if (codec != null) return codec;

        codec = trimmedString(
                metadata.getFirstDirectoryOfType(QuickTimeSoundDirectory.class),
                QuickTimeSoundDirectory.TAG_AUDIO_FORMAT);
        if (codec != null) return codec;

        return trimmedString(
                metadata.getFirstDirectoryOfType(AviDirectory.class),
                AviDirectory.TAG_AUDIO_CODEC);
    }

    private static Double resolveGpsAltitude(GpsDirectory directory) {
        Double altitude = positiveDouble(directory, GpsDirectory.TAG_ALTITUDE);
        if (altitude == null) return null;
        Integer altitudeReference = directory.getInteger(GpsDirectory.TAG_ALTITUDE_REF);
        return altitudeReference != null && altitudeReference == GPS_ALTITUDE_BELOW_SEA_LEVEL ? -altitude : altitude;
    }

    private static Dimensions dimensions(Directory directory, int widthTag, int heightTag) {
        Integer width = positiveInteger(directory, widthTag);
        Integer height = positiveInteger(directory, heightTag);
        return width == null || height == null ? null : new Dimensions(width, height);
    }

    private static Integer positiveInteger(Directory directory, int tag) {
        if (directory == null) return null;
        Integer value = directory.getInteger(tag);
        return value != null && value > 0 ? value : null;
    }

    private static Double positiveDouble(Directory directory, int tag) {
        if (directory == null) return null;
        Double value = directory.getDoubleObject(tag);
        return value != null && Double.isFinite(value) && value > 0.0 ? value : null;
    }

    private static String trimmedString(Directory directory, int tag) {
        if (directory == null) return null;
        String value = directory.getString(tag);
        if (value == null) return null;
        value = value.replace("\0", "").trim();
        return value.isEmpty() ? null : value;
    }

    private static Double parseClockDuration(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.trim().split(":");
        if (parts.length != DURATION_PART_COUNT) return null;
        try {
            double duration = Double.parseDouble(parts[0]) * SECONDS_PER_MINUTE * SECONDS_PER_MINUTE
                              + Double.parseDouble(parts[1]) * SECONDS_PER_MINUTE
                              + Double.parseDouble(parts[2]);
            return Double.isFinite(duration) && duration > 0.0 ? duration : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record TechnicalMetadata(
            Integer widthPixels,
            Integer heightPixels,
            Double apertureFNumber,
            Double exposureTimeSeconds,
            Integer isoSpeed,
            Double focalLengthMm,
            Integer focalLength35Mm,
            String lensModel,
            Double gpsAltitudeMeters,
            Double durationSeconds,
            Double frameRate,
            String videoCodec,
            String audioCodec) {
    }

    private record Dimensions(int width, int height) {
    }
}
