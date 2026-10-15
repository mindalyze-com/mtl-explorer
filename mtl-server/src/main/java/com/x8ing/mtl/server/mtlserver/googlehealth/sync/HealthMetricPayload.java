package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Metric payloads shared by synthetic generation and ordinary Health queries. */
public sealed interface HealthMetricPayload {
    String dataType();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Steps(Double countSum, Double count) implements HealthMetricPayload {
        public Steps(double countSum) { this(countSum, null); }
        @Override public String dataType() { return "steps"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Distance(Double millimetersSum, Double millimeters) implements HealthMetricPayload {
        public Distance(double millimetersSum) { this(millimetersSum, null); }
        @Override public String dataType() { return "distance"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActiveEnergy(Double kcalSum, Double kcal) implements HealthMetricPayload {
        public ActiveEnergy(double kcalSum) { this(kcalSum, null); }
        @Override public String dataType() { return "active-energy-burned"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RestingHeartRate(Double beatsPerMinute) implements HealthMetricPayload {
        @Override public String dataType() { return "daily-resting-heart-rate"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Weight(Double weightGrams) implements HealthMetricPayload {
        public Weight(long weightGrams) { this((double) weightGrams); }
        @Override public String dataType() { return "weight"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sleep(SleepSummary summary, List<SleepStage> stages) implements HealthMetricPayload {
        @Override public String dataType() { return "sleep"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SleepSummary(Double minutesAsleep) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SleepStage(String stage, long minutes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartRate(Double beatsPerMinute, Double beatsPerMinuteAvg, Double beatsPerMinuteMin,
                     Double beatsPerMinuteMax, Long sampleCount) implements HealthMetricPayload {
        public HeartRate(double average, double minimum, double maximum, long sampleCount) {
            this(null, average, minimum, maximum, sampleCount);
        }
        @Override public String dataType() { return DataTypePolicy.HEART_RATE_DATA_TYPE; }
    }
}
