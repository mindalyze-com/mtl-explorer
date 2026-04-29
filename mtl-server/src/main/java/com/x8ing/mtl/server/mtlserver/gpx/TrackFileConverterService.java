package com.x8ing.mtl.server.mtlserver.gpx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

        String[] command = {
                GPSBABEL_CMD,
                "-i", format.getGpsBabelFormat(),
                "-f", inputFile.toAbsolutePath().toString(),
                "-o", "gpx,gpxver=1.1",  // request GPX 1.1 output with full precision
                "-F", "-"                 // write GPX to stdout
        };

        log.info("Converting {} ({}) to GPX: {}", inputFile.getFileName(), format, String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command)
                .redirectErrorStream(false);

        Process process = pb.start();

        // Read stdout (GPX XML) and stderr (error messages) concurrently to avoid deadlock
        String gpxXml;
        String stderr;
        try (InputStream stdoutStream = process.getInputStream();
             InputStream stderrStream = process.getErrorStream()) {
            gpxXml = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("GPSBabel conversion interrupted for " + inputFile, e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("GPSBabel conversion timed out after " + TIMEOUT_SECONDS + "s for " + inputFile);
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
