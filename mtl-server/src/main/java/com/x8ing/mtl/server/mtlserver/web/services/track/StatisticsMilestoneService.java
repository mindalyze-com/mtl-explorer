package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.StatisticsMilestoneQueryRepository;
import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystem;
import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystemService;
import com.x8ing.mtl.server.mtlserver.measurement.MilestoneResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves and evaluates statistics milestones for one measurement system.
 */
@Service
public class StatisticsMilestoneService {

    private final MeasurementSystemService measurementSystemService;
    private final StatisticsMilestoneQueryRepository milestoneQueryRepository;

    public StatisticsMilestoneService(
            MeasurementSystemService measurementSystemService,
            StatisticsMilestoneQueryRepository milestoneQueryRepository
    ) {
        this.measurementSystemService = measurementSystemService;
        this.milestoneQueryRepository = milestoneQueryRepository;
    }

    public List<MilestoneResult> findMilestones(Long[] filterTrackIds, MeasurementSystem measurementSystem) {
        return milestoneQueryRepository.findMilestones(
                filterTrackIds,
                measurementSystemService.milestones(measurementSystem).definitions()
        );
    }
}
