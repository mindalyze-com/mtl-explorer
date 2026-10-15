package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.indexer.SourceUnreadableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceIssueIngestService {
    private final SourceIssueRepository issues;

    /** Stream media contents without retaining large videos in memory. */
    public String contentRevision(IndexedFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(Path.of(file.getFullPath())), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException error) {
            log.warn("Source contents unreadable: sourceId={} type={} message={}", file.getId(), error.getClass().getSimpleName(), error.getMessage());
            throw new SourceUnreadableException(error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    @Transactional
    public void empty(long sourceId, String revision) {
        issues.sync(sourceId, revision, List.of(finding("READ", Reason.EMPTY_FILE, Severity.INFORMATION,
                "Empty file skipped. Any earlier imported data is retained. It will be processed when its contents change.")),
                Set.of("READ", "CONVERSION", "PARSING", "TIMING", "POSITION", "ELEVATION", "PROCESSING", "METADATA"));
        log.info("Empty source skipped: sourceId={}", sourceId);
    }

    @Transactional
    public void unreadable(long sourceId, String message) {
        issues.sync(sourceId, issues.unreadableRevision(sourceId), List.of(finding("READ", Reason.SOURCE_UNREADABLE,
                Severity.REVIEW, message)), Set.of("READ"));
    }

    @Transactional
    public void mediaFailed(long sourceId, String revision, String message) {
        issues.sync(sourceId, revision, List.of(finding("METADATA", Reason.MEDIA_METADATA_UNREADABLE, Severity.REVIEW, message)),
                Set.of("READ", "METADATA"));
    }

    @Transactional
    public void mediaCompleted(long sourceId, String revision) {
        issues.sync(sourceId, revision, List.of(), Set.of("READ", "METADATA"));
    }

    @Transactional
    public void removed(long sourceId) {
        issues.resolveSource(sourceId);
    }

    private static Finding finding(String stage, Reason reason, Severity severity, String message) {
        return new Finding(stage, reason, severity, -1, SourceIssueRepository.hash(stage + ":" + reason + ":" + severity),
                message, Map.of());
    }

}
