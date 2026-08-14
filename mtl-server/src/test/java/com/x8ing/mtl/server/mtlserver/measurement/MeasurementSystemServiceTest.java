package com.x8ing.mtl.server.mtlserver.measurement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementSystemServiceTest {

    private final MeasurementSystemService service = new MeasurementSystemService();

    @Test
    void definesMetricMilestonesInCanonicalUnits() {
        assertThat(service.milestones(MeasurementSystem.METRIC).definitions())
                .extracting(MilestoneDefinition::dimension, MilestoneDefinition::thresholdCanonical)
                .containsExactly(
                        tuple(MilestoneDimension.DISTANCE, 10_000.0),
                        tuple(MilestoneDimension.DISTANCE, 25_000.0),
                        tuple(MilestoneDimension.DISTANCE, 50_000.0),
                        tuple(MilestoneDimension.DISTANCE, 100_000.0),
                        tuple(MilestoneDimension.ASCENT, 500.0),
                        tuple(MilestoneDimension.ASCENT, 1000.0),
                        tuple(MilestoneDimension.ASCENT, 2000.0),
                        tuple(MilestoneDimension.ENERGY, 1000.0)
                );
    }

    @Test
    void definesUsCustomaryMilestonesInExactCanonicalUnits() {
        assertThat(service.milestones(MeasurementSystem.US_CUSTOMARY).definitions())
                .extracting(MilestoneDefinition::dimension, MilestoneDefinition::thresholdCanonical)
                .containsExactly(
                        tuple(MilestoneDimension.DISTANCE, 8046.72),
                        tuple(MilestoneDimension.DISTANCE, 16_093.44),
                        tuple(MilestoneDimension.DISTANCE, 40_233.6),
                        tuple(MilestoneDimension.DISTANCE, 80_467.2),
                        tuple(MilestoneDimension.DISTANCE, 160_934.4),
                        tuple(MilestoneDimension.ASCENT, 304.8),
                        tuple(MilestoneDimension.ASCENT, 762.0),
                        tuple(MilestoneDimension.ASCENT, 1524.0),
                        tuple(MilestoneDimension.ENERGY, 1000.0)
                );
    }

    private static org.assertj.core.groups.Tuple tuple(MilestoneDimension dimension, double threshold) {
        return org.assertj.core.groups.Tuple.tuple(dimension, threshold);
    }
}
