package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ExerciseTypeMapper {

    private static final Map<String, GpsTrack.ACTIVITY_TYPE> TYPE_MAP = Map.ofEntries(
            Map.entry("running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("walking", GpsTrack.ACTIVITY_TYPE.WALKING),
            Map.entry("hiking", GpsTrack.ACTIVITY_TYPE.HIKING),
            Map.entry("biking", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("mountain_biking", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING),
            Map.entry("rowing", GpsTrack.ACTIVITY_TYPE.ROWING),
            Map.entry("kayaking", GpsTrack.ACTIVITY_TYPE.KAYAKING),
            Map.entry("kayak", GpsTrack.ACTIVITY_TYPE.KAYAKING),
            Map.entry("stand_up_paddleboarding", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE),
            Map.entry("skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("cross_country_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("downhill_skiing", GpsTrack.ACTIVITY_TYPE.SKIING)
    );

    private final ObjectMapper objectMapper;

    public ExerciseTypeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<MappedType> map(String rawJson) {
        try {
            JsonNode exercise = objectMapper.readTree(rawJson).path("exercise");
            String originalType = exercise.path("exerciseType").asText(null);
            if (originalType == null || originalType.isBlank()) {
                return Optional.empty();
            }
            String normalized = normalize(originalType);
            GpsTrack.ACTIVITY_TYPE type = TYPE_MAP.get(normalized);
            return type == null ? Optional.empty() : Optional.of(new MappedType(type, originalType));
        } catch (JacksonException exception) {
            log.warn("Could not map a Google Health exercise type because the stored payload is invalid",
                    exception);
            return Optional.empty();
        }
    }

    private static String normalize(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("exercise_type_")) {
            return normalized.substring("exercise_type_".length());
        }
        return normalized;
    }

    public record MappedType(GpsTrack.ACTIVITY_TYPE activityType, String googleType) {
    }
}
