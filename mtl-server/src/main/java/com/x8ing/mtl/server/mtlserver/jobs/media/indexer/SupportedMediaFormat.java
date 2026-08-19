package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Single source of truth for file extensions that MTL Explorer can ingest as
 * images or videos. The video set intentionally covers common FFmpeg input
 * containers and elementary video streams, while excluding audio-only,
 * document, playlist, subtitle, and sidecar formats.
 */
public final class SupportedMediaFormat {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            // Common and web images
            "apng", "avif", "bmp", "cur", "dds", "dib", "exr", "gif", "hdr", "heic", "heif",
            "ico", "jfif", "jpe", "jpeg", "jpg", "jxl", "png", "psd", "tga", "tif", "tiff", "webp",
            // JPEG 2000 and portable bitmap families
            "j2c", "j2k", "jp2", "jpc", "jpf", "jpm", "jpx", "pbm", "pcx", "pgm", "pnm", "ppm",
            // Camera raw formats commonly encountered in photo archives
            "3fr", "arw", "bay", "cap", "cr2", "cr3", "crw", "dcr", "dng", "erf", "fff", "iiq",
            "k25", "kdc", "mdc", "mef", "mos", "mrw", "nef", "nrw", "orf", "pef", "ptx", "pxn",
            "raf", "raw", "rw2", "rwl", "rwz", "sr2", "srf", "srw", "x3f"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            // ISO base media, QuickTime, and mobile containers
            "3g2", "3gp", "3gp2", "3gpp", "f4v", "ismv", "m4v", "mj2", "mov", "mp4", "qt",
            // Matroska, WebM, Ogg, Flash, Microsoft, RealMedia, and other containers
            "amv", "asf", "avi", "divx", "dvr-ms", "flv", "mkv", "ogm", "ogv", "rm", "rmvb", "webm",
            "wmv", "wtv",
            // MPEG program/transport streams and camcorder formats
            "m1v", "m2p", "m2t", "m2ts", "m2v", "mod", "mpe", "mpeg", "mpg", "mpv", "mts", "tod",
            "trp", "ts", "vob", "vro",
            // Professional and less common containers
            "bik", "bik2", "dav", "dv", "gxf", "ifv", "ivf", "mxf", "nsv", "nut", "roq", "viv", "y4m",
            // Camera and cinema raw video
            "cine", "r3d",
            // Elementary video bitstreams understood by FFmpeg
            "264", "265", "av1", "avs", "avs2", "avs3", "cavs", "drc", "dnxhd", "dnxhr", "evc",
            "h261", "h263", "h264", "h265", "hevc", "mjpeg", "mjpg", "obu", "vc1"
    );

    private static final Set<String> METADATA_EXTRACTOR_EXTENSIONS = Set.of(
            // Preserve the formats that were read before broad filtering was introduced.
            "jpg", "jpeg", "tiff", "tif", "heic", "heif",
            "dng", "nef", "cr2", "cr3", "orf", "arw", "rw2", "rwl", "srw", "raf", "pef", "x3f",
            "psd", "png", "bmp", "gif", "ico", "pcx", "webp",
            "mp4", "mov", "m4v", "3gp", "avi"
    );

    private static final Set<String> SUPPORTED_EXTENSIONS = Stream
            .concat(IMAGE_EXTENSIONS.stream(), VIDEO_EXTENSIONS.stream())
            .collect(Collectors.toUnmodifiableSet());

    private static final String SUPPORTED_EXTENSION_ALTERNATION = alternation(SUPPORTED_EXTENSIONS);
    private static final String VIDEO_EXTENSION_ALTERNATION = alternation(VIDEO_EXTENSIONS);

    private SupportedMediaFormat() {
    }

    public static boolean isSupported(Path path) {
        return path != null && isSupportedFileName(path.getFileName().toString());
    }

    public static boolean isSupportedFileName(String fileName) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(fileName));
    }

    public static boolean isVideoFileName(String fileName) {
        return VIDEO_EXTENSIONS.contains(extensionOf(fileName));
    }

    static boolean supportsMetadataExtraction(String fileName) {
        return METADATA_EXTRACTOR_EXTENSIONS.contains(extensionOf(fileName));
    }

    /** Returns a case-insensitive Java NIO PathMatcher regex. */
    public static String inclusionRegex() {
        return ".*(?i)\\.(" + SUPPORTED_EXTENSION_ALTERNATION + ")$";
    }

    /** Returns the sorted extension alternation used by native SQL media-kind classification. */
    public static String videoExtensionSqlAlternation() {
        return VIDEO_EXTENSION_ALTERNATION;
    }

    static Set<String> imageExtensions() {
        return IMAGE_EXTENSIONS;
    }

    static Set<String> videoExtensions() {
        return VIDEO_EXTENSIONS;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int separator = fileName.lastIndexOf('.');
        if (separator < 0 || separator == fileName.length() - 1) return "";
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String alternation(Set<String> extensions) {
        return extensions.stream()
                .sorted()
                .collect(Collectors.joining("|"));
    }
}
