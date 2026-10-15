package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;

@RestController
@RequestMapping("/api/source-issues")
@RequiredArgsConstructor
@Slf4j
public class SourceIssueController {
    private final SourceIssueRepository issues;
    private final DemoWriteAccess demoWriteAccess;

    @GetMapping
    @Operation(operationId = "getSourceIssues")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SourceIssuePage list(@RequestParam(required = false) String index, @RequestParam(required = false) Long sourceId,
                          @RequestParam(required = false) State state, @RequestParam(defaultValue = "0") int offset,
                          @RequestParam(defaultValue = "50") int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid issue page.");
        return issues.list(account(), index(index), sourceId, state, offset, limit);
    }

    @GetMapping("/acknowledgement-targets")
    @Operation(operationId = "getSourceIssueAcknowledgementTargets")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SourceIssueTargetsSnapshot targets(@RequestParam(required = false) String index, @RequestParam(required = false) Long sourceId,
                                              @Parameter(schema = @Schema(allowableValues = {"PENDING", "ACKNOWLEDGED"}))
                                              @RequestParam(defaultValue = "PENDING") State state) {
        if (state != State.PENDING && state != State.ACKNOWLEDGED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select pending or acknowledged issues.");
        return issues.targets(account(), index(index), sourceId, state);
    }

    @PostMapping("/acknowledgements")
    @Operation(operationId = "acknowledgeSourceIssues")
    @Transactional
    public SourceIssueMutationResult acknowledge(@RequestBody SourceIssueMutationRequest request) {
        return mutate(request, false);
    }

    @PostMapping("/reopen")
    @Operation(operationId = "reopenSourceIssues")
    @Transactional
    public SourceIssueMutationResult reopen(@RequestBody SourceIssueMutationRequest request) {
        return mutate(request, true);
    }

    private SourceIssueMutationResult mutate(SourceIssueMutationRequest request, boolean reopen) {
        demoWriteAccess.requireImportAndReviewAccess();
        List<SourceIssueTarget> targets = request.targets();
        if (targets == null || targets.stream().anyMatch(t -> t == null || t.issueId() <= 0
                || t.sourceRevision() == null || t.sourceRevision().isBlank() || t.sourceRevision().length() > 80))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply issue IDs and source revisions.");
        long started = System.currentTimeMillis();
        log.info("Source issue {} started: targets={}", reopen ? "reopen" : "acknowledgement", targets.size());
        var result = issues.mutate(account(), targets, reopen);
        log.info("Source issue {} completed: changed={} stale={} pendingFiles={} durationMs={}", reopen ? "reopen" : "acknowledgement",
                reopen ? result.reopened() : result.newlyAcknowledged(), result.staleOrResolved(), result.summary().pendingFileCount(), System.currentTimeMillis() - started);
        return result;
    }

    private static String account() {
        String account = IssueAccount.current();
        if (account.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required.");
        return account;
    }

    private static String index(String index) {
        if (index != null && !List.of("GPS", "MEDIA").contains(index))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source index.");
        return index;
    }
}
