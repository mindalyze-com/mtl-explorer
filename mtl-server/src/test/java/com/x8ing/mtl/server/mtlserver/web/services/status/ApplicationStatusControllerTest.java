package com.x8ing.mtl.server.mtlserver.web.services.status;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusControllerTest {

    @Test
    void omittedIncludeSelectsEverySection() {
        assertThat(ApplicationStatusController.parseIncludedSections(null))
                .isEqualTo(EnumSet.allOf(ApplicationStatusSection.class));
    }

    @Test
    void acceptsRepeatedAndCommaSeparatedSections() {
        assertThat(ApplicationStatusController.parseIncludedSections(
                List.of("freshness,processing", "google-health")))
                .containsExactly(
                        ApplicationStatusSection.FRESHNESS,
                        ApplicationStatusSection.PROCESSING,
                        ApplicationStatusSection.GOOGLE_HEALTH
                );
    }

    @Test
    void rejectsEmptyOrUnknownSections() {
        assertThatThrownBy(() -> ApplicationStatusController.parseIncludedSections(List.of("")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ApplicationStatusController.parseIncludedSections(List.of("unknown")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
