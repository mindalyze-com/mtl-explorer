package com.x8ing.mtl.server.mtlserver.gpx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Converts non-GPX track files to GPX XML using GPSBabel.
 * <p>
 * The original file is read directly from disk by GPSBabel (seekable I/O for binary formats like FIT).
 * The GPX output is piped to stdout ({@code -F -}) and captured in-memory — no temp files are created
 * and the original file is never modified.
 */
@Service
@Slf4j
public class TrackFileConverterService {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String GPSBABEL_CMD = "gpsbabel";
    private static final String KMZ_KML_ENTRY_NAME = "track.kml";
    private static final String KMZ_PREFERRED_KML_ENTRY_NAME = "doc.kml";

    /**
     * Converts a track file to GPX XML.
     *
     * @param inputFile the path to the source file (must exist on disk)
     * @param format    the {@link SupportedTrackFormat} describing the input format
     * @return the GPX XML string (BOM-stripped, stylesheet-stripped, ready for jpx parsing)
     * @throws IOException           if GPSBabel cannot be started or the process fails
     * @throws IllegalStateException if GPSBabel exits with a non-zero code
     */
    public String convertToGpx(Path inputFile, SupportedTrackFormat format) throws IOException {
        if (!format.needsConversion()) {
            throw new IllegalArgumentException("Format " + format + " is native GPX, no conversion needed");
        }

        Path tempDir = null;
        Path conversionInputFile = inputFile;
        SupportedTrackFormat conversionFormat = format;
        try {
            if (format == SupportedTrackFormat.KMZ) {
                tempDir = Files.createTempDirectory("mtl-kmz-");
                conversionInputFile = extractKmlFromKmz(inputFile, tempDir);
                conversionFormat = SupportedTrackFormat.KML;
            }

            List<String> command = buildGpsBabelCommand(conversionInputFile, conversionFormat);

            log.info("Converting {} ({}) to GPX: {}", inputFile.getFileName(), format, String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectErrorStream(false);

            Process process = pb.start();

            // Read stdout (GPX XML) and stderr (error messages) concurrently to avoid deadlock
            CompletableFuture<String> stdoutFuture = readProcessStream(process.getInputStream());
            CompletableFuture<String> stderrFuture = readProcessStream(process.getErrorStream());

            boolean finished;
            try {
                finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                throw new IOException("GPSBabel conversion interrupted for " + inputFile, e);
            }

            if (!finished) {
                process.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                throw new IOException("GPSBabel conversion timed out after " + TIMEOUT_SECONDS + "s for " + inputFile);
            }

            String gpxXml;
            String stderr;
            try {
                gpxXml = stdoutFuture.join();
                stderr = stderrFuture.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IOException("Could not read GPSBabel output for " + inputFile, cause);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("GPSBabel failed (exit {}) for {}: {}", exitCode, inputFile, stderr);
                throw new IOException("GPSBabel conversion failed (exit " + exitCode + ") for " + inputFile + ": " + stderr.trim());
            }

            if (gpxXml.isBlank()) {
                throw new IOException("GPSBabel produced empty output for " + inputFile);
            }

            log.info("GPSBabel converted {} → {} chars of GPX XML", inputFile.getFileName(), gpxXml.length());

            return cleanGpxXml(gpxXml);
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private static CompletableFuture<String> readProcessStream(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (inputStream) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    static List<String> buildGpsBabelCommand(Path inputFile, SupportedTrackFormat format) {
        List<String> command = new ArrayList<>(List.of(
                GPSBABEL_CMD,
                "-i", format.getGpsBabelFormat(),
                "-f", inputFile.toAbsolutePath().toString()));

        if (format == SupportedTrackFormat.GEOJSON) {
            command.addAll(List.of("-x", "transform,trk=rte,del"));
        }

        command.addAll(List.of(
                "-o", "gpx,gpxver=1.1",
                "-F", "-"));
        return command;
    }

    static Path extractKmlFromKmz(Path kmzFile, Path tempDir) throws IOException {
        Path extractedKml = tempDir.resolve(KMZ_KML_ENTRY_NAME);
        boolean foundFallbackKml = false;
        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(kmzFile))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory() || !isKmlEntry(entryName)) {
                    continue;
                }
                if (isPreferredKmzKmlEntry(entryName)) {
                    Files.copy(zipInput, extractedKml, StandardCopyOption.REPLACE_EXISTING);
                    return extractedKml;
                }
                if (!foundFallbackKml) {
                    Files.copy(zipInput, extractedKml, StandardCopyOption.REPLACE_EXISTING);
                    foundFallbackKml = true;
                }
            }
        }
        if (foundFallbackKml) return extractedKml;
        throw new IOException("KMZ file does not contain a KML document: " + kmzFile);
    }

    private static boolean isKmlEntry(String entryName) {
        return entryName != null && entryName.toLowerCase(Locale.ROOT).endsWith(".kml");
    }

    private static boolean isPreferredKmzKmlEntry(String entryName) {
        String normalizedName = entryName.replace('\\', '/');
        return normalizedName.equalsIgnoreCase(KMZ_PREFERRED_KML_ENTRY_NAME)
               || normalizedName.toLowerCase(Locale.ROOT).endsWith("/" + KMZ_PREFERRED_KML_ENTRY_NAME);
    }

    private static void deleteTempDir(Path tempDir) {
        if (tempDir == null) return;
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Could not delete temporary conversion file {}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean temporary conversion directory {}", tempDir, e);
        }
    }

    /**
     * Applies the same XML cleaning that GPXReader.readFileContentAndClean() does:
     * strip BOM and xml-stylesheet processing instructions.
     */
    private static String cleanGpxXml(String xml) {
        if (xml.startsWith("\uFEFF")) {
            xml = xml.substring(1);
        }
        return xml.replaceAll("(?s)<\\?xml-stylesheet.*?\\?>", "");
    }
}
