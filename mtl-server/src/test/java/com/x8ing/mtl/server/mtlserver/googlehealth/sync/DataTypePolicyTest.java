package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataTypePolicyTest {

    @Test
    void usesReviewedReadOnlyScopesAndHybridRepresentations() {
        assertThat(OAuthScopes.REQUESTED).containsExactlyInAnyOrder(
                OAuthScopes.ACTIVITY_AND_FITNESS,
                OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS,
                OAuthScopes.SLEEP,
                OAuthScopes.LOCATION);
        assertThat(DataTypePolicy.all())
                .anySatisfy(policy -> {
                    assertThat(policy.dataType()).isEqualTo("heart-rate");
                    assertThat(policy.mode()).isEqualTo(DataTypePolicy.Mode.HEART_RATE_SAMPLE);
                })
                .anySatisfy(policy -> {
                    assertThat(policy.dataType()).isEqualTo("steps");
                    assertThat(policy.mode()).isEqualTo(DataTypePolicy.Mode.RECONCILED_INTERVAL);
                })
                .anySatisfy(policy -> {
                    assertThat(policy.dataType()).isEqualTo("steps");
                    assertThat(policy.mode()).isEqualTo(DataTypePolicy.Mode.DAILY_ROLLUP);
                });
    }

    @Test
    void partialConsentExcludesCategoriesWithoutTheirScope() {
        assertThat(DataTypePolicy.allowedBy(Set.of(OAuthScopes.SLEEP)))
                .extracting(DataTypePolicy.Policy::dataType)
                .containsExactly("sleep");
    }

    @Test
    void usesOnlyOperationsAndScopesSupportedByTheCurrentCatalog() {
        var activityLevel = policy("activity-level", DataTypePolicy.Mode.RECONCILED_INTERVAL);
        var zoneCalories = policy("calories-in-heart-rate-zone", DataTypePolicy.Mode.DAILY_ROLLUP);
        var zoneTime = policy("time-in-heart-rate-zone", DataTypePolicy.Mode.DAILY_ROLLUP);

        assertThat(activityLevel.requiredScope()).isEqualTo(OAuthScopes.ACTIVITY_AND_FITNESS);
        assertThat(activityLevel.recordType()).isEqualTo(DataTypePolicy.RecordType.INTERVAL);
        assertThat(DataTypePolicy.all())
                .noneMatch(policy -> policy.dataType().equals("activity-level")
                                     && policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP);
        assertThat(zoneCalories.requiredScope()).isEqualTo(OAuthScopes.ACTIVITY_AND_FITNESS);
        assertThat(zoneCalories.recordType()).isEqualTo(DataTypePolicy.RecordType.DAILY);
        assertThat(zoneTime.requiredScope()).isEqualTo(OAuthScopes.ACTIVITY_AND_FITNESS);
        assertThat(policy("sleep", DataTypePolicy.Mode.RAW).recordType())
                .isEqualTo(DataTypePolicy.RecordType.SESSION_END);
    }

    @Test
    void convertsKebabNamesToApiJsonFieldNames() {
        assertThat(DataTypePolicy.jsonFieldName("daily-vo2-max"))
                .isEqualTo("dailyVo2Max");
    }

    private static DataTypePolicy.Policy policy(
            String dataType, DataTypePolicy.Mode mode) {
        return DataTypePolicy.all().stream()
                .filter(candidate -> candidate.dataType().equals(dataType) && candidate.mode() == mode)
                .findFirst()
                .orElseThrow();
    }
}
