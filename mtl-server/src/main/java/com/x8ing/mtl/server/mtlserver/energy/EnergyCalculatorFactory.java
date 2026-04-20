package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that maps each {@link GpsTrack.ACTIVITY_TYPE} to its corresponding {@link EnergyCalculator}.
 * Auto-discovers all Spring-managed EnergyCalculator beans on startup.
 */
@Component
@Slf4j
public class EnergyCalculatorFactory {

    private final List<EnergyCalculator> calculators;
    private final Map<GpsTrack.ACTIVITY_TYPE, EnergyCalculator> calculatorMap = new EnumMap<>(GpsTrack.ACTIVITY_TYPE.class);

    public EnergyCalculatorFactory(List<EnergyCalculator> calculators) {
        this.calculators = calculators;
    }

    @PostConstruct
    void init() {
        EnergyCalculator defaultCalculator = null;

        for (EnergyCalculator calc : calculators) {
            for (GpsTrack.ACTIVITY_TYPE type : calc.getActivityTypes()) {
                EnergyCalculator existing = calculatorMap.put(type, calc);
                if (existing != null) {
                    log.warn("Activity type {} is claimed by multiple calculators: {} and {}. Using {}.",
                            type, existing.getClass().getSimpleName(), calc.getClass().getSimpleName(), calc.getClass().getSimpleName());
                }
            }
            // The calculator that handles null/unknown types is the one with the broadest set
            // or explicitly named Default — we detect it by checking if it handles types like CAR
            if (calc.supports(GpsTrack.ACTIVITY_TYPE.CAR)) {
                defaultCalculator = calc;
            }
        }

        // Ensure every ACTIVITY_TYPE has a calculator; assign default for any missing
        if (defaultCalculator != null) {
            for (GpsTrack.ACTIVITY_TYPE type : GpsTrack.ACTIVITY_TYPE.values()) {
                calculatorMap.putIfAbsent(type, defaultCalculator);
            }
        }

        log.info("Registered {} energy calculators covering {} activity types.", calculators.size(), calculatorMap.size());
    }

    /**
     * Get the calculator for a given activity type.
     * Returns the default calculator if the type is null or not mapped.
     */
    public EnergyCalculator getCalculator(GpsTrack.ACTIVITY_TYPE activityType) {
        if (activityType == null) {
            return calculatorMap.get(GpsTrack.ACTIVITY_TYPE.CAR); // default fallback
        }
        return calculatorMap.getOrDefault(activityType, calculatorMap.get(GpsTrack.ACTIVITY_TYPE.CAR));
    }
}
