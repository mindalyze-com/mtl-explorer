package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;

@Repository
@RequiredArgsConstructor
public class SourceIssueRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private static final int MUTATION_BATCH_SIZE = 2000;
    private static final String PENDING = """
            EXISTS(SELECT 1 FROM source_issue si WHERE si.source_id=i.id AND si.resolved_at IS NULL
                AND si.severity='REVIEW' AND NOT EXISTS(SELECT 1 FROM source_issue_acknowledgement a
                    WHERE a.issue_id=si.id AND a.account_key=:account))
            """;

    public static String pendingSourcePredicate() {
        return PENDING;
    }

    public String unreadableRevision(long sourceId) {
        return jdbc.query("""
                SELECT source_revision FROM source_issue WHERE source_id=? AND resolved_at IS NULL
                  AND source_revision LIKE 'unreadable:%' ORDER BY id DESC LIMIT 1
                """, (rs, n) -> rs.getString(1), sourceId).stream().findFirst()
                .orElseGet(() -> "unreadable:" + UUID.randomUUID());
    }

    public static String revision(String hash) {
        return hash.startsWith("unreadable:") ? hash : "sha256:" + hash;
    }

    public void syncGps(long sourceId, String sourceHash, TrackDiagnosis diagnosis) {
        String revision = sourceHash.equals("unreadable") ? unreadableRevision(sourceId) : revision(sourceHash);
        Set<String> completed = new HashSet<>(List.of("READ"));
        var reasons = diagnosis.findings().stream().map(TrackFinding::reason).toList();
        boolean limited = reasons.contains(TrackFinding.Reason.ASSESSMENT_LIMIT);
        if (!reasons.contains(TrackFinding.Reason.SOURCE_UNREADABLE)) completed.add("CONVERSION");
        if (!limited && !reasons.contains(TrackFinding.Reason.CONVERSION_FAILED)
            && !reasons.contains(TrackFinding.Reason.SOURCE_UNREADABLE)) completed.add("PARSING");
        if (!limited && !reasons.contains(TrackFinding.Reason.PARSE_FAILED)
            && !reasons.contains(TrackFinding.Reason.CONVERSION_FAILED) && !reasons.contains(TrackFinding.Reason.SOURCE_UNREADABLE)) {
            completed.addAll(List.of("TIMING", "POSITION", "ELEVATION"));
            if (!reasons.contains(TrackFinding.Reason.PROCESSING_EVIDENCE_UNAVAILABLE)) completed.add("PROCESSING");
        }
        List<Finding> findings = diagnosis.findings().stream()
                // Unexpected import/service failures keep operational recovery, not source acceptance.
                .filter(f -> f.reason() != TrackFinding.Reason.PROCESSING_FAILED)
                .filter(f -> !retiredMetricSummary(f))
                .map(f -> {
                    Severity severity = com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine.requiresReview(f)
                            ? Severity.REVIEW : Severity.INFORMATION;
                    String identity = String.join(":", f.stage().name(), f.reason().name(), f.severity().name(), severity.name(),
                            Integer.toString(f.sectionIndex()), String.valueOf(f.firstPointIndex()), String.valueOf(f.lastPointIndex()),
                            Integer.toString(f.affectedCount()));
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("domainSeverity", f.severity().name());
                    details.put("firstPointIndex", f.firstPointIndex());
                    details.put("lastPointIndex", f.lastPointIndex());
                    details.put("affectedCount", f.affectedCount());
                    details.put("magnitude", f.magnitude());
                    details.put("unit", f.unit());
                    details.put("affectedCapabilities", f.affectedCapabilities());
                    return new Finding(f.stage().name(), Reason.valueOf(f.reason().name()), severity, f.sectionIndex(),
                            hash(identity), f.message(), details);
                }).toList();
        sync(sourceId, revision, findings, completed);
        // sync holds the source row lock. Retiring a removed rule does not require completing
        // position/elevation assessment; keep every unrelated unchecked finding and its history.
        jdbc.update("""
                UPDATE source_issue SET resolved_at=CURRENT_TIMESTAMP
                WHERE source_id=? AND resolved_at IS NULL
                  AND ((stage='POSITION' AND reason='POSITION_CORRECTION')
                    OR (stage='ELEVATION' AND reason='ELEVATION_CORRECTION'))
                  AND details->>'affectedCount'='0'
                  AND details->>'firstPointIndex' IS NULL AND details->>'lastPointIndex' IS NULL
                """, sourceId);
    }

    private static boolean retiredMetricSummary(TrackFinding finding) {
        return finding.affectedCount() == 0 && finding.firstPointIndex() == null && finding.lastPointIndex() == null
               && (finding.stage() == TrackFinding.Stage.POSITION && finding.reason() == TrackFinding.Reason.POSITION_CORRECTION
                   || finding.stage() == TrackFinding.Stage.ELEVATION && finding.reason() == TrackFinding.Reason.ELEVATION_CORRECTION);
    }

    public static String hash(String identity) {
        return GpxDocument.hash(identity.getBytes(StandardCharsets.UTF_8));
    }

    public void sync(long sourceId, String revision, List<Finding> findings, Set<String> completedStages) {
        jdbc.queryForObject("SELECT id FROM indexed_file WHERE id=? FOR UPDATE", Long.class, sourceId);
        var parameters = new MapSqlParameterSource("source", sourceId).addValue("revision", revision);
        var named = new NamedParameterJdbcTemplate(jdbc);
        // Reading establishes a content revision; an unreadable episode cannot establish new bytes.
        if (revision.startsWith("sha256:")) named.update("""
                UPDATE source_issue SET resolved_at=CURRENT_TIMESTAMP WHERE source_id=:source
                  AND source_revision<>:revision AND resolved_at IS NULL
                """, parameters);
        for (Finding finding : findings) {
            jdbc.update("""
                    INSERT INTO source_issue(source_id,source_revision,finding_hash,stage,reason,severity,section_index,message,details)
                    VALUES(?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(source_id,source_revision,finding_hash)
                    DO UPDATE SET message=EXCLUDED.message,details=EXCLUDED.details,last_seen_at=CURRENT_TIMESTAMP,resolved_at=NULL
                    """, sourceId, revision, finding.findingHash(), finding.stage(), finding.reason().name(),
                    finding.severity().name(), finding.sectionIndex(), finding.message(), mapper.writeValueAsString(finding.details()));
        }
        if (!completedStages.isEmpty()) {
            parameters.addValue("stages", completedStages).addValue("hashes", findings.stream().map(Finding::findingHash).toList());
            named.update("""
                    UPDATE source_issue SET resolved_at=CURRENT_TIMESTAMP WHERE source_id=:source AND source_revision=:revision
                      AND resolved_at IS NULL AND stage IN (:stages)
                    """ + (findings.isEmpty() ? "" : " AND finding_hash NOT IN (:hashes)"), parameters);
        }
    }

    public void resolveSource(long sourceId) {
        jdbc.update("UPDATE source_issue SET resolved_at=CURRENT_TIMESTAMP WHERE source_id=? AND resolved_at IS NULL", sourceId);
    }

    public SourceIssueSummary summary(String account) {
        var named = new NamedParameterJdbcTemplate(jdbc);
        List<SourceIssueIndexSummary> indexes = named.query("""
                SELECT grouped.index, COUNT(*) FILTER(WHERE grouped.pending) AS pending,
                  COUNT(*) FILTER(WHERE NOT grouped.pending) AS acknowledged FROM (
                SELECT i.index,si.source_id,bool_or(a.issue_id IS NULL) AS pending
                FROM source_issue si JOIN indexed_file i ON i.id=si.source_id
                LEFT JOIN source_issue_acknowledgement a ON a.issue_id=si.id AND a.account_key=:account
                WHERE si.resolved_at IS NULL AND si.severity='REVIEW' AND i.index IN ('GPS','MEDIA')
                  AND i.indexer_status NOT IN ('REMOVED','EXCLUDED') GROUP BY i.index,si.source_id) grouped GROUP BY grouped.index ORDER BY grouped.index
                """, new MapSqlParameterSource("account", account),
                (rs, n) -> new SourceIssueIndexSummary(rs.getString(1), rs.getLong(2), rs.getLong(3)));
        return new SourceIssueSummary(indexes.stream().mapToLong(SourceIssueIndexSummary::pendingFileCount).sum(),
                indexes.stream().mapToLong(SourceIssueIndexSummary::acknowledgedFileCount).sum(), indexes);
    }

    public SourceIssueTargetsSnapshot targets(String account, String index, Long sourceId) {
        return targets(account, index, sourceId, State.PENDING);
    }

    public SourceIssueTargetsSnapshot targets(String account, String index, Long sourceId, State state) {
        if (state != State.PENDING && state != State.ACKNOWLEDGED)
            throw new IllegalArgumentException("Select pending or acknowledged issues.");
        var params = parameters(account, index, sourceId);
        List<Long> sourceIds = new ArrayList<>();
        List<SourceIssueTarget> targets = new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT si.id,si.source_revision,si.source_id FROM source_issue si JOIN indexed_file i ON i.id=si.source_id
                WHERE si.resolved_at IS NULL AND si.severity='REVIEW' AND i.index IN ('GPS','MEDIA')
                  AND i.indexer_status NOT IN ('REMOVED','EXCLUDED') AND (:index IS NULL OR i.index=:index)
                  AND (:source IS NULL OR i.id=:source) AND %s EXISTS(SELECT 1 FROM source_issue_acknowledgement a
                    WHERE a.issue_id=si.id AND a.account_key=:account) ORDER BY si.id
                """.formatted(state == State.PENDING ? "NOT" : ""), params, (rs, n) -> {
                    sourceIds.add(rs.getLong(3));
                    return new SourceIssueTarget(rs.getLong(1), rs.getString(2));
                });
        long fileCount = sourceIds.stream().distinct().count();
        return new SourceIssueTargetsSnapshot(state == State.PENDING ? fileCount : 0, targets);
    }

    public SourceIssueMutationResult mutate(String account, List<SourceIssueTarget> submitted, boolean reopen) {
        List<SourceIssueTarget> targets = submitted.stream().distinct().toList();
        int changed = 0, valid = 0;
        List<SourceIssueTarget> changedTargets = new ArrayList<>();
        var named = new NamedParameterJdbcTemplate(jdbc);
        for (int start = 0; start < targets.size(); start += MUTATION_BATCH_SIZE) {
            var params = new MapSqlParameterSource("account", account).addValue("targets",
                    mapper.writeValueAsString(targets.subList(start, Math.min(targets.size(), start + MUTATION_BATCH_SIZE))));
            // Use the same transactional source guard as assessment. No per-target queries or reassessments.
            named.query("""
                    SELECT i.id FROM indexed_file i WHERE i.id IN (
                      SELECT si.source_id FROM source_issue si JOIN jsonb_to_recordset(CAST(:targets AS jsonb))
                        AS target("issueId" bigint,"sourceRevision" text) ON si.id=target."issueId")
                    ORDER BY i.id FOR UPDATE
                    """, params, (rs, n) -> rs.getLong(1));
            String selected = """
                    WITH selected AS (SELECT DISTINCT si.id,si.source_revision FROM source_issue si JOIN indexed_file i ON i.id=si.source_id
                      JOIN jsonb_to_recordset(CAST(:targets AS jsonb)) AS target("issueId" bigint,"sourceRevision" text)
                        ON si.id=target."issueId" AND si.source_revision=target."sourceRevision"
                      WHERE si.resolved_at IS NULL AND si.severity='REVIEW' AND i.index IN ('GPS','MEDIA')
                        AND i.indexer_status NOT IN ('REMOVED','EXCLUDED')),
                    """;
            String write = reopen ? """
                    changed AS (DELETE FROM source_issue_acknowledgement a USING selected s
                      WHERE a.issue_id=s.id AND a.account_key=:account RETURNING a.issue_id)
                    """ : """
                    changed AS (INSERT INTO source_issue_acknowledgement(issue_id,account_key)
                      SELECT id,:account FROM selected ON CONFLICT(issue_id,account_key) DO NOTHING RETURNING issue_id)
                    """;
            var counts = named.queryForMap(selected + write + """
                    SELECT (SELECT COUNT(*) FROM selected) AS valid,(SELECT COUNT(*) FROM changed) AS changed,
                      COALESCE((SELECT jsonb_agg(jsonb_build_object('issueId',s.id,'sourceRevision',s.source_revision) ORDER BY s.id)
                        FROM changed c JOIN selected s ON s.id=c.issue_id),'[]'::jsonb) AS changed_targets
                    """, params);
            valid += ((Number) counts.get("valid")).intValue();
            changed += ((Number) counts.get("changed")).intValue();
            changedTargets.addAll(mapper.readValue(counts.get("changed_targets").toString(),
                    new TypeReference<List<SourceIssueTarget>>() {}));
        }
        return new SourceIssueMutationResult(reopen ? 0 : changed, reopen ? 0 : valid - changed, targets.size() - valid,
                reopen ? changed : 0, List.copyOf(changedTargets), summary(account));
    }

    public SourceIssuePage list(String account, String index, Long sourceId, State state, int offset, int limit) {
        var params = parameters(account, index, sourceId).addValue("state", state == null ? null : state.name(), java.sql.Types.VARCHAR)
                .addValue("offset", offset).addValue("limit", limit);
        String base = """
                FROM source_issue si JOIN indexed_file i ON i.id=si.source_id
                LEFT JOIN source_issue_acknowledgement a ON a.issue_id=si.id AND a.account_key=:account
                WHERE i.index IN ('GPS','MEDIA') AND (si.resolved_at IS NOT NULL OR i.indexer_status NOT IN ('REMOVED','EXCLUDED')) AND (:index IS NULL OR i.index=:index) AND (:source IS NULL OR i.id=:source)
                  AND (:state IS NULL OR CASE WHEN si.resolved_at IS NOT NULL THEN 'RESOLVED'
                    WHEN si.severity='INFORMATION' THEN 'INFORMATION' WHEN a.issue_id IS NULL THEN 'PENDING'
                    ELSE 'ACKNOWLEDGED' END=:state)
                """;
        var named = new NamedParameterJdbcTemplate(jdbc);
        long total = Objects.requireNonNull(named.queryForObject("SELECT COUNT(*) " + base, params, Long.class));
        List<SourceIssue> issues = named.query("SELECT si.*,i.index,i.name,i.full_path,a.acknowledged_at " + base
                                       + " ORDER BY si.resolved_at NULLS FIRST,si.id DESC LIMIT :limit OFFSET :offset", params, (rs, n) -> issue(rs));
        return new SourceIssuePage(total, issues, summary(account));
    }

    private SourceIssue issue(ResultSet rs) throws SQLException {
        var resolved = instant(rs, "resolved_at");
        var acknowledged = instant(rs, "acknowledged_at");
        Severity severity = Severity.valueOf(rs.getString("severity"));
        return new SourceIssue(rs.getLong("id"), rs.getLong("source_id"), rs.getString("index"), rs.getString("name"),
                rs.getString("full_path"), rs.getString("source_revision"), rs.getString("stage"), Reason.valueOf(rs.getString("reason")),
                severity, rs.getInt("section_index"), rs.getString("message"),
                mapper.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {}),
                resolved != null ? State.RESOLVED : severity == Severity.INFORMATION ? State.INFORMATION
                        : acknowledged == null ? State.PENDING : State.ACKNOWLEDGED,
                instant(rs, "first_seen_at"), instant(rs, "last_seen_at"), resolved, acknowledged);
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var timestamp = rs.getTimestamp(name);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static MapSqlParameterSource parameters(String account, String index, Long sourceId) {
        return new MapSqlParameterSource("account", account).addValue("index", index, java.sql.Types.VARCHAR)
                .addValue("source", sourceId, java.sql.Types.BIGINT);
    }
}
