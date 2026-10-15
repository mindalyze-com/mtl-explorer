package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.ProcessedRecording;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingIssue;
import com.x8ing.mtl.server.mtlserver.sourceissues.IssueAccount;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SourceReviewRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private final SourceIssueRepository issues;
    private static final double DEFAULT_EXPLORATION_CORRIDOR_METERS = 25.0;
    private static final int DEFAULT_EXPLORATION_PRECISION_METERS = 10;
    @Value("${mtl.exploration.corridor-width-m:25.0}")
    private double explorationCorridorMeters = DEFAULT_EXPLORATION_CORRIDOR_METERS;
    @Value("${mtl.exploration.use-track-precision:10}")
    private int explorationPrecisionMeters = DEFAULT_EXPLORATION_PRECISION_METERS;

    public record Assessment(long sourceId, String sourceHash, TrackDiagnosis diagnosis, Instant assessedAt) {
    }

    private record ImportedRevision(String hash, boolean estimated, boolean unreliable, boolean withheld) {
    }

    public Optional<Assessment> find(long sourceId) {
        return jdbc.query("SELECT source_id, source_hash, diagnosis_json, assessed_at FROM track_source_review WHERE source_id=?",
                (rs, n) -> new Assessment(rs.getLong(1), rs.getString(2), decode(rs.getString(3)), rs.getTimestamp(4).toInstant()), sourceId).stream().findFirst();
    }

    public Map<Long, String> failureMessages(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) return Map.of();
        Map<Long, String> messages = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query(
                "SELECT source_id, diagnosis_json FROM track_source_review WHERE source_id IN (:ids)",
                new MapSqlParameterSource("ids", sourceIds), rs -> {
                    long sourceId = rs.getLong("source_id");
                    TrackDiagnosis diagnosis;
                    try {
                        diagnosis = mapper.readValue(rs.getString("diagnosis_json"), TrackDiagnosis.class);
                    } catch (RuntimeException error) {
                        log.warn("Cannot read failure diagnosis: sourceId={} type={} message={}",
                                sourceId, error.getClass().getSimpleName(), error.getMessage());
                        log.debug("Invalid stored failure diagnosis", error);
                        return;
                    }
                    if (diagnosis == null || diagnosis.findings() == null) {
                        log.warn("Failure diagnosis has no findings: sourceId={}", sourceId);
                        return;
                    }
                    String message = diagnosis.findings().stream()
                            .filter(Objects::nonNull).filter(finding -> finding.reason() != null)
                            .filter(finding -> switch (finding.reason()) {
                                case SOURCE_UNREADABLE, CONVERSION_FAILED, PARSE_FAILED, NO_OBSERVATIONS -> true;
                                default -> false;
                            })
                            .map(TrackFinding::message).filter(Objects::nonNull).filter(value -> !value.isBlank())
                            .distinct().collect(java.util.stream.Collectors.joining(" "));
                    if (!message.isBlank()) messages.put(sourceId, message);
                });
        return messages;
    }

    public void save(long sourceId, String sourceHash, TrackDiagnosis diagnosis, ReviewState state) {
        issues.syncGps(sourceId, sourceHash, diagnosis);
        jdbc.update("""
                INSERT INTO track_source_review(source_id,source_hash,diagnosis_json,review_state,assessed_at) VALUES(?,?,?,?,?)
                ON CONFLICT(source_id) DO UPDATE SET source_hash=EXCLUDED.source_hash,diagnosis_json=EXCLUDED.diagnosis_json,review_state=EXCLUDED.review_state,assessed_at=EXCLUDED.assessed_at
                """, sourceId, sourceHash, mapper.writeValueAsString(diagnosis), state.name(), Timestamp.from(Instant.now()));
    }

    public void setState(long sourceId, ReviewState state) {
        jdbc.update("UPDATE track_source_review SET review_state=? WHERE source_id=?", state.name(), sourceId);
    }

    public void lockSource(long sourceId) {
        jdbc.queryForObject("SELECT id FROM indexed_file WHERE id=? FOR UPDATE", Long.class, sourceId);
    }

    public TrackReviewQueue queue(ReviewState state, String search, TrackFinding.Reason reason,
                                  TrackFinding.Severity severity, Decision decision, int offset, int limit) {
        String pending = SourceIssueRepository.pendingSourcePredicate();
        String effectiveState = "CASE WHEN " + pending + " THEN 'NEEDS_REVIEW' ELSE COALESCE(r.review_state,'NOT_ASSESSED') END";
        String predicate = "i.index='GPS' AND i.indexer_status NOT IN ('REMOVED','EXCLUDED')";
        var counts = new NamedParameterJdbcTemplate(jdbc).queryForMap("""
                                              SELECT COUNT(*) AS total, COUNT(*) FILTER(WHERE %s) AS pending,
                                                     COUNT(*) FILTER(WHERE r.review_state IN ('EXCLUDED','EXCLUDED_MAP','EXCLUDED_STATISTICS','MIXED')
                                                       OR EXISTS(SELECT 1 FROM gps_track t WHERE t.file_id=i.id AND (t.review_excluded OR t.review_map_excluded))) AS excluded,
                                                     COUNT(*) FILTER(WHERE r.source_id IS NULL OR r.review_state='NOT_ASSESSED') AS unassessed
                                              FROM indexed_file i LEFT JOIN track_source_review r ON r.source_id=i.id WHERE
                                              """.formatted(pending) + predicate, new MapSqlParameterSource("account", IssueAccount.current()));
        var parameters = new MapSqlParameterSource("account", IssueAccount.current())
                .addValue("state", state == null ? null : state.name(), java.sql.Types.VARCHAR)
                .addValue("reason", reason == null ? null : reason.name(), java.sql.Types.VARCHAR)
                .addValue("severity", severity == null ? null : severity.name(), java.sql.Types.VARCHAR)
                .addValue("decision", decision == null ? null : decision.name(), java.sql.Types.VARCHAR)
                .addValue("search", "%" + search + "%").addValue("limit", limit).addValue("offset", offset);
        List<SourceReviewItem> rows = new NamedParameterJdbcTemplate(jdbc).query("""
                                                                                         SELECT i.id,i.name,r.source_hash,%s,
                                                                                                (SELECT COUNT(*) FROM gps_track t WHERE t.file_id=i.id),r.diagnosis_json,r.assessed_at
                                                                                         FROM indexed_file i LEFT JOIN track_source_review r ON r.source_id=i.id WHERE
                                                                                         """.formatted(effectiveState) + predicate + """
                                                                                          AND (:state IS NULL OR %s=:state) AND i.name ILIKE :search
                                                                                          AND ((:reason IS NULL AND :severity IS NULL) OR EXISTS (
                                                                                              SELECT 1 FROM jsonb_array_elements(r.diagnosis_json::jsonb->'findings') finding
                                                                                              WHERE (:reason IS NULL OR finding->>'reason'=:reason) AND (:severity IS NULL OR finding->>'severity'=:severity)))
                                                                                          AND (:decision IS NULL OR EXISTS (
                                                                                              SELECT 1 FROM (SELECT DISTINCT ON(section_index) decision FROM track_review_decision
                                                                                                  WHERE source_id=i.id AND source_hash=r.source_hash ORDER BY section_index,id DESC) latest
                                                                                              WHERE latest.decision=:decision))
                                                                                          ORDER BY CASE WHEN %s THEN 0 ELSE 1 END,i.id DESC LIMIT :limit OFFSET :offset
                                                                                         """.formatted(effectiveState, pending), parameters,
                (rs, n) -> {
                    TrackDiagnosis diagnosis = rs.getString(6) == null ? null : decode(rs.getString(6));
                    return reviewItem(rs.getLong(1), rs.getString(2), rs.getString(3), ReviewState.valueOf(rs.getString(4)), rs.getInt(5),
                            diagnosis, rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant());
                });
        return new TrackReviewQueue(((Number) counts.get("pending")).intValue(), ((Number) counts.get("excluded")).intValue(),
                ((Number) counts.get("unassessed")).intValue(), ((Number) counts.get("total")).intValue(), rows);
    }

    /**
     * Map/statistics choices survive reassessment. A later review does not release an exclusion.
     */
    public boolean hasPendingIssues(long sourceId) {
        return Boolean.TRUE.equals(new NamedParameterJdbcTemplate(jdbc).queryForObject(
                "SELECT " + SourceIssueRepository.pendingSourcePredicate() + " FROM indexed_file i WHERE i.id=:source",
                new MapSqlParameterSource("account", IssueAccount.current()).addValue("source", sourceId), Boolean.class));
    }

    public Map<Integer, Decision> useDecisions(Assessment assessment) {
        Map<Integer, Decision> result = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON(section_index) section_index,decision FROM track_review_decision
                WHERE source_id=? AND source_hash=? ORDER BY section_index,id DESC
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getInt(1), Decision.valueOf(rs.getString(2))), assessment.sourceId(), assessment.sourceHash());
        return result;
    }

    static SourceReviewItem reviewItem(long sourceId, String name, String hash, ReviewState state, int trackCount,
                                       TrackDiagnosis diagnosis, Instant assessedAt) {
        List<TrackFinding> findings = diagnosis == null ? List.of() : diagnosis.findings();
        var attention = findings.stream().filter(com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine::requiresReview).toList();
        var information = findings.stream().filter(f -> !com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine.requiresReview(f)).toList();
        return new SourceReviewItem(sourceId, name, hash, state, trackCount, diagnosis == null ? 0 : diagnosis.pointCount(),
                findings.size(), attention.size(), information.size(), assessedAt,
                findings.stream().map(f -> f.reason().name()).distinct().toList(),
                attention.stream().map(f -> f.reason().name()).distinct().toList(),
                information.stream().map(f -> f.reason().name()).distinct().toList());
    }

    public void decide(Assessment assessment, int section, Decision decision) {
        if (decision == Decision.LATER) return;
        jdbc.update("INSERT INTO track_review_decision(source_id,source_hash,section_index,decision,decided_by,decided_at) VALUES(?,?,?,?,?,?)",
                assessment.sourceId(), assessment.sourceHash(), section, decision.name(), IssueAccount.current(), Timestamp.from(Instant.now()));
    }

    public List<ReviewDecisionHistory> history(long sourceId) {
        return jdbc.query("SELECT source_hash,section_index,decision,decided_at FROM track_review_decision WHERE source_id=? ORDER BY id DESC LIMIT 100",
                (rs, n) -> new ReviewDecisionHistory(rs.getString(1), rs.getInt(2), Decision.valueOf(rs.getString(3)), rs.getTimestamp(4).toInstant()), sourceId);
    }

    public List<ReviewedTrack> tracks(long sourceId) {
        return jdbc.query("SELECT id,track_name,source_recording_section,review_status FROM gps_track WHERE file_id=? ORDER BY id",
                (rs, n) -> new ReviewedTrack(rs.getLong(1), rs.getString(2), rs.getObject(3, Integer.class), rs.getString(4)), sourceId);
    }

    public List<ProcessedRecording> processedRecordings(long sourceId) {
        return jdbc.query("SELECT source_content_hash,source_recording_section,start_date,end_date,track_duration_in_motion_secs,track_length_in_meter FROM gps_track WHERE file_id=? AND load_status='SUCCESS'",
                (rs, n) -> new ProcessedRecording(rs.getString(1), rs.getObject(2, Integer.class),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                        rs.getObject(5) == null ? null : rs.getDouble(5), rs.getObject(6) == null ? null : rs.getDouble(6)), sourceId);
    }

    /**
     * Read the evidence from exactly the imported source revision. Reassessment never re-cleans tracks.
     */
    public List<GpsProcessingIssue> processingIssues(long sourceId, String sourceHash) {
        List<GpsProcessingIssue> issues = new ArrayList<>();
        jdbc.query("""
                SELECT d.processing_issues FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id
                WHERE t.file_id=? AND t.source_content_hash=? AND t.load_status='SUCCESS' AND d.track_type='RAW'
                ORDER BY t.id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String json = rs.getString(1);
            if (json == null) return;
            try {
                issues.addAll(mapper.readValue(json, new TypeReference<List<GpsProcessingIssue>>() {
                }));
            } catch (RuntimeException error) {
                log.error("Cannot read import processing evidence: sourceId={} type={} message={}",
                        sourceId, error.getClass().getSimpleName(), error.getMessage(), error);
                throw error;
            }
        }, sourceId, sourceHash);
        return issues;
    }

    public boolean hasProcessingEvidence(long sourceId, String sourceHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id
                  WHERE t.file_id=? AND t.source_content_hash=? AND t.load_status='SUCCESS' AND d.track_type='RAW')
                AND NOT EXISTS(SELECT 1 FROM gps_track t LEFT JOIN gps_track_data d
                  ON d.gps_track_id=t.id AND d.track_type='RAW'
                  WHERE t.file_id=? AND t.source_content_hash=? AND t.load_status='SUCCESS'
                  AND (d.processing_issues IS NULL OR d.processing_settings->>'assessment' IS NULL))
                """, Boolean.class, sourceId, sourceHash, sourceId, sourceHash));
    }

    public void applyToTracks(long sourceId, List<SourceSectionReview> sections) {
        Assessment assessment = find(sourceId).orElseThrow();
        Map<Long, ImportedRevision> importedRevisions = new HashMap<>();
        jdbc.query("SELECT id,source_content_hash,timing_estimated,timing_unreliable,review_excluded FROM gps_track WHERE file_id=?", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                importedRevisions.put(rs.getLong(1), new ImportedRevision(rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5))), sourceId);
        boolean staleDerived = false;
        List<Long> eligibilityChanges = new ArrayList<>();
        for (ReviewedTrack track : tracks(sourceId)) {
            ImportedRevision imported = importedRevisions.get(track.trackId());
            boolean stale = imported.hash() != null && !imported.hash().equals(assessment.sourceHash());
            staleDerived |= stale;
            var settings = SourceReviewPolicy.forTrack(assessment.diagnosis(), sections, track.sectionIndex(),
                    stale, imported.estimated(), imported.unreliable());
            ReviewState state = settings.state();
            boolean estimated = settings.estimated(), unreliable = settings.unreliable();
            boolean statisticsExcluded = settings.statisticsExcluded(), mapExcluded = settings.mapExcluded();
            int updated = jdbc.update("UPDATE gps_track SET review_status=?,review_excluded=?,review_map_excluded=?,timing_estimated=?,timing_unreliable=?,version=COALESCE(version,0)+1 WHERE id=? AND (review_status IS DISTINCT FROM ? OR review_excluded IS DISTINCT FROM ? OR review_map_excluded IS DISTINCT FROM ? OR timing_estimated IS DISTINCT FROM ? OR timing_unreliable IS DISTINCT FROM ?)",
                    state.name(), statisticsExcluded, mapExcluded, estimated, unreliable, track.trackId(), state.name(), statisticsExcluded, mapExcluded, estimated, unreliable);
            if (updated > 0 && (imported.withheld() || imported.unreliable()) != (statisticsExcluded || unreliable))
                eligibilityChanges.add(track.trackId());
        }
        invalidateDependentResults(sourceId, eligibilityChanges);
        setState(sourceId, staleDerived ? ReviewState.NOT_ASSESSED : combinedState(sections));
    }

    private void invalidateDependentResults(long sourceId, List<Long> changedIds) {
        if (changedIds.isEmpty()) return;
        long started = System.currentTimeMillis();
        log.info("Review dependency invalidation started: sourceId={} changedTracks={}", sourceId, changedIds.size());
        var named = new NamedParameterJdbcTemplate(jdbc);
        var parameters = new MapSqlParameterSource("changedIds", changedIds);
        List<Long> released = named.queryForList("""
                UPDATE gps_track SET duplicate_of=NULL,duplicate_status='NOT_CHECKED_YET',
                    exploration_score=NULL,exploration_status='SCHEDULED',version=COALESCE(version,0)+1
                WHERE duplicate_of IN (:changedIds) AND duplicate_status='DUPLICATE'
                RETURNING id
                """, parameters, Long.class);
        named.update("""
                UPDATE gps_track SET duplicate_of=NULL,
                    duplicate_status=CASE WHEN review_excluded OR timing_unreliable THEN 'UNIQUE' ELSE 'NOT_CHECKED_YET' END,
                    version=COALESCE(version,0)+1
                WHERE id IN (:changedIds) AND duplicate_status IS DISTINCT FROM 'EXCLUDED'
                """, parameters);
        named.update("""
                UPDATE gps_track SET exploration_score=NULL,
                    exploration_status=CASE WHEN review_excluded OR timing_unreliable THEN 'NOT_SCHEDULED' ELSE 'SCHEDULED' END,
                    version=COALESCE(version,0)+1
                WHERE id IN (:changedIds)
                """, parameters);
        List<Long> impacted = new ArrayList<>(changedIds);
        impacted.addAll(released);
        parameters.addValue("impactedIds", impacted).addValue("precision", explorationPrecisionMeters)
                .addValue("corridor", explorationCorridorMeters);
        // Include both sides of the date boundary: a finding may mean the source's stored date is wrong.
        int scores = named.update("""
                UPDATE gps_track dependent SET exploration_score=NULL,exploration_status='NEEDS_RECALCULATION',
                    version=COALESCE(dependent.version,0)+1
                WHERE dependent.id NOT IN (:impactedIds)
                  AND dependent.exploration_status IN ('CALCULATED','IN_PROGRESS')
                  AND NOT dependent.review_excluded AND NOT dependent.timing_unreliable
                  AND EXISTS (
                      SELECT 1 FROM gps_track_data changed JOIN gps_track_data other ON other.gps_track_id=dependent.id
                      WHERE changed.gps_track_id IN (:impactedIds)
                        AND changed.track_type='SIMPLIFIED_SHAPE' AND changed.precision_in_meter=:precision
                        AND other.track_type='SIMPLIFIED_SHAPE' AND other.precision_in_meter=:precision
                        AND other.track && ST_Transform(ST_Expand(ST_Envelope(ST_Transform(changed.observed_path,3857)),:corridor),4326)
                        AND ST_DWithin(ST_Transform(changed.observed_path,3857),ST_Transform(other.observed_path,3857),:corridor)
                  )
                """, parameters);
        log.info("Review dependency invalidation completed: sourceId={} changedTracks={} releasedDuplicates={} invalidatedScores={} durationMs={}",
                sourceId, changedIds.size(), released.size(), scores, System.currentTimeMillis() - started);
    }

    public boolean hasStaleDerivedRevision(long sourceId, String sourceHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM gps_track WHERE file_id=? AND source_content_hash IS NOT NULL AND source_content_hash<>?)", Boolean.class, sourceId, sourceHash));
    }

    public static boolean withheld(ReviewState state) {
        return state == ReviewState.EXCLUDED_STATISTICS || state == ReviewState.EXCLUDED;
    }

    public static boolean hiddenFromMap(ReviewState state) {
        return state == ReviewState.EXCLUDED_MAP || state == ReviewState.EXCLUDED;
    }

    public static ReviewState combinedState(List<SourceSectionReview> sections) {
        if (sections.stream().anyMatch(s -> s.state() == ReviewState.NEEDS_REVIEW)) return ReviewState.NEEDS_REVIEW;
        if (sections.stream().map(SourceSectionReview::state).distinct().count() > 1
            && sections.stream().anyMatch(s -> withheld(s.state()) || hiddenFromMap(s.state()))) return ReviewState.MIXED;
        if (sections.stream().anyMatch(s -> s.state() == ReviewState.NOT_ASSESSED)) return ReviewState.NOT_ASSESSED;
        if (!sections.isEmpty()) return sections.getFirst().state();
        return ReviewState.INCLUDED;
    }

    private TrackDiagnosis decode(String value) {
        try {
            return mapper.readValue(value, TrackDiagnosis.class);
        } catch (RuntimeException e) {
            log.error("Cannot read stored source diagnosis: type={}", e.getClass().getSimpleName(), e);
            throw e;
        }
    }
}
