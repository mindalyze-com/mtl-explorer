package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.function.Consumer;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mtl.test.review.jdbc-url", matches = "jdbc:postgresql:.*")
class SourceIssueRepositoryIntegrationTest {
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String CHANGED_REVISION = "sha256:" + "b".repeat(64);

    @Test
    void limitedAssessmentRetiresOnlyAggregateIssuesAndPreservesUncheckedFindingsAndHistory() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS"), other = source(jdbc, "GPS"), onlyAggregate = source(jdbc, "GPS");
            var legacy = List.of(retiredMetricFinding("distance"), retiredMetricFinding("ascent"), retiredMetricFinding("descent"));
            var point = new Finding("ELEVATION", Reason.ELEVATION_CORRECTION, Severity.REVIEW, 0,
                    SourceIssueRepository.hash("real-point"), "Unchecked point correction",
                    Map.of("firstPointIndex", 25, "lastPointIndex", 25, "affectedCount", 1));
            var partialRange = new Finding("POSITION", Reason.POSITION_CORRECTION, Severity.REVIEW, 0,
                    SourceIssueRepository.hash("partial-range"), "Unchecked correction range",
                    Map.of("lastPointIndex", 30, "affectedCount", 0));
            issues.sync(id, REVISION, List.of(legacy.get(0), legacy.get(1), legacy.get(2), point, partialRange), Set.of("POSITION", "ELEVATION"));
            issues.sync(other, REVISION, List.of(legacy.getFirst()), Set.of("POSITION"));
            issues.sync(onlyAggregate, REVISION, List.of(legacy.getFirst()), Set.of("POSITION"));
            var before = issues.targets("reviewer", null, id).targets();
            issues.mutate("reviewer", List.of(before.get(1), before.get(3)), false);
            var acknowledgements = jdbc.queryForList("SELECT issue_id,acknowledged_at FROM source_issue_acknowledgement ORDER BY issue_id");
            var limit = new TrackFinding(TrackFinding.Stage.PARSING, TrackFinding.Reason.ASSESSMENT_LIMIT,
                    TrackFinding.Severity.INFORMATION, -1, null, null, 0, null, null, "Synthetic assessment limit", List.of());
            var oldSummary = new TrackFinding(TrackFinding.Stage.ELEVATION, TrackFinding.Reason.ELEVATION_CORRECTION,
                    TrackFinding.Severity.REVIEW, 0, null, null, 0, 1000.0, "metres", "Stored summary", List.of("Ascent", "Energy"));
            var limited = new TrackDiagnosis("synthetic", "a".repeat(64), 0, false, false, List.of(), List.of(limit, oldSummary));
            issues.syncGps(id, limited.sourceHash(), limited);
            issues.syncGps(onlyAggregate, limited.sourceHash(), limited);
            assertEquals(List.of(before.get(4)), issues.targets("reviewer", null, id).targets());
            assertEquals(List.of(before.get(3)), issues.targets("reviewer", null, id, State.ACKNOWLEDGED).targets());
            assertEquals(3, issues.list("reviewer", null, id, State.RESOLVED, 0, 100).total());
            assertEquals(1, issues.targets("reviewer", null, other).targets().size(), "Other sources stay untouched");
            assertTrue(issues.targets("reviewer", null, onlyAggregate).targets().isEmpty());
            var resolved = jdbc.queryForList("SELECT id,resolved_at FROM source_issue WHERE source_id=? AND resolved_at IS NOT NULL ORDER BY id", id);
            issues.syncGps(id, limited.sourceHash(), limited);
            assertEquals(resolved, jdbc.queryForList("SELECT id,resolved_at FROM source_issue WHERE source_id=? AND resolved_at IS NOT NULL ORDER BY id", id));
            assertEquals(acknowledgements, jdbc.queryForList("SELECT issue_id,acknowledged_at FROM source_issue_acknowledgement ORDER BY issue_id"));
        });
    }

    private static Finding retiredMetricFinding(String metric) {
        boolean elevation = !metric.equals("distance");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("firstPointIndex", null);
        details.put("lastPointIndex", null);
        details.put("affectedCount", 0);
        details.put("magnitude", 1000.0);
        return new Finding(elevation ? "ELEVATION" : "POSITION", elevation ? Reason.ELEVATION_CORRECTION : Reason.POSITION_CORRECTION,
                Severity.REVIEW, 0, SourceIssueRepository.hash("retired-" + metric), "Retired " + metric + " summary", details);
    }

    @Test
    void bulkAndPerFileDecisionsAreAccountScopedAndDuplicateInsertsKeepTheirTimestamp() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long gps = source(jdbc, "GPS"), media = source(jdbc, "MEDIA");
            issues.sync(gps, REVISION, List.of(finding("one"), finding("two")), Set.of("PARSING"));
            issues.sync(media, REVISION, List.of(finding("media")), Set.of("PARSING"));
            var snapshot = issues.targets("reviewer", null, null);
            assertEquals(2, snapshot.pendingFileCount());
            assertEquals(3, snapshot.targets().size());
            var result = issues.mutate("reviewer", snapshot.targets(), false);
            assertEquals(3, result.newlyAcknowledged());
            assertEquals(snapshot.targets(), result.changedTargets());
            assertEquals(0, result.summary().pendingFileCount());
            assertEquals(2, result.summary().acknowledgedFileCount());
            var timestamps = jdbc.queryForList("SELECT acknowledged_at FROM source_issue_acknowledgement ORDER BY issue_id");
            var duplicate = new ArrayList<>(snapshot.targets());
            duplicate.addAll(snapshot.targets());
            var repeated = issues.mutate("reviewer", duplicate, false);
            assertEquals(3, repeated.alreadyAcknowledged());
            assertTrue(repeated.changedTargets().isEmpty());
            assertEquals(timestamps, jdbc.queryForList("SELECT acknowledged_at FROM source_issue_acknowledgement ORDER BY issue_id"));
            assertEquals(2, issues.summary("second-reviewer").pendingFileCount());
            issues.mutate("second-reviewer", issues.targets("second-reviewer", null, gps).targets(), false);
            issues.mutate("second-reviewer", issues.targets("second-reviewer", "MEDIA", null).targets(), false);
            assertEquals(0, issues.summary("second-reviewer").pendingFileCount());
            assertEquals(3, issues.mutate("reviewer", snapshot.targets(), true).reopened());
            assertEquals(0, issues.mutate("reviewer", snapshot.targets(), true).reopened());
            assertEquals(0, issues.summary("second-reviewer").pendingFileCount());
            assertEquals(2, issues.summary("reviewer").pendingFileCount());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM track_review_decision", Integer.class));
        });
    }

    @Test
    void retriesCosmeticChangesAndOtherFileRemovalDoNotReopenIssuesButNewDefectsAndRevisionsDo() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS"), other = source(jdbc, "MEDIA");
            issues.sync(id, REVISION, List.of(finding("one"), finding("two")), Set.of("PARSING"));
            issues.sync(other, REVISION, List.of(finding("other")), Set.of("PARSING"));
            issues.mutate("reviewer", issues.targets("reviewer", null, null).targets(), false);
            var original = issues.list("reviewer", null, id, State.ACKNOWLEDGED, 0, 100).issues();
            var cosmetic = new Finding("PARSING", Reason.PARSE_FAILED, Severity.REVIEW, -1,
                    finding("one").findingHash(), "New wording", Map.of("measurement", 1.00000001));
            issues.sync(id, REVISION, List.of(finding("two"), cosmetic), Set.of("PARSING"));
            issues.resolveSource(other);
            issues.sync(id, REVISION, List.of(cosmetic), Set.of("PARSING"));
            assertEquals(0, issues.summary("reviewer").pendingFileCount());
            assertEquals(original.get(1).issueId(), issues.list("reviewer", null, id, State.ACKNOWLEDGED, 0, 100).issues().getFirst().issueId());
            issues.sync(id, REVISION, List.of(cosmetic, finding("new")), Set.of("PARSING"));
            assertEquals(1, issues.summary("reviewer").pendingFileCount());
            assertEquals(1, issues.targets("reviewer", null, id).targets().size());
            var oldTargets = issues.targets("reviewer", null, id).targets();
            issues.sync(id, CHANGED_REVISION, List.of(cosmetic, finding("new")), Set.of("PARSING"));
            assertEquals(1, issues.mutate("reviewer", oldTargets, false).staleOrResolved());
            assertEquals(2, issues.targets("reviewer", null, id).targets().size());
            assertEquals(3, issues.list("reviewer", null, id, State.RESOLVED, 0, 100).total());
        });
    }

    @Test
    void unreadableEpisodesAndIncompleteStagesPreserveUncheckedFindings() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS");
            issues.sync(id, REVISION, List.of(finding("one")), Set.of("PARSING"));
            String unreadable = issues.unreadableRevision(id);
            var unreadableFinding = new Finding("READ", Reason.SOURCE_UNREADABLE, Severity.REVIEW, -1,
                    SourceIssueRepository.hash("unreadable"), "Unreadable", Map.of());
            issues.sync(id, unreadable, List.of(unreadableFinding), Set.of("READ"));
            assertEquals(unreadable, issues.unreadableRevision(id));
            assertEquals(2, issues.targets("reviewer", null, id).targets().size());
            issues.mutate("reviewer", issues.targets("reviewer", null, id).targets(), false);
            issues.sync(id, unreadable, List.of(unreadableFinding), Set.of("READ"));
            assertEquals(0, issues.summary("reviewer").pendingFileCount());
            issues.sync(id, REVISION, List.of(), Set.of("READ"));
            assertEquals(1, issues.list("reviewer", null, id, State.ACKNOWLEDGED, 0, 100).total());
            issues.sync(id, REVISION, List.of(), Set.of("PARSING"));
            assertEquals(0, issues.targets("reviewer", null, id).targets().size());
        });
    }

    @Test
    void gpsDomainSeverityChangesIdentityWhileAssessmentLimitsRemainInformational() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS");
            var warning = gpsFinding(TrackFinding.Reason.POSITION_JUMP, TrackFinding.Severity.WARNING, "Original", 1.0);
            issues.syncGps(id, "a".repeat(64), diagnosis(warning));
            issues.mutate("reviewer", issues.targets("reviewer", null, id).targets(), false);
            issues.syncGps(id, "a".repeat(64), diagnosis(gpsFinding(warning.reason(), warning.severity(), "Cosmetic", 1.0000001)));
            assertEquals(0, issues.summary("reviewer").pendingFileCount());
            issues.syncGps(id, "a".repeat(64), diagnosis(gpsFinding(warning.reason(), TrackFinding.Severity.REVIEW, "Escalated", 1.0)));
            assertEquals(1, issues.targets("reviewer", null, id).targets().size());
            issues.syncGps(id, "a".repeat(64), diagnosis(gpsFinding(TrackFinding.Reason.ASSESSMENT_LIMIT, TrackFinding.Severity.WARNING, "Incomplete", null)));
            assertEquals(1, issues.targets("reviewer", null, id).targets().size(), "Limited assessment must retain the unchecked defect");
            assertEquals(1, issues.list("reviewer", null, id, State.INFORMATION, 0, 100).total());
        });
    }

    @Test
    void completeLargeSnapshotDoesNotIncludeLaterArrivalsAndStaleTargetsDoNotExpandIt() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS");
            var findings = new ArrayList<Finding>();
            for (int i = 0; i < 2005; i++) findings.add(finding("issue-" + i));
            issues.sync(id, REVISION, findings, Set.of("PARSING"));
            var snapshot = issues.targets("reviewer", "GPS", null);
            assertEquals(2005, snapshot.targets().size());
            findings.removeFirst();
            findings.add(finding("arrived-later"));
            issues.sync(id, REVISION, findings, Set.of("PARSING"));
            var result = issues.mutate("reviewer", snapshot.targets(), false);
            assertEquals(2004, result.newlyAcknowledged());
            assertEquals(2004, result.changedTargets().size());
            assertEquals(1, result.staleOrResolved());
            assertEquals(1, result.summary().pendingFileCount());
            assertEquals(1, issues.targets("reviewer", null, id).targets().size());
        });
    }

    @Test
    void undoOnlyReopensAcknowledgementsCreatedByItsOwnMutation() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS");
            issues.sync(id, REVISION, List.of(finding("one"), finding("two")), Set.of("PARSING"));
            var firstDeviceSelection = issues.targets("reviewer", null, id).targets();
            var earlier = issues.mutate("reviewer", List.of(firstDeviceSelection.getFirst()), false);
            var later = issues.mutate("reviewer", firstDeviceSelection, false);
            assertEquals(1, later.newlyAcknowledged());
            assertEquals(1, later.alreadyAcknowledged());
            assertEquals(List.of(firstDeviceSelection.getLast()), later.changedTargets());
            issues.mutate("reviewer", later.changedTargets(), true);
            assertEquals(earlier.changedTargets(), issues.targets("reviewer", null, id, State.ACKNOWLEDGED).targets());
            assertEquals(List.of(firstDeviceSelection.getLast()), issues.targets("reviewer", null, id).targets());
        });
    }

    @Test
    void acknowledgedSnapshotIncludesAllFileIssuesBeyondTheVisiblePageAndKeepsAccountScope() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS"), other = source(jdbc, "MEDIA");
            var findings = new ArrayList<Finding>();
            for (int i = 0; i < 51; i++) findings.add(finding("issue-" + i));
            issues.sync(id, REVISION, findings, Set.of("PARSING"));
            issues.sync(other, REVISION, List.of(finding("media")), Set.of("PARSING"));
            issues.mutate("reviewer", issues.targets("reviewer", null, null).targets(), false);
            assertEquals(50, issues.list("reviewer", null, id, State.ACKNOWLEDGED, 0, 50).issues().size());
            var snapshot = issues.targets("reviewer", "GPS", id, State.ACKNOWLEDGED);
            assertEquals(0, snapshot.pendingFileCount());
            assertEquals(51, snapshot.targets().size());
            assertTrue(issues.targets("another-reviewer", null, id, State.ACKNOWLEDGED).targets().isEmpty());
            var reopened = issues.mutate("reviewer", snapshot.targets(), true);
            assertEquals(51, reopened.reopened());
            assertEquals(snapshot.targets(), reopened.changedTargets());
            assertEquals(1, issues.targets("reviewer", "MEDIA", null, State.ACKNOWLEDGED).targets().size());
            assertEquals(1, reopened.summary().pendingFileCount());
        });
    }

    @Test
    void removalRetainsHistoryAndHardDeletionCascadesAcknowledgements() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "MEDIA");
            issues.sync(id, REVISION, List.of(finding("one")), Set.of("PARSING"));
            issues.mutate("reviewer", issues.targets("reviewer", null, id).targets(), false);
            issues.resolveSource(id);
            jdbc.update("UPDATE indexed_file SET indexer_status='REMOVED' WHERE id=?", id);
            assertEquals(1, issues.list("reviewer", null, id, State.RESOLVED, 0, 100).total());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM source_issue_acknowledgement", Integer.class));
            jdbc.update("DELETE FROM indexed_file WHERE id=?", id);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM source_issue_acknowledgement", Integer.class));
        });
    }

    @Test
    void failedSecondBatchRollsBackTheWholeAcknowledgement() {
        inDatabase(jdbc -> {
            var issues = repository(jdbc);
            long id = source(jdbc, "GPS");
            jdbc.update("""
                    INSERT INTO source_issue(source_id,source_revision,finding_hash,stage,reason,severity,message)
                    SELECT ?,?,md5(n::text),'PARSING','PARSE_FAILED','REVIEW','Synthetic issue'
                    FROM generate_series(1,2001) n
                    """, id, REVISION);
            var snapshot = issues.targets("rollback", null, id);
            long lastId = snapshot.targets().getLast().issueId();
            jdbc.execute("""
                    CREATE FUNCTION synthetic_ack_failure() RETURNS trigger LANGUAGE plpgsql AS $body$
                    BEGIN IF NEW.account_key='rollback' AND NEW.issue_id >= %d THEN
                      RAISE EXCEPTION 'Synthetic second-batch failure'; END IF; RETURN NEW; END $body$;
                    CREATE TRIGGER synthetic_ack_failure BEFORE INSERT ON source_issue_acknowledgement
                      FOR EACH ROW EXECUTE FUNCTION synthetic_ack_failure();
                    """.formatted(lastId));
            var manager = new DataSourceTransactionManager(jdbc.getDataSource());
            manager.setNestedTransactionAllowed(true);
            var transaction = new TransactionTemplate(manager);
            transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> transaction.executeWithoutResult(status -> issues.mutate("rollback", snapshot.targets(), false)));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM source_issue_acknowledgement WHERE account_key='rollback'", Integer.class));
            assertEquals(2001, issues.targets("rollback", null, id).targets().size());
        });
    }

    @Test
    void canonicalAccountComesFromTheAuthenticatedPrincipal() {
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("Reviewer", "", List.of()));
            assertEquals("reviewer", IssueAccount.current());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Finding finding(String key) {
        return new Finding("PARSING", Reason.PARSE_FAILED, Severity.REVIEW, -1,
                SourceIssueRepository.hash(key), "Synthetic defect " + key, Map.of());
    }

    private static TrackFinding gpsFinding(TrackFinding.Reason reason, TrackFinding.Severity severity, String message, Double magnitude) {
        return new TrackFinding(TrackFinding.Stage.POSITION, reason, severity, 0, 1, 2, 1, magnitude, "metres", message, List.of("Map"));
    }

    private static TrackDiagnosis diagnosis(TrackFinding finding) {
        return new TrackDiagnosis("synthetic", "a".repeat(64), 10, true, false, List.of(), List.of(finding));
    }

    private static long source(JdbcTemplate jdbc, String index) {
        return jdbc.queryForObject("INSERT INTO indexed_file(\"index\",name,path,full_path,indexer_status) VALUES(?,?,'/synthetic','/synthetic/source','COMPLETED_WITH_SUCCESS') RETURNING id",
                Long.class, index, "synthetic-" + UUID.randomUUID());
    }

    private static SourceIssueRepository repository(JdbcTemplate jdbc) {
        return new SourceIssueRepository(jdbc, new JsonMapper());
    }

    private static void inDatabase(Consumer<JdbcTemplate> assertions) {
        var dataSource = new DriverManagerDataSource(System.getProperty("mtl.test.review.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            try { assertions.accept(new JdbcTemplate(dataSource)); }
            finally { status.setRollbackOnly(); }
        });
    }
}
