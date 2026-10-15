package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncWindowPolicyTest {
    private static DataTypePolicy.Policy policy(String type, DataTypePolicy.Mode mode) {
        return DataTypePolicy.all().stream().filter(p -> p.dataType().equals(type) && p.mode() == mode).findFirst().orElseThrow();
    }

    @Test
    void fastEnvelopeCoversBothCivilOffsetExtremes() {
        var p = new ConnectorProperties();
        var now = Instant.parse("2026-10-08T12:00:00.123456789Z");
        var physical = SyncWindowPolicy.recent(policy("sleep", DataTypePolicy.Mode.RAW), p, now, true);
        var civil = SyncWindowPolicy.recent(policy("exercise", DataTypePolicy.Mode.RAW), p, now, true);
        var date = SyncWindowPolicy.recent(policy("steps", DataTypePolicy.Mode.DAILY_ROLLUP), p, now, true);
        assertThat(physical.start()).isEqualTo(Instant.parse("2026-10-07T12:00:00.123456Z"));
        assertThat(civil.start()).isEqualTo(Instant.parse("2026-10-07T00:00:00.123456Z"));
        assertThat(civil.end()).isEqualTo(Instant.parse("2026-10-09T02:00:00.123456Z"));
        assertThat(date.start()).isEqualTo(Instant.parse("2026-10-07T00:00:00Z"));
        assertThat(date.end()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        assertThat(SyncWindowPolicy.cutoff(p, now)).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    @Test
    void subdayFastWindowsRetainWholeDateCellsAndEndpointCaps() {
        var p = new ConnectorProperties();
        p.setRecentFastWindow(Duration.ofHours(6));
        p.setRecentWindow(Period.ofDays(120));
        p.validateSynchronization();
        var now = Instant.parse("2026-10-08T22:00:00Z");
        for (var policy : DataTypePolicy.all()) {
            var bounds = SyncWindowPolicy.recent(policy, p, now, false);
            var cells = SyncWindowPolicy.cells(policy, bounds);
            assertThat(cells).allSatisfy(cell -> assertThat(Duration.between(cell.start(), cell.end())).isLessThanOrEqualTo(Duration.ofDays(policy.maxWindowDays())));
            assertThat(cells.getFirst().end()).isEqualTo(bounds.end());
            assertThat(cells.getLast().start()).isEqualTo(bounds.start());
        }
    }

    @Test
    void rejectsInvalidConfigurationAndAcceptsExplicit1970Floor() {
        var p = new ConnectorProperties();
        p.setRecentWindow(Period.ofDays(1));
        assertThatThrownBy(p::validateSynchronization).isInstanceOf(IllegalArgumentException.class);
        p.setRecentWindow(Period.ofDays(7));
        p.setRecentFastWindow(Duration.ofMinutes(1));
        assertThatThrownBy(p::validateSynchronization).isInstanceOf(IllegalArgumentException.class);
        p.setRecentFastWindow(Duration.ofHours(6));
        p.setCycleDeadlineMargin(p.getSyncInterval());
        assertThatThrownBy(p::validateSynchronization).isInstanceOf(IllegalArgumentException.class);
        p.setCycleDeadlineMargin(Duration.ofMinutes(1));
        p.setHistoryBudgetFraction(Double.NaN);
        assertThatThrownBy(p::validateSynchronization).isInstanceOf(IllegalArgumentException.class);
        p.setHistoryBudgetFraction(.5);
        p.setHistoryStartDate(LocalDate.of(1970, 1, 1));
        p.validateSynchronization();
        assertThat(p.getHistoryStartDate()).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    void civilDayCompletionWaitsForUtcMinus12() {
        assertThat(SyncWindowPolicy.completion(policy("exercise", DataTypePolicy.Mode.RAW), LocalDate.of(2026, 10, 1)))
                .isEqualTo(Instant.parse("2026-10-02T12:00:00Z"));
    }
}
