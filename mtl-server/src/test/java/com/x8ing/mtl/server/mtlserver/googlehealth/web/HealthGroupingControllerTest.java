package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthGroupingControllerTest {
    private MirrorRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        repository = mock(MirrorRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new HealthGroupingController(
                new HealthGroupingService(repository, new ObjectMapper()))).build();
    }

    @ParameterizedTest
    @CsvSource({"2026-02-30,2026-03-01,AUTO,UTC,startDate must be a valid", "2026-01-02,2026-01-01,AUTO,UTC,endDate must be after",
            "2026-01-01,2026-01-02,HOUR,UTC,groupBy must be", "2026-01-01,2026-01-02,AUTO,Wrong/Zone,timeZone must be"})
    void returnsClearValidationProblems(String start, String end, String grouping, String zone, String detail) throws Exception {
        mvc.perform(get("/api/google-health/metrics/grouped").param("startDate", start).param("endDate", end)
                        .param("groupBy", grouping).param("timeZone", zone))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(detail)));
        verifyNoInteractions(repository);
    }

    @Test
    void missingDateHasAClearError() throws Exception {
        mvc.perform(get("/api/google-health/metrics/grouped").param("endDate", "2026-01-02").param("timeZone", "UTC"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("startDate is required."));
    }

    @Test
    void emptyResponseStillIncludesResolvedCalendarMetadata() throws Exception {
        mvc.perform(get("/api/google-health/metrics/grouped").param("startDate", "2025-01-01").param("endDate", "2026-01-01").param("timeZone", "UTC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolvedGrouping").value("MONTH"))
                .andExpect(jsonPath("$.requestedGrouping").value("AUTO")).andExpect(jsonPath("$.series").isEmpty());
    }

    @Test
    void readsUseTheOrdinaryGroupedHealthQuery() throws Exception {
        mvc.perform(get("/api/google-health/metrics/grouped").param("startDate", "2026-01-01").param("endDate", "2026-01-02").param("timeZone", "UTC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series").isEmpty());
    }
}
