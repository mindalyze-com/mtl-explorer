package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ServerLogController {

    private static final List<String> LOG_CANDIDATES = List.of(
            "/app/logs/mtl-server.log",  // Docker container path
            "logs/mtl-server.log"        // Local dev (relative to working dir)
    );

    /**
     * Read buffer for tail: 512 KB from file end is well beyond 200 typical log lines.
     */
    private static final int TAIL_BUFFER_BYTES = 512 * 1024;

    /**
     * Hard cap — prevent accidentally dumping tens of thousands of lines.
     */
    private static final int MAX_LINES = 5000;

    private final MtlAppProperties mtlAppProperties;

    @GetMapping("/server-log")
    public String getServerLog(@RequestParam(defaultValue = "200") int lines) {
        if (!mtlAppProperties.isLogViewerEnabled()) {
            return "function disabled";
        }

        int count = Math.min(Math.max(lines, 1), MAX_LINES);

        Path logFile = resolveLogFile();
        if (logFile == null) {
            return "(Log file not found. Checked: " + String.join(", ", LOG_CANDIDATES) + ")";
        }

        try {
            return readLastLines(logFile, count);
        } catch (IOException e) {
            return "(Error reading log: " + e.getMessage() + ")";
        }
    }

    private Path resolveLogFile() {
        for (String candidate : LOG_CANDIDATES) {
            Path p = Path.of(candidate);
            if (Files.isReadable(p)) {
                return p;
            }
        }
        return null;
    }

    private String readLastLines(Path file, int n) throws IOException {
        long fileSize = Files.size(file);
        if (fileSize == 0) {
            return "";
        }

        long bufferSize = Math.min(fileSize, TAIL_BUFFER_BYTES);
        long startPos = fileSize - bufferSize;

        byte[] buffer = new byte[(int) bufferSize];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(startPos);
            int read = raf.read(buffer);
            if (read < bufferSize) {
                buffer = Arrays.copyOf(buffer, read);
            }
        }

        String content = new String(buffer, StandardCharsets.UTF_8);

        // When not at file start, skip the first (potentially partial) line
        if (startPos > 0) {
            int firstNewline = content.indexOf('\n');
            if (firstNewline >= 0) {
                content = content.substring(firstNewline + 1);
            }
        }

        String[] allLines = content.split("\n", -1);
        int from = Math.max(0, allLines.length - n);
        return String.join("\n", Arrays.asList(allLines).subList(from, allLines.length));
    }
}
