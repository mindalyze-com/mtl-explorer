package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculatorFactory;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy;
import com.x8ing.mtl.server.mtlserver.energy.impl.HikingEnergyCalculator;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.gpx.GpsPipelineReliabilityTest.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Synthetic GPX import, denoising, continuity, slope, motion and energy coverage.
 */
class MetabolicEnergyIngestTest {
    @Test
    void autoPauseGapExcludesOnlyUnsupportedTimeAndKeepsEndpointSlopesUsable() throws Exception {
        var input = straight(20, 5, 5);
        var afterPause = straight(20, 5, 5);
        afterPause.forEach(point -> {
            point.x += 100 / 76000.0;
            point.setM(point.getM() + 395);
        });
        var loaded = read("<trk>" + segment(input) + segment(afterPause) + "</trk>").getFirst();
        var canonical = smooth(loaded);
        var points = metrics(canonical, loaded.recordingBreakBeforePointIndexes);
        var motion = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getCoordinates()), loaded.recordingBreakBeforePointIndexes);
        for (var point : points) {
            var interval = motion.intervals().get(point.getPointIndex());
            point.setMotionState(interval.state());
            point.setMotionReason(interval.reason());
        }
        var factory = new EnergyCalculatorFactory(List.of(new HikingEnergyCalculator()));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(factory, "init");
        var result = new EnergyService(factory, null, null, null, null).calculateAndPopulatePoints(
                points, GpsTrack.ACTIVITY_TYPE.HIKING, EnergyParameters.builder().riderWeightKg(75).build()).getMetabolicEnergy();
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
        assertThat(result.getCoveredSeconds()).isEqualTo(190);
        assertThat(result.getRecordingGapSeconds()).isEqualTo(300);
        assertThat(result.getUncoveredSeconds()).isEqualTo(300);
        assertThat(result.getUnknownDuration()).isFalse();
        assertThat(result.getExclusionReasons()).containsExactly(MetabolicEnergy.ExclusionReason.RECORDING_GAP);
        assertThat(result.getActiveKcal()).isPositive();
    }
}
