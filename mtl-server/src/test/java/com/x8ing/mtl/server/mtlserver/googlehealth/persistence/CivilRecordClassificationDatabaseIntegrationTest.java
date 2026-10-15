package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.PointMapper;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
@Transactional
class CivilRecordClassificationDatabaseIntegrationTest {
    @Autowired
    private MirrorRepository repository;
    @Autowired
    private PointMapper mapper;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;
    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @BeforeEach
    void emptyMirror() {
        repository.disconnect();
        repository.deleteMirrorRows();
    }

    @Test
    void aMappedCivilExerciseKeepsItsClockTimeDespiteItsDerivedCivilDate() {
        StoredPoint point = map("exercise", DataTypePolicy.Mode.RAW, """
                {"name":"users/me/dataTypes/exercise/dataPoints/synthetic-civil",
                 "exercise":{"interval":{
                   "civilStartTime":{"date":{"year":2048,"month":9,"day":5},"time":{"hours":12,"minutes":30}},
                   "civilEndTime":{"date":{"year":2048,"month":9,"day":5},"time":{"hours":13,"minutes":30}}
                 }}}
                """);
        assertThat(point.physicalStartTime()).isNull();
        assertThat(point.civilDate()).isEqualTo(LocalDate.parse("2048-09-05"));
        repository.upsertDataPoints(List.of(point), UUID.randomUUID());

        RecordRow listed = onlyRecord();
        assertThat(listed.temporalKind()).isEqualTo(RecordRow.TemporalKind.CIVIL);
        assertThat(listed.civilTime()).isEqualTo("2048-09-05T12:30");
        assertThat(repository.findRecord(listed.kind(), listed.id())).get()
                .extracting(RecordRow::temporalKind).isEqualTo(RecordRow.TemporalKind.CIVIL);
        assertThat(repository.findTypeSummaries()).singleElement().satisfies(summary -> {
            assertThat(summary.temporalKind()).isEqualTo(RecordRow.TemporalKind.CIVIL);
            assertThat(summary.latestCivilTime()).isEqualTo("2048-09-05T12:30:00");
        });
    }

    @Test
    void mappedDailyPolicyAndRollupRecordsRemainCalendarDates() {
        StoredPoint daily = map("daily-vo2-max", DataTypePolicy.Mode.RAW, """
                {"name":"users/me/dataTypes/daily-vo2-max/dataPoints/synthetic-daily",
                 "dailyVo2Max":{"date":{"year":2048,"month":9,"day":5},"vo2Max":45}}
                """);
        StoredPoint rollup = map("steps", DataTypePolicy.Mode.DAILY_ROLLUP, """
                {"civilStartTime":{"date":{"year":2048,"month":9,"day":5}},
                 "civilEndTime":{"date":{"year":2048,"month":9,"day":6}},"steps":{"countSum":"250"}}
                """);
        repository.upsertDataPoints(List.of(daily, rollup), UUID.randomUUID());

        assertThat(repository.findRecords(null, null, null, null, null, null, null, null, 10))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.temporalKind()).isEqualTo(RecordRow.TemporalKind.CALENDAR_DATE);
                    assertThat(row.civilDate()).isEqualTo(LocalDate.parse("2048-09-05"));
                    assertThat(repository.findRecord(row.kind(), row.id())).get()
                            .extracting(RecordRow::temporalKind).isEqualTo(RecordRow.TemporalKind.CALENDAR_DATE);
                });
        assertThat(repository.findTypeSummaries()).hasSize(2)
                .allSatisfy(summary -> assertThat(summary.temporalKind()).isEqualTo(RecordRow.TemporalKind.CALENDAR_DATE));
    }

    private StoredPoint map(String dataType, DataTypePolicy.Mode mode, String payload) {
        var policy = DataTypePolicy.all().stream().filter(value ->
                value.dataType().equals(dataType) && value.mode() == mode).findFirst().orElseThrow();
        return mapper.mapPoint(policy, json.readTree(payload), null);
    }

    private RecordRow onlyRecord() {
        var rows = repository.findRecords(null, null, null, null, null, null, null, null, 10);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
