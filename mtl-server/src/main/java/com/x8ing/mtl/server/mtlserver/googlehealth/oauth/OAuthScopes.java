package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class OAuthScopes {

    public static final String ACTIVITY_AND_FITNESS =
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly";
    public static final String HEALTH_METRICS_AND_MEASUREMENTS =
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly";
    public static final String SLEEP =
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly";
    public static final String LOCATION =
            "https://www.googleapis.com/auth/googlehealth.location.readonly";

    public static final List<String> REQUESTED_ORDERED = List.of(
            ACTIVITY_AND_FITNESS,
            HEALTH_METRICS_AND_MEASUREMENTS,
            SLEEP,
            LOCATION
    );
    public static final Set<String> REQUESTED = Set.copyOf(new LinkedHashSet<>(REQUESTED_ORDERED));

    private OAuthScopes() {
    }
}
