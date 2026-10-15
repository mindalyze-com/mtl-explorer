package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DataTypePolicy {

    public static final String EXERCISE_DATA_TYPE = "exercise";
    public static final String HEART_RATE_DATA_TYPE = "heart-rate";

    private static final int STANDARD_WINDOW_DAYS = 90;
    private static final int SHORT_WINDOW_DAYS = 14;

    private static final List<Policy> POLICIES = buildPolicies();

    public static List<Policy> all() {
        return POLICIES;
    }

    public static List<Policy> allowedBy(Set<String> grantedScopes) {
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            return List.of();
        }
        return POLICIES.stream().filter(policy -> grantedScopes.contains(policy.requiredScope())).toList();
    }

    public static String jsonFieldName(String dataType) {
        StringBuilder field = new StringBuilder();
        boolean uppercaseNext = false;
        for (int index = 0; index < dataType.length(); index++) {
            char character = dataType.charAt(index);
            if (character == '-') {
                uppercaseNext = true;
            } else if (uppercaseNext) {
                field.append(Character.toUpperCase(character));
                uppercaseNext = false;
            } else {
                field.append(character);
            }
        }
        return field.toString();
    }

    public static String filterFieldName(String dataType) {
        return dataType.replace('-', '_');
    }

    private static List<Policy> buildPolicies() {
        List<Policy> policies = new ArrayList<>();

        policies.add(new Policy(HEART_RATE_DATA_TYPE, Mode.HEART_RATE_SAMPLE, RecordType.SAMPLE,
                OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, SHORT_WINDOW_DAYS));
        policies.add(new Policy("steps", Mode.RECONCILED_INTERVAL, RecordType.INTERVAL,
                OAuthScopes.ACTIVITY_AND_FITNESS, STANDARD_WINDOW_DAYS));
        policies.add(new Policy("activity-level", Mode.RECONCILED_INTERVAL, RecordType.INTERVAL,
                OAuthScopes.ACTIVITY_AND_FITNESS, STANDARD_WINDOW_DAYS));

        addRaw(policies, OAuthScopes.ACTIVITY_AND_FITNESS, RecordType.SESSION_START,
                EXERCISE_DATA_TYPE);
        addRaw(policies, OAuthScopes.ACTIVITY_AND_FITNESS, RecordType.DAILY,
                "daily-vo2-max");
        addRaw(policies, OAuthScopes.ACTIVITY_AND_FITNESS, RecordType.SAMPLE,
                "run-vo2-max", "vo2-max");
        addRaw(policies, OAuthScopes.SLEEP, RecordType.SESSION_END, "sleep");
        addRaw(policies, OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, RecordType.SAMPLE,
                "heart-rate-variability", "oxygen-saturation", "respiratory-rate-sleep-summary",
                "core-body-temperature", "blood-glucose", "weight", "height", "body-fat");
        addRaw(policies, OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, RecordType.DAILY,
                "daily-heart-rate-variability", "daily-resting-heart-rate", "daily-heart-rate-zones",
                "daily-oxygen-saturation", "daily-respiratory-rate", "daily-sleep-temperature-derivations");

        addDaily(policies, OAuthScopes.ACTIVITY_AND_FITNESS,
                "steps", "distance", "floors", "altitude", "active-energy-burned",
                "total-calories", "active-minutes", "active-zone-minutes",
                "sedentary-period", "swim-lengths-data",
                "calories-in-heart-rate-zone", "time-in-heart-rate-zone");
        addDaily(policies, OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS,
                HEART_RATE_DATA_TYPE);

        return List.copyOf(policies);
    }

    private static void addRaw(List<Policy> policies, String scope, RecordType recordType, String... dataTypes) {
        for (String dataType : dataTypes) {
            policies.add(new Policy(dataType, Mode.RAW, recordType, scope, STANDARD_WINDOW_DAYS));
        }
    }

    private static void addDaily(List<Policy> policies, String scope, String... dataTypes) {
        for (String dataType : dataTypes) {
            int maxDays = Set.of(HEART_RATE_DATA_TYPE, "active-minutes", "total-calories", "calories-in-heart-rate-zone")
                    .contains(dataType) ? SHORT_WINDOW_DAYS : STANDARD_WINDOW_DAYS;
            policies.add(new Policy(dataType, Mode.DAILY_ROLLUP, RecordType.DAILY, scope, maxDays));
        }
    }

    private DataTypePolicy() {
    }

    public enum Mode {
        RAW,
        HEART_RATE_SAMPLE,
        RECONCILED_INTERVAL,
        DAILY_ROLLUP
    }

    public enum RecordType {
        INTERVAL,
        SAMPLE,
        DAILY,
        SESSION_START,
        SESSION_END
    }

    public enum WindowTimeDomain {
        PHYSICAL,
        CIVIL,
        DATE
    }

    public record Policy(
            String dataType,
            Mode mode,
            RecordType recordType,
            WindowTimeDomain windowTimeDomain,
            String requiredScope,
            int maxWindowDays) {

        public Policy(
                String dataType,
                Mode mode,
                RecordType recordType,
                String requiredScope,
                int maxWindowDays) {
            this(dataType, mode, recordType, inferredWindowTimeDomain(recordType), requiredScope, maxWindowDays);
        }

        public Policy(String dataType, Mode mode, String requiredScope, int maxWindowDays) {
            this(dataType, mode, inferredRecordType(dataType, mode), requiredScope, maxWindowDays);
        }

        public String representation() {
            return mode.name();
        }

        public String jsonFieldName() {
            return DataTypePolicy.jsonFieldName(dataType);
        }

        private static RecordType inferredRecordType(String dataType, Mode mode) {
            if (mode == Mode.DAILY_ROLLUP || dataType.startsWith("daily-")) {
                return RecordType.DAILY;
            }
            if (dataType.equals(EXERCISE_DATA_TYPE)) {
                return RecordType.SESSION_START;
            }
            if (dataType.equals("sleep")) {
                return RecordType.SESSION_END;
            }
            if (mode == Mode.RECONCILED_INTERVAL || dataType.equals("activity-level")) {
                return RecordType.INTERVAL;
            }
            return RecordType.SAMPLE;
        }

        private static WindowTimeDomain inferredWindowTimeDomain(RecordType recordType) {
            return switch (recordType) {
                case DAILY -> WindowTimeDomain.DATE;
                case SESSION_START -> WindowTimeDomain.CIVIL;
                case INTERVAL, SAMPLE, SESSION_END -> WindowTimeDomain.PHYSICAL;
            };
        }
    }
}
