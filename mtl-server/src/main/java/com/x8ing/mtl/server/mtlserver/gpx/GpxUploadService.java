package com.x8ing.mtl.server.mtlserver.gpx;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@Slf4j
public class GpxUploadService {

    static final String UPLOAD_SUBDIR = "GPX-UPLOAD";

    public record GpxUploadStatus(boolean available, String message) {
    }

    public record GpxUploadResult(boolean success, String message, String fileName) {
    }

    @Value("${mtl.gpx-watch-directory}")
    private String gpxWatchDirectory;

    private Path uploadDir;
    private volatile GpxUploadStatus status = new GpxUploadStatus(false, "Not yet initialised");

    @PostConstruct
    void init() {
        uploadDir = Paths.get(gpxWatchDirectory).resolve(UPLOAD_SUBDIR);
        status = probe();
    }

    private GpxUploadStatus probe() {
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("Created GPX upload directory: {}", uploadDir);
            }
            if (!Files.isDirectory(uploadDir)) {
                return new GpxUploadStatus(false,
                        "Path '" + uploadDir + "' exists but is not a directory.");
            }
            // Verify write access by creating and immediately deleting a probe file
            Path probe = uploadDir.resolve(".write-probe");
            Files.deleteIfExists(probe);
            Files.createFile(probe);
            Files.delete(probe);
            return new GpxUploadStatus(true, "Upload directory is available.");
        } catch (IOException e) {
            log.warn("GPX upload directory is not available: {}", e.getMessage());
            return new GpxUploadStatus(false,
                    "Upload directory '" + uploadDir + "' is not writable. " +
                    "Make sure the GPX folder is mounted with write access.");
        }
    }

    public GpxUploadStatus getStatus() {
        return status;
    }

    /**
     * Saves the uploaded GPX file to the upload directory.
     * The filename is sanitised to prevent path traversal.
     * If a file with the same name already exists, a numeric suffix is appended.
     *
     * @param file the uploaded file
     * @return the actual filename used on disk
     * @throws IllegalArgumentException if the file is not a .gpx file
     * @throws IllegalStateException    if the upload directory is not available
     * @throws IOException              on disk write failure
     */
    public String saveFile(MultipartFile file) throws IOException {
        if (!status.available()) {
            throw new IllegalStateException("Upload directory is not available");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || SupportedTrackFormat.fromPath(Path.of(originalName)) == null) {
            throw new IllegalArgumentException("Unsupported file format. Accepted: " +
                    java.util.Arrays.stream(SupportedTrackFormat.values())
                            .map(f -> "." + f.getExtension())
                            .collect(java.util.stream.Collectors.joining(", ")));
        }

        // Strip any path components the client may have smuggled in
        String safeName = Paths.get(originalName).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (safeName.isBlank() || safeName.startsWith("_.")) {
            safeName = "upload" + originalName.substring(originalName.lastIndexOf('.'));
        }

        // Resolve collision: prefix with _1_, _2_, … so the original name stays readable
        Path target = uploadDir.resolve(safeName);
        if (Files.exists(target)) {
            int counter = 1;
            do {
                target = uploadDir.resolve("_" + counter + "_" + safeName);
                counter++;
            } while (Files.exists(target));
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Track file uploaded to: {}", target);
        return target.getFileName().toString();
    }
}
