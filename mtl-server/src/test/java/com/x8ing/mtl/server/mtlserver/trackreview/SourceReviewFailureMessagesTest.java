package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceReviewFailureMessagesTest {
    @Test
    void invalidDiagnosesDoNotHideOtherFilesAndIncompleteFindingsAreIgnored() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var mapper = new JsonMapper();
        var diagnoses = List.of(
                diagnosis(mapper, List.of(finding(TrackFinding.Reason.SOURCE_UNREADABLE, "Cannot read source"))),
                "invalid json", "null", "{}", diagnosis(mapper, null),
                diagnosis(mapper, Arrays.asList(null, finding(null, "Missing reason"),
                        finding(TrackFinding.Reason.PARSE_FAILED, "Cannot parse source"),
                        finding(TrackFinding.Reason.PARSE_FAILED, "Cannot parse source"),
                        finding(TrackFinding.Reason.POSITION_JUMP, "Not an import failure"),
                        finding(TrackFinding.Reason.NO_OBSERVATIONS, " "),
                        finding(TrackFinding.Reason.CONVERSION_FAILED, null))));
        doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(1);
            for (int index = 0; index < diagnoses.size(); index++) {
                var row = mock(ResultSet.class);
                when(row.getLong("source_id")).thenReturn((long) index + 1);
                when(row.getString("diagnosis_json")).thenReturn(diagnoses.get(index));
                callback.processRow(row);
            }
            return null;
        }).when(jdbc).query(any(PreparedStatementCreator.class), any(RowCallbackHandler.class));

        var messages = new SourceReviewRepository(jdbc, mapper, org.mockito.Mockito.mock(com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository.class))
                .failureMessages(List.of(1L, 2L, 3L, 4L, 5L, 6L));

        assertThat(messages).containsOnly(
                org.assertj.core.api.Assertions.entry(1L, "Cannot read source"),
                org.assertj.core.api.Assertions.entry(6L, "Cannot parse source"));
    }

    private static String diagnosis(JsonMapper mapper, List<TrackFinding> findings) {
        return mapper.writeValueAsString(new TrackDiagnosis("test", "synthetic", 0, false, false, List.of(), findings));
    }

    private static TrackFinding finding(TrackFinding.Reason reason, String message) {
        return new TrackFinding(TrackFinding.Stage.READ, reason, TrackFinding.Severity.REVIEW,
                -1, null, null, 0, null, null, message, List.of());
    }

    @Test
    void emptyPageDoesNotQueryDiagnoses() {
        var jdbc = mock(JdbcTemplate.class);
        assertThat(new SourceReviewRepository(jdbc, new JsonMapper(), new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository(jdbc, new JsonMapper())).failureMessages(List.of())).isEmpty();
        verifyNoInteractions(jdbc);
    }
}
