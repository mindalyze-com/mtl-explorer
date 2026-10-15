package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Calendar boundaries are civil ISO dates; end dates are exclusive. Null statistics represent missing data.
 */
@JsonPropertyOrder({"startDate", "endDate", "periodStartDate", "periodEndDate", "total", "average",
        "minimum", "maximum", "minimumDate", "maximumDate", "availableDays", "selectedDays",
        "inProgress", "clipped", "sessions"})
public record GHMetricBucket(
        String startDate, String endDate, String periodStartDate, String periodEndDate,
        @Schema(nullable = true) Double total, @Schema(nullable = true) Double average,
        @Schema(nullable = true) Double minimum, @Schema(nullable = true) Double maximum,
        @Schema(nullable = true) String minimumDate, @Schema(nullable = true) String maximumDate,
        int availableDays, int selectedDays, boolean inProgress, boolean clipped, List<GHMetricPoint> sessions) {
}
