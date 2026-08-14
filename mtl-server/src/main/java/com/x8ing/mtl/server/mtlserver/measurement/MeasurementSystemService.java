package com.x8ing.mtl.server.mtlserver.measurement;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.measurement.MeasurementConversions.METERS_PER_FOOT;
import static com.x8ing.mtl.server.mtlserver.measurement.MeasurementConversions.METERS_PER_KILOMETER;
import static com.x8ing.mtl.server.mtlserver.measurement.MeasurementConversions.METERS_PER_MILE;

/**
 * Resolves measurement-system-dependent domain definitions.
 */
@Service
public class MeasurementSystemService {

    private static final double ENERGY_MILESTONE_WH = 1000.0;

    private static final MilestoneProfile METRIC_MILESTONES = profile(
            List.of(10.0, 25.0, 50.0, 100.0),
            METERS_PER_KILOMETER,
            List.of(500.0, 1000.0, 2000.0),
            1.0
    );

    private static final MilestoneProfile US_CUSTOMARY_MILESTONES = profile(
            List.of(5.0, 10.0, 25.0, 50.0, 100.0),
            METERS_PER_MILE,
            List.of(1000.0, 2500.0, 5000.0),
            METERS_PER_FOOT
    );

    public MilestoneProfile milestones(MeasurementSystem measurementSystem) {
        return switch (measurementSystem) {
            case METRIC -> METRIC_MILESTONES;
            case US_CUSTOMARY -> US_CUSTOMARY_MILESTONES;
        };
    }

    private static MilestoneProfile profile(
            List<Double> distanceThresholds,
            double distanceUnitInMeters,
            List<Double> ascentThresholds,
            double ascentUnitInMeters
    ) {
        List<MilestoneDefinition> definitions = new ArrayList<>();
        addDefinitions(definitions, MilestoneDimension.DISTANCE, distanceThresholds, distanceUnitInMeters);
        addDefinitions(definitions, MilestoneDimension.ASCENT, ascentThresholds, ascentUnitInMeters);
        definitions.add(new MilestoneDefinition(
                MilestoneDimension.ENERGY,
                ENERGY_MILESTONE_WH,
                definitions.size() + 1
        ));
        return new MilestoneProfile(definitions);
    }

    private static void addDefinitions(
            List<MilestoneDefinition> definitions,
            MilestoneDimension dimension,
            List<Double> thresholds,
            double canonicalUnitFactor
    ) {
        for (double threshold : thresholds) {
            definitions.add(new MilestoneDefinition(
                    dimension,
                    threshold * canonicalUnitFactor,
                    definitions.size() + 1
            ));
        }
    }
}
