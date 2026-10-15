package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterParamResolverTest {
    private final FilterParamResolver resolver = new FilterParamResolver();

    @Test
    void normalizesOffsetsAndPreservesFractionalSeconds() {
        FilterParamsRequest params = new FilterParamsRequest();
        params.setDateTimeParams(Map.of(
                "DATE_TIME_FROM", "2025-06-15T12:00:00.123+02:00",
                "DATE_TIME_TO", "2025-01-15T12:01:00+01:00"));

        assertThat(resolver.expand(params)).containsAllEntriesOf(Map.of(
                "DATE_TIME_FROM", "2025-06-15T10:00:00.123Z",
                "DATE_TIME_TO", "2025-01-15T11:01:00Z"));
    }

    @Test
    void treatsNullAndBlankBoundsAsAbsent() {
        FilterParamsRequest params = new FilterParamsRequest();
        Map<String, String> dates = new HashMap<>();
        dates.put("DATE_TIME_FROM", null);
        dates.put("DATE_TIME_TO", " ");
        params.setDateTimeParams(dates);

        assertThat(resolver.expand(params)).containsEntry("DATE_TIME_FROM", null)
                .containsEntry("DATE_TIME_TO", null);
    }

    @Test
    void rejectsDatesWithoutOffsetsInsteadOfGuessingTheServerTimezone() {
        for (String value : new String[]{"2025-06-15 12:00:00", "2025-06-15T12:00:00", "invalid"}) {
            FilterParamsRequest params = new FilterParamsRequest();
            params.setDateTimeParams(Map.of("DATE_TIME_FROM", value));

            assertThatThrownBy(() -> resolver.expand(params))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(exception.getReason()).contains("DATE_TIME_FROM", "explicit offset");
                    });
        }
    }

    @Test
    void bindsCalendarTimezoneAsContextAndDoesNotExposeItAsAnEditableCriterion() {
        FilterParamsRequest params = new FilterParamsRequest();
        params.setTimeZone(" Europe/Zurich ");
        params.setStringParams(Map.of(FilterParamResolver.SQL_TIME_ZONE_PARAM, "America/New_York"));
        assertThat(resolver.expand(params)).containsEntry(FilterParamResolver.SQL_TIME_ZONE_PARAM, "Europe/Zurich");
        assertThat(resolver.expand(null)).containsEntry(FilterParamResolver.SQL_TIME_ZONE_PARAM, "UTC");
        assertThat(resolver.expand(new FilterParamsRequest())).containsEntry(FilterParamResolver.SQL_TIME_ZONE_PARAM, "UTC");
        assertThat(resolver.analyze(Set.of(FilterParamResolver.SQL_TIME_ZONE_PARAM, "YEAR_FROM")))
                .extracting("name").containsExactly("YEAR_FROM");
    }

    @Test
    void rejectsUnknownCalendarTimezone() {
        FilterParamsRequest params = new FilterParamsRequest();
        params.setTimeZone("invalid/timezone");
        assertBadRequest(params, "IANA timezone");
    }

    @Test
    void acceptsOpenAndEqualYearBounds() {
        for (Map<String, String> years : java.util.List.of(Map.<String, String>of(),
                Map.of("YEAR_FROM", "2025"), Map.of("YEAR_TO", "2025"),
                Map.of("YEAR_FROM", " 2025 ", "YEAR_TO", "2025"),
                Map.of("YEAR_FROM", "1", "YEAR_TO", "9999"))) {
            FilterParamsRequest params = new FilterParamsRequest();
            params.setStringParams(years);
            assertThat(resolver.expand(params)).containsEntry(FilterParamResolver.SQL_TIME_ZONE_PARAM, "UTC");
        }
    }

    @Test
    void rejectsFractionalOutOfRangeAndReversedYears() {
        for (String value : new String[]{"2025.5", "1e3", "+2025", "NaN", "0", "-1", "10000", "999999999999999"}) {
            for (String name : new String[]{"YEAR_FROM", "YEAR_TO"}) {
                FilterParamsRequest params = new FilterParamsRequest();
                params.setStringParams(Map.of(name, value));
                assertBadRequest(params, "whole year");
            }
        }
        FilterParamsRequest params = new FilterParamsRequest();
        params.setStringParams(Map.of("YEAR_FROM", "2026", "YEAR_TO", "2025"));
        assertBadRequest(params, "on or after");
    }

    private void assertBadRequest(FilterParamsRequest params, String reason) {
        assertThatThrownBy(() -> resolver.expand(params))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains(reason);
                });
    }
}
