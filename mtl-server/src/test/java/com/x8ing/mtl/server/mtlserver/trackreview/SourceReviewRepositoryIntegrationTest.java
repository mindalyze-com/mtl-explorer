package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Real SQL against an explicitly selected disposable database; each fixture transaction is rolled back.
 */
@EnabledIfSystemProperty(named = "mtl.test.review.jdbc-url", matches = "jdbc:postgresql:.*")
class SourceReviewRepositoryIntegrationTest {
    @Test
    void cosmeticFindingChangesRetainAcknowledgementAndNewDefectsPreserveUseSettings() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            var source = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.source(SyntheticTrackSources.regular(60))));
            var warning = new TrackFinding(TrackFinding.Stage.POSITION, TrackFinding.Reason.POSITION_CORRECTION,
                    TrackFinding.Severity.REVIEW, 0, 10, 10, 1, 253.055634892074, "metres", "Moved position", List.of("Map", "Distance"));
            var original = TrackProcessingDiagnosticEngine.withFindings(source, List.of(warning));
            var cosmeticWarning = new TrackFinding(warning.stage(), warning.reason(), warning.severity(), 0, 10, 10, 1,
                    253.055634892075, "metres", "Position moved", List.of("Distance", "Map"));
            var information = new TrackFinding(TrackFinding.Stage.ELEVATION, TrackFinding.Reason.ELEVATION_MISSING,
                    TrackFinding.Severity.INFORMATION, 0, null, null, 60, null, null, "Altitude unavailable", List.of("Ascent"));
            var cosmetic = TrackProcessingDiagnosticEngine.withFindings(source, List.of(information, cosmeticWarning));
            var newWarning = new TrackFinding(TrackFinding.Stage.POSITION, TrackFinding.Reason.POSITION_JUMP,
                    TrackFinding.Severity.REVIEW, 0, 23, 24, 1, 1064.0, "metres", "Unsupported transition", List.of("Distance"));
            var changed = TrackProcessingDiagnosticEngine.withFindings(source, List.of(cosmeticWarning, information, newWarning));
            long sourceId = source(jdbc), trackId = track(jdbc, sourceId, original.sourceHash(), 0);
            int excludedBefore = repository.queue(null, "", null, null, null, 0, 0).excludedCount();
            var before = new SourceReviewRepository.Assessment(sourceId, original.sourceHash(), original, Instant.now());
            var afterCosmetic = new SourceReviewRepository.Assessment(sourceId, original.sourceHash(), cosmetic, Instant.now());
            var afterNewDefect = new SourceReviewRepository.Assessment(sourceId, original.sourceHash(), changed, Instant.now());
            for (Decision decision : List.of(Decision.INCLUDE, Decision.EXCLUDE_MAP, Decision.EXCLUDE_STATISTICS, Decision.EXCLUDE)) {
                repository.save(sourceId, original.sourceHash(), original, ReviewState.NEEDS_REVIEW);
                repository.decide(before, 0, decision);
                assertEquals(decision, repository.useDecisions(afterCosmetic).get(0));
                repository.save(sourceId, original.sourceHash(), cosmetic, ReviewState.NEEDS_REVIEW);
                repository.applyToTracks(sourceId, service.sections(afterCosmetic));
                assertNotEquals(ReviewState.NEEDS_REVIEW, service.sections(afterCosmetic).getFirst().state());
                repository.save(sourceId, original.sourceHash(), changed, ReviewState.NEEDS_REVIEW);
                assertTrue(repository.hasPendingIssues(sourceId));
                assertEquals(decision, service.sections(afterNewDefect).getFirst().useDecision());
                assertNotEquals(ReviewState.NEEDS_REVIEW, service.sections(afterNewDefect).getFirst().state());
                repository.applyToTracks(sourceId, service.sections(afterNewDefect));
                assertEquals(excludedBefore + (decision == Decision.INCLUDE ? 0 : 1),
                        repository.queue(null, "", null, null, null, 0, 0).excludedCount());
                assertEquals(decision == Decision.EXCLUDE || decision == Decision.EXCLUDE_STATISTICS,
                        jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
                assertEquals(decision == Decision.EXCLUDE || decision == Decision.EXCLUDE_MAP,
                        jdbc.queryForObject("SELECT review_map_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
                repository.decide(afterNewDefect, 0, Decision.LATER);
                assertEquals(decision, repository.useDecisions(afterNewDefect).get(0));
                repository.applyToTracks(sourceId, service.sections(afterNewDefect));
                assertEquals(decision == Decision.EXCLUDE || decision == Decision.EXCLUDE_STATISTICS,
                        jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
            }
            var replacement = new SourceReviewRepository.Assessment(sourceId, "changed-source-revision", changed, Instant.now());
            assertTrue(repository.useDecisions(replacement).isEmpty());
        });
    }

    @Test
    void informationIsCountedSeparatelyFromReviewFindings() {
        inDatabase(jdbc -> {
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.source(SyntheticTrackSources.regular(60))));
            var info = new TrackFinding(TrackFinding.Stage.ELEVATION, TrackFinding.Reason.ELEVATION_MISSING,
                    TrackFinding.Severity.INFORMATION, 0, null, null, 60, null, null, "No elevation", List.of("Ascent"));
            var warning = new TrackFinding(TrackFinding.Stage.POSITION, TrackFinding.Reason.POSITION_JUMP,
                    TrackFinding.Severity.REVIEW, 0, 23, 24, 1, 1064.0, "metres", "Unsupported transition", List.of("Distance"));
            diagnosis = TrackProcessingDiagnosticEngine.withFindings(diagnosis, List.of(info, warning));
            long sourceId = source(jdbc);
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.NEEDS_REVIEW);
            String name = jdbc.queryForObject("SELECT name FROM indexed_file WHERE id=?", String.class, sourceId);
            var item = repository.queue(null, name, null, null, null, 0, 50).sources().getFirst();
            assertEquals(2, item.findingCount());
            assertEquals(1, item.reviewFindingCount());
            assertEquals(1, item.informationFindingCount());
            assertEquals(List.of("POSITION_JUMP"), item.reviewReasons());
            assertEquals(List.of("ELEVATION_MISSING"), item.informationReasons());
        });
    }

    @Test
    void fourUseChoicesIndependentlyControlMapQueriesAndStatisticsAndRemainReversible() {
        inDatabase(jdbc -> {
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.source(SyntheticTrackSources.regular(60))));
            long sourceId = source(jdbc), id = spatialTrack(jdbc, sourceId, diagnosis.sourceHash(), -140.0);
            jdbc.update("UPDATE gps_track SET track_length_in_meter=1000,track_duration_in_motion_secs=100 WHERE id=?", id);
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            var assessment = new SourceReviewRepository.Assessment(sourceId, diagnosis.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.INCLUDED);
            for (Decision decision : List.of(Decision.INCLUDE, Decision.EXCLUDE_MAP, Decision.EXCLUDE_STATISTICS, Decision.EXCLUDE, Decision.INCLUDE)) {
                repository.decide(assessment, 0, decision);
                repository.applyToTracks(sourceId, service.sections(assessment));
                boolean mapHidden = decision == Decision.EXCLUDE_MAP || decision == Decision.EXCLUDE;
                boolean statisticsHidden = decision == Decision.EXCLUDE_STATISTICS || decision == Decision.EXCLUDE;
                assertEquals(mapHidden, jdbc.queryForObject("SELECT review_map_excluded FROM gps_track WHERE id=?", Boolean.class, id));
                assertEquals(statisticsHidden, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, id));
                var totals = jdbc.queryForMap(overviewQuery("getTrackOverviewSummary", List.of(id), Long[].class));
                assertEquals(statisticsHidden ? 0L : 1L, ((Number) totals.get("trackcount")).longValue(), decision.name());
                try {
                    String nearbySql = com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository.class
                            .getMethod("getTracksWithinDistanceToPoint", double.class, double.class, double.class, Long[].class)
                            .getAnnotation(org.springframework.data.jpa.repository.Query.class).value()
                            .replace(":longitude", "-140.0").replace(":latitude", "-55.0").replace(":distanceInMeter", "100.0")
                            .replace(":filterIds", "ARRAY[" + id + "]::bigint[]");
                    assertEquals(mapHidden ? List.of() : List.of(id), jdbc.queryForList(nearbySql, Long.class), decision.name());
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                assertEquals(60, repository.find(sourceId).orElseThrow().diagnosis().pointCount());
            }
        });
    }

    @Test
    void reviewEligibilityChangesReleaseDuplicatesAndInvalidateOnlyNearbyExplorationScores() {
        inDatabase(jdbc -> {
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.source(SyntheticTrackSources.regular(60))));
            long sourceId = source(jdbc), otherSource = source(jdbc);
            long suspect = spatialTrack(jdbc, sourceId, diagnosis.sourceHash(), -140.0);
            long duplicate = spatialTrack(jdbc, otherSource, "healthy-copy", -140.0);
            long nearby = spatialTrack(jdbc, otherSource, "nearby", -139.9999);
            long distant = spatialTrack(jdbc, otherSource, "distant", -120.0);
            jdbc.update("UPDATE gps_track SET duplicate_status='DUPLICATE',duplicate_of=? WHERE id=?", suspect, duplicate);
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.INCLUDED);
            var excluded = List.of(new SourceSectionReview(0, "Synthetic", ReviewState.EXCLUDED, Decision.EXCLUDE, false, false, 0, 60));
            repository.applyToTracks(sourceId, excluded);
            assertEquals(true, jdbc.queryForObject("SELECT review_excluded AND exploration_score IS NULL AND exploration_status='NOT_SCHEDULED' FROM gps_track WHERE id=?", Boolean.class, suspect));
            assertEquals(true, jdbc.queryForObject("SELECT duplicate_of IS NULL AND duplicate_status='NOT_CHECKED_YET' AND exploration_score IS NULL AND exploration_status='SCHEDULED' FROM gps_track WHERE id=?", Boolean.class, duplicate));
            assertEquals(true, jdbc.queryForObject("SELECT exploration_score IS NULL AND exploration_status='NEEDS_RECALCULATION' FROM gps_track WHERE id=?", Boolean.class, nearby));
            assertEquals(true, jdbc.queryForObject("SELECT exploration_score=0.75 AND exploration_status='CALCULATED' FROM gps_track WHERE id=?", Boolean.class, distant));
            // Repeating the same assessment must not discard a freshly recalculated neighbor again.
            jdbc.update("UPDATE gps_track SET exploration_score=0.9,exploration_status='CALCULATED' WHERE id=?", nearby);
            repository.applyToTracks(sourceId, excluded);
            assertEquals(0.9, jdbc.queryForObject("SELECT exploration_score FROM gps_track WHERE id=?", Double.class, nearby));
            jdbc.update("UPDATE gps_track SET duplicate_status='EXCLUDED' WHERE id=?", suspect);
            repository.applyToTracks(sourceId, List.of(new SourceSectionReview(0, "Synthetic", ReviewState.INCLUDED, Decision.INCLUDE, false, false, 0, 60)));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND exploration_status='SCHEDULED' AND duplicate_status='EXCLUDED' FROM gps_track WHERE id=?", Boolean.class, suspect));
            assertNull(jdbc.queryForObject("SELECT exploration_score FROM gps_track WHERE id=?", Double.class, nearby));
        });
    }

    @Test
    void withheldAndUnsupportedRecordingsDoNotEstablishExplorationHistory() {
        inDatabase(jdbc -> {
            long sourceId = source(jdbc);
            long earlier = spatialTrack(jdbc, sourceId, "earlier", -130.0);
            long later = spatialTrack(jdbc, sourceId, "later", -130.0);
            jdbc.update("UPDATE gps_track SET start_date=timestamp '2034-03-21 10:00:00' WHERE id=?", later);
            var query = new com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreQueryRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
            assertEquals(0.0, query.calculateExplorationScore(later, null, 25, 10).getExplorationScore());
            jdbc.update("UPDATE gps_track SET review_excluded=true WHERE id=?", earlier);
            assertEquals(1.0, query.calculateExplorationScore(later, null, 25, 10).getExplorationScore());
            assertNull(query.calculateExplorationScore(earlier, null, 25, 10));
            jdbc.update("UPDATE gps_track SET review_excluded=false,timing_unreliable=true,timing_estimated=true WHERE id=?", earlier);
            assertEquals(1.0, query.calculateExplorationScore(later, null, 25, 10).getExplorationScore());
            assertNull(query.calculateExplorationScore(earlier, null, 25, 10));
            jdbc.update("UPDATE gps_track SET timing_unreliable=false,timing_estimated=false WHERE id=?", earlier);
            assertEquals(0.0, query.calculateExplorationScore(later, null, 25, 10).getExplorationScore());
        });
    }

    private static long spatialTrack(JdbcTemplate jdbc, long sourceId, String hash, double longitude) {
        long id = track(jdbc, sourceId, hash, 0);
        jdbc.update("UPDATE gps_track SET start_date=timestamp '2034-03-20 10:00:00',load_status='SUCCESS',duplicate_status='UNIQUE',exploration_score=0.75,exploration_status='CALCULATED' WHERE id=?", id);
        String shape = "LINESTRING(" + longitude + " -55," + (longitude + 0.001) + " -54.999)";
        jdbc.update("INSERT INTO gps_track_data(gps_track_id,track_type,precision_in_meter,track,observed_path) VALUES(?,'SIMPLIFIED_SHAPE',10,ST_GeomFromText(?,4326),ST_GeomFromText(?,4326))", id, shape, shape);
        // Real imports maintain source bounds alongside the geometry.
        jdbc.update("UPDATE gps_track SET bbox_min_lng=?,bbox_max_lng=?,bbox_min_lat=-55,bbox_max_lat=-54.999 WHERE id=?",
                longitude, longitude + 0.001, id);
        return id;
    }

    @Test
    void summariesRankingsAndRecentActivitiesApplyReviewAndMetricSupportWithoutChangingManualScopes() {
        inDatabase(jdbc -> {
            long sourceId = source(jdbc);
            jdbc.update("INSERT INTO source_issue(source_id,source_revision,finding_hash,stage,reason,severity,message) VALUES(?,'sha256:synthetic','synthetic','CLOCK','TIME_MISSING','REVIEW','Synthetic timing finding')", sourceId);
            var ids = new java.util.ArrayList<Long>();
            for (int i = 0; i < 5; i++) {
                long id = track(jdbc, sourceId, "synthetic-metrics", 0);
                ids.add(id);
                jdbc.update("UPDATE gps_track SET start_date=timestamp '2032-02-18 08:00:00',end_date=timestamp '2032-02-18 09:00:00',load_status='SUCCESS',duplicate_status='UNIQUE',track_length_in_meter=1000,track_duration_in_motion_secs=1000,energy_net_total_wh=100,elevation_gain_per_hour_30s_max=?,speed_in_kmh_30s_max=20,power_watts_30s_max=200 WHERE id=?", i == 1 ? 9000 : 300, id);
            }
            jdbc.update("UPDATE gps_track SET timing_unreliable=true,timing_estimated=true WHERE id=?", ids.get(1));
            jdbc.update("UPDATE gps_track SET timing_unreliable=true,review_excluded=true,review_status='NEEDS_REVIEW' WHERE id=?", ids.get(2));
            jdbc.update("UPDATE gps_track SET statistics_exclusion_reason='OTHER' WHERE id=?", ids.get(3));
            jdbc.update("UPDATE gps_track SET highlight_exclusion_reason='OTHER' WHERE id=?", ids.get(4));
            var summary = jdbc.queryForMap(overviewQuery("getTrackOverviewSummary", ids, Long[].class));
            assertEquals(3L, ((Number) summary.get("trackcount")).longValue());
            assertEquals(3000.0, ((Number) summary.get("distancem")).doubleValue());
            assertEquals(2_000_000.0, ((Number) summary.get("durationms")).doubleValue());
            assertEquals(200.0, ((Number) summary.get("energywh")).doubleValue());
            var rankings = jdbc.queryForList(overviewQuery("getTrackOverviewTrackRankings", ids, Long[].class, int.class));
            var ascent = rankings.stream().filter(row -> "quickest-ascent".equals(row.get("rowkey"))).toList();
            assertEquals(1, ascent.size());
            assertEquals(ids.getFirst(), ((Number) ascent.getFirst().get("trackid")).longValue());
            var recent = jdbc.queryForList(overviewQuery("getTrackOverviewRecentActivities", ids, Long[].class));
            assertEquals(java.util.Set.of(ids.get(0), ids.get(1), ids.get(4)), recent.stream().map(row -> ((Number) row.get("trackid")).longValue()).collect(java.util.stream.Collectors.toSet()));
            var excluded = jdbc.queryForMap(overviewQuery("getTrackOverviewExclusions", ids, Long[].class, String.class));
            assertEquals(1L, ((Number) excluded.get("reviewwithheldtrackcount")).longValue());
            assertEquals(1L, ((Number) excluded.get("reviewpendingsourcecount")).longValue());
            assertEquals(1L, ((Number) excluded.get("statisticsexcludedtrackcount")).longValue());
            assertEquals(2L, ((Number) excluded.get("highlightexcludedtrackcount")).longValue());
        });
    }

    private static String overviewQuery(String method, List<Long> ids, Class<?>... types) {
        try {
            String sql = com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository.class.getMethod(method, types)
                    .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
            return sql.replace(":filterIds", "ARRAY[" + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]::bigint[]")
                    .replace(":limit", "5").replace(":account", "'reviewer'");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing statistics query contract", error);
        }
    }

    @Test
    void newSourceWideProcessingFindingRequiresFreshSectionDecisions() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            var original = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.document(
                    SyntheticTrackSources.section(SyntheticTrackSources.regular(200), 0),
                    SyntheticTrackSources.section(SyntheticTrackSources.regular(100), 1000))));
            long sourceId = source(jdbc), first = track(jdbc, sourceId, original.sourceHash(), 0), second = track(jdbc, sourceId, original.sourceHash(), 1);
            var before = new SourceReviewRepository.Assessment(sourceId, original.sourceHash(), original, Instant.now());
            repository.save(sourceId, original.sourceHash(), original, ReviewState.INCLUDED);
            repository.decide(before, 0, Decision.INCLUDE);
            var diagnosis = new TrackProcessingDiagnosticEngine().diagnose(original, List.of(new ProcessedRecording(
                    original.sourceHash(), null, Instant.EPOCH, Instant.EPOCH.plusSeconds(20), 20.0, 10.0)));
            var after = new SourceReviewRepository.Assessment(sourceId, original.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, original.sourceHash(), diagnosis, ReviewState.NEEDS_REVIEW);
            assertTrue(repository.hasPendingIssues(sourceId), "Use settings do not acknowledge source issues.");
            repository.applyToTracks(sourceId, service.sections(after));
            assertTimingOnly(jdbc, first);
            assertTimingOnly(jdbc, second);
            repository.decide(after, 0, Decision.INCLUDE);
            repository.applyToTracks(sourceId, service.sections(after));
            assertEquals(false, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, first));
            assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, first));
            assertTimingOnly(jdbc, second);
        });
    }

    @Test
    void reviewFiltersMatchReasonAndSeverityTogetherAndLatestDecisionOnCurrentVersion() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            long sourceId = source(jdbc);
            String name = jdbc.queryForObject("SELECT name FROM indexed_file WHERE id=?", String.class, sourceId);
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.alternatingClockDates()));
            var assessment = new SourceReviewRepository.Assessment(sourceId, diagnosis.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.NEEDS_REVIEW);
            assertEquals(1, repository.queue(null, name, TrackFinding.Reason.TIME_BACKWARD, TrackFinding.Severity.REVIEW, null, 0, 50).sources().size());
            assertEquals(1, repository.queue(null, name, TrackFinding.Reason.TIME_MISSING, TrackFinding.Severity.REVIEW, null, 0, 50).sources().size());
            assertTrue(repository.queue(null, name, TrackFinding.Reason.TIME_MISSING, TrackFinding.Severity.INFORMATION, null, 0, 50).sources().isEmpty());
            assertTrue(repository.queue(null, name, null, null, Decision.INCLUDE, 0, 50).sources().isEmpty());
            repository.decide(assessment, 0, Decision.INCLUDE);
            assertEquals(1, repository.queue(null, name, null, null, Decision.INCLUDE, 0, 50).sources().size());
            repository.decide(assessment, 0, Decision.LATER);
            assertEquals(1, repository.queue(null, name, null, null, Decision.INCLUDE, 0, 50).sources().size());
            assertTrue(repository.queue(null, name, null, null, Decision.LATER, 0, 50).sources().isEmpty());
            repository.save(sourceId, "new-revision", diagnosis, ReviewState.NEEDS_REVIEW);
            assertTrue(repository.queue(null, name, null, null, Decision.LATER, 0, 50).sources().isEmpty());
        });
    }

    @Test
    void incompleteAssessmentPreservesInclusionAndExistingMetricRestrictionsWhileAllowingExplicitExclusion() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            long sourceId = source(jdbc);
            long healthy = track(jdbc, sourceId, "large-revision", 0);
            long restricted = track(jdbc, sourceId, "large-revision", 1);
            jdbc.update("UPDATE gps_track SET timing_estimated=true,timing_unreliable=true WHERE id=?", restricted);
            var diagnosis = new TrackDiagnosis("synthetic", "large-revision", 0, false, false, List.of(), List.of(
                    new TrackFinding(TrackFinding.Stage.READ, TrackFinding.Reason.ASSESSMENT_LIMIT,
                            TrackFinding.Severity.WARNING, -1, null, null, 0, null, null, "Review incomplete", List.of())));
            var assessment = new SourceReviewRepository.Assessment(sourceId, "large-revision", diagnosis, Instant.now());
            repository.save(sourceId, "large-revision", diagnosis, ReviewState.NOT_ASSESSED);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertWithheld(jdbc, healthy, false);
            assertEquals(false, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, restricted));
            assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, restricted));
            assertTrue(repository.queue(ReviewState.NOT_ASSESSED, "", null, null, null, 0, 100).sources().stream().anyMatch(s -> s.sourceId() == sourceId));
            repository.decide(assessment, -1, Decision.EXCLUDE);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertEquals(true, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, healthy));
            assertEquals(true, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, restricted));
            repository.decide(assessment, -1, Decision.INCLUDE);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertWithheld(jdbc, healthy, false);
            assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, restricted));
        });
    }

    @Test
    void unsupportedTimingDoesNotBiasPowerMediansOrExplorationAverages() throws Exception {
        String template = com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository.class
                .getMethod("getTrackStatistics", String.class, String.class, Long[].class, Double.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        inDatabase(jdbc -> {
            long sourceId = source(jdbc);
            jdbc.update("INSERT INTO source_issue(source_id,source_revision,finding_hash,stage,reason,severity,message) VALUES(?,'sha256:synthetic','synthetic','CLOCK','TIME_MISSING','REVIEW','Synthetic timing finding')", sourceId);
            var ids = new java.util.ArrayList<Long>();
            for (int i = 1; i <= 4; i++) {
                long id = track(jdbc, sourceId, "synthetic-revision", 0);
                ids.add(id);
                jdbc.update("UPDATE gps_track SET start_date=timestamp '2032-02-18 08:00:00',end_date=timestamp '2032-02-18 09:00:00',load_status='SUCCESS',duplicate_status='UNIQUE',track_duration_in_motion_secs=3600,power_watts_avg=?,normalized_power_watts=?,timing_unreliable=? WHERE id=?", i * 10, i * 10, i == 4, id);
                jdbc.update("UPDATE gps_track SET exploration_status='CALCULATED',exploration_score=? WHERE id=?", i / 10.0, id);
            }
            String selected = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            String sql = template.replace(":filterIds", "ARRAY[" + selected + "]::bigint[]")
                    .replace(":group_by_date_format", "'YYYY-MM'").replace(":filter_value", "NULL").replace(":threshold_power", "250.0");
            var summary = jdbc.queryForMap(sql);
            assertEquals(4L, ((Number) summary.get("numberoftracks")).longValue());
            assertEquals(20.0, ((Number) summary.get("powerwattsavgmed")).doubleValue());
            assertEquals(20.0, ((Number) summary.get("normalizedpowermed")).doubleValue());
            assertEquals(10800.0, ((Number) summary.get("totaltrackdurationsecs")).doubleValue());
            assertEquals(0.2, ((Number) summary.get("explorationscoreavg")).doubleValue());
        });
    }

    @Test
    void acceptingANewSourceRevisionCannotReleaseOldDerivedMetrics() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            long sourceId = source(jdbc);
            String oldHash = GpxDocument.hash(SyntheticTrackSources.alternatingClockDates());
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.source(SyntheticTrackSources.regular(20))));
            long trackId = track(jdbc, sourceId, oldHash, 0);
            var assessment = new SourceReviewRepository.Assessment(sourceId, diagnosis.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.INCLUDED);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertTrue(repository.hasStaleDerivedRevision(sourceId, diagnosis.sourceHash()));
            assertTimingOnly(jdbc, trackId);
            repository.decide(assessment, 0, Decision.INCLUDE);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertTimingOnly(jdbc, trackId);
            assertEquals("NOT_ASSESSED", jdbc.queryForObject("SELECT review_state FROM track_source_review WHERE source_id=?", String.class, sourceId));

            // Model successful replacement of the derived rows with the actual assessed source revision.
            jdbc.update("UPDATE gps_track SET source_content_hash=? WHERE id=?", diagnosis.sourceHash(), trackId);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertWithheld(jdbc, trackId, false);
            assertEquals("INCLUDED", jdbc.queryForObject("SELECT review_state FROM track_source_review WHERE source_id=?", String.class, sourceId));
        });
    }

    @Test
    void acceptingSeriousTimingFindingsKeepsUnsupportedMetricsAndManualExclusions() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            long sourceId = source(jdbc);
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(SyntheticTrackSources.alternatingClockDates()));
            long trackId = track(jdbc, sourceId, diagnosis.sourceHash(), 0);
            jdbc.update("UPDATE gps_track SET statistics_exclusion_reason='User exclusion' WHERE id=?", trackId);
            var assessment = new SourceReviewRepository.Assessment(sourceId, diagnosis.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.NEEDS_REVIEW);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertTimingOnly(jdbc, trackId);
            repository.decide(assessment, 0, Decision.INCLUDE);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertEquals(false, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
            assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, trackId));
            assertEquals("User exclusion", jdbc.queryForObject("SELECT statistics_exclusion_reason FROM gps_track WHERE id=?", String.class, trackId));
            repository.decide(assessment, 0, Decision.LATER);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertTimingOnly(jdbc, trackId);
        });
    }

    @Test
    void sectionDecisionLeavesTheHealthySectionIncluded() {
        inDatabase(jdbc -> {
            var repository = new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper()));
            var service = new SourceReviewService(mock(TrackSourceAccess.class), repository, directTransactions());
            var badTimes = SyntheticTrackSources.regular(100);
            for (int i = 10; i < 60; i += 2) badTimes.set(i, badTimes.get(i).minusSeconds(86400));
            byte[] source = SyntheticTrackSources.document(SyntheticTrackSources.section(badTimes, 0), SyntheticTrackSources.section(SyntheticTrackSources.regular(20), 200));
            var diagnosis = new TrackDiagnosticEngine().diagnose(GpxDocument.parse(source));
            long sourceId = source(jdbc), bad = track(jdbc, sourceId, diagnosis.sourceHash(), 0), healthy = track(jdbc, sourceId, diagnosis.sourceHash(), 1);
            var assessment = new SourceReviewRepository.Assessment(sourceId, diagnosis.sourceHash(), diagnosis, Instant.now());
            repository.save(sourceId, diagnosis.sourceHash(), diagnosis, ReviewState.NEEDS_REVIEW);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertTimingOnly(jdbc, bad);
            assertWithheld(jdbc, healthy, false);
            repository.decide(assessment, 0, Decision.EXCLUDE);
            repository.applyToTracks(sourceId, service.sections(assessment));
            assertWithheld(jdbc, bad, true);
            assertWithheld(jdbc, healthy, false);
        });
    }

    private static void assertTimingOnly(JdbcTemplate jdbc, long trackId) {
        assertEquals(false, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
        assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, trackId));
    }

    private static void assertWithheld(JdbcTemplate jdbc, long trackId, boolean value) {
        assertEquals(value, jdbc.queryForObject("SELECT review_excluded FROM gps_track WHERE id=?", Boolean.class, trackId));
        assertEquals(value, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE id=?", Boolean.class, trackId));
    }

    private static long source(JdbcTemplate jdbc) {
        return jdbc.queryForObject("INSERT INTO indexed_file(\"index\",name,path,full_path,indexer_status) VALUES('GPS',?,'/synthetic','/synthetic/review.gpx','COMPLETED_WITH_SUCCESS') RETURNING id", Long.class, "synthetic-review-" + UUID.randomUUID() + ".gpx");
    }

    private static long track(JdbcTemplate jdbc, long sourceId, String hash, int section) {
        return jdbc.queryForObject("INSERT INTO gps_track(file_id,version,track_source,source_content_hash,source_recording_section,track_name) VALUES(?,0,'IMPORTED',?,?,'Synthetic review track') RETURNING id", Long.class, sourceId, hash, section);
    }

    private static void inDatabase(Consumer<JdbcTemplate> assertions) {
        var dataSource = new DriverManagerDataSource(System.getProperty("mtl.test.review.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            try {
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("reviewer", "", List.of()));
                assertions.accept(new JdbcTemplate(dataSource));
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                status.setRollbackOnly();
            }
        });
    }
}
