package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@JsonPropertyOrder({
        "gpsTrackRepository"
})
public class ActivityTypeAutoClassifier {

    private final GpsTrackRepository gpsTrackRepository;

    private static final Map<String, GpsTrack.ACTIVITY_TYPE> GARMIN_TRACK_TYPE_ALIASES = Map.ofEntries(
            Map.entry("street_running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("trail_running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("track_running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("treadmill_running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("indoor_running", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("virtual_run", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("ultra_run", GpsTrack.ACTIVITY_TYPE.RUNNING),
            Map.entry("obstacle_run", GpsTrack.ACTIVITY_TYPE.RUNNING),

            Map.entry("casual_walking", GpsTrack.ACTIVITY_TYPE.WALKING),
            Map.entry("speed_walking", GpsTrack.ACTIVITY_TYPE.WALKING),
            Map.entry("fitness_walking", GpsTrack.ACTIVITY_TYPE.WALKING),
            Map.entry("indoor_walking", GpsTrack.ACTIVITY_TYPE.WALKING),

            Map.entry("mountaineering", GpsTrack.ACTIVITY_TYPE.HIKING),
            Map.entry("rucking", GpsTrack.ACTIVITY_TYPE.HIKING),

            Map.entry("cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("biking", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("road_biking", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("road_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("indoor_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("virtual_ride", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("gravel_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("gravel_biking", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("cyclocross", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("track_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("recumbent_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("hand_cycling", GpsTrack.ACTIVITY_TYPE.BICYCLE),
            Map.entry("e_biking", GpsTrack.ACTIVITY_TYPE.BICYCLE),

            Map.entry("e_mountain_biking", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING),

            Map.entry("cross_country_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("alpine_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("resort_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("backcountry_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("skate_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("classic_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),
            Map.entry("indoor_skiing", GpsTrack.ACTIVITY_TYPE.SKIING),

            Map.entry("indoor_rowing", GpsTrack.ACTIVITY_TYPE.ROWING),
            Map.entry("rowing_machine", GpsTrack.ACTIVITY_TYPE.ROWING),

            Map.entry("kayak", GpsTrack.ACTIVITY_TYPE.KAYAKING),
            Map.entry("sea_kayaking", GpsTrack.ACTIVITY_TYPE.KAYAKING),
            Map.entry("whitewater_kayaking", GpsTrack.ACTIVITY_TYPE.KAYAKING),

            Map.entry("stand_up_paddleboarding", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE),
            Map.entry("stand_up_paddle", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE),
            Map.entry("paddleboarding", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE),
            Map.entry("sup", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE),

            Map.entry("motorcycling", GpsTrack.ACTIVITY_TYPE.MOTORBIKING),
            Map.entry("motorcycle", GpsTrack.ACTIVITY_TYPE.MOTORBIKING),
            Map.entry("motor_biking", GpsTrack.ACTIVITY_TYPE.MOTORBIKING),

            Map.entry("driving", GpsTrack.ACTIVITY_TYPE.CAR),
            Map.entry("automotive", GpsTrack.ACTIVITY_TYPE.CAR),
            Map.entry("off_roading", GpsTrack.ACTIVITY_TYPE.CAR),
            Map.entry("auto_racing", GpsTrack.ACTIVITY_TYPE.CAR),

            Map.entry("flying", GpsTrack.ACTIVITY_TYPE.AIRPLANE),
            Map.entry("aviation", GpsTrack.ACTIVITY_TYPE.AIRPLANE)
    );

    public ActivityTypeAutoClassifier(GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
    }

    @JsonPropertyOrder({
            "previousType",
            "determinedType",
            "updated"
    })
    public record ClassificationResult(
            GpsTrack.ACTIVITY_TYPE previousType,
            GpsTrack.ACTIVITY_TYPE determinedType,
            boolean updated) {
    }

    @JsonPropertyOrder({
            "activityType",
            "activityTypeSource",
            "activityTypeSourceDetails"
    })
    private record ActivityClassification(
            GpsTrack.ACTIVITY_TYPE activityType,
            GpsTrack.ACTIVITY_TYPE_SOURCE activityTypeSource,
            String activityTypeSourceDetails) {
    }

    /**
     * Classifies the activity type for the given track and commits only the classification
     * columns in its own transaction. This avoids merging stale GpsTrack entities and
     * overwriting unrelated state such as duplicate detection results.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClassificationResult classifyActivity(Long gpsTrackId, List<GpsTrackDataPoint> gpsTrackDataPointList) {

        if (gpsTrackId == null) {
            String msg = "GPS track ID was null. That's invalid. Can't classify";
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        GpsTrack gpsTrack = gpsTrackRepository.findById(gpsTrackId)
                .orElseThrow(() -> new IllegalArgumentException("GPS track not found. id=" + gpsTrackId));
        GpsTrack.ACTIVITY_TYPE previousType = gpsTrack.getActivityType();
        if (gpsTrack.getActivityTypeSource() != null) {
            return new ClassificationResult(previousType, previousType, false);
        }

        ActivityClassification classification;
        if (CollectionUtils.isEmpty(gpsTrackDataPointList)) {
            String msg = "GPS track had no data points. gpsTrackId=%s".formatted(gpsTrackId);
            log.warn(msg);
            classification = new ActivityClassification(null, GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED, msg);
        } else {
            classification = guessActivity(gpsTrack, gpsTrackDataPointList);
        }
        int updated = gpsTrackRepository.updateActivityClassificationIfPending(
                gpsTrackId,
                classification.activityType(),
                classification.activityTypeSource(),
                classification.activityTypeSourceDetails());
        return new ClassificationResult(previousType, classification.activityType(), updated > 0);
    }

    private static ActivityClassification guessActivity(GpsTrack gpsTrack, List<GpsTrackDataPoint> gpsTrackDataPointList) {


        GpsTrack.ACTIVITY_TYPE activityType = null;
        StringBuilder typeSourceDetails = new StringBuilder();
        typeSourceDetails.append("Start Auto Guessing Activity Type. \n");

        // check if we get something out of the filename
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackName(), typeSourceDetails, "trackName");
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackDescription(), typeSourceDetails, "trackDescription");
        String fileName = gpsTrack.getIndexedFile() == null ? null : gpsTrack.getIndexedFile().getName();
        activityType = guessBasedOnText(activityType, fileName, typeSourceDetails, "fileName");
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackType(), typeSourceDetails, "trackType"); // not reliable for me especially for older tracks
        activityType = guessBasedOnTrackType(activityType, gpsTrack.getTrackType(), typeSourceDetails);

        if (activityType == null) {

            typeSourceDetails.append("Activity type not found in name or text fields. Try using speed. \n");

            double[] speed = gpsTrackDataPointList.stream()
                    .filter(p -> p.getSpeedInKmhMovingWindow() != null && p.getSpeedInKmhMovingWindow() > 0)
                    .mapToDouble(GpsTrackDataPoint::getSpeedInKmhMovingWindow)
                    .toArray();

            if (speed != null) {
                Percentile percentile = new Percentile();
                double speed66p = percentile.evaluate(speed, 66.0);
                double speed95p = percentile.evaluate(speed, 95);

                typeSourceDetails.append(String.format("Speed percentiles: 66%%-percentile: %.1f km/h, 95%%-percentile: %.1f km/h. \n", speed66p, speed95p));

                //noinspection ConstantValue
                if (activityType == null && (speed66p > 200 || speed95p > 1236)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.SUPER_SONIC;
                    typeSourceDetails.append("Guessed based on speed to be super fast like super-sonic. This can indicate an data issue, e.g. GPS signal weak or old trackers. \n");
                }
                if (activityType == null && (speed66p > 150 || speed95p > 200)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.AIRPLANE;
                    typeSourceDetails.append("Guessed based on speed to be type airplane. \n");
                }
                if (activityType == null && (speed66p > 50 || speed95p > 80)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.CAR;
                    typeSourceDetails.append("Guessed based on speed to be type car. \n");
                }

                if (activityType == null && (speed66p < 9 || speed95p < 15)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.WALKING;
                    typeSourceDetails.append("Guessed based on speed to be type walking. \n");
                }

                if (activityType == null) {
                    activityType = GpsTrack.ACTIVITY_TYPE.BICYCLE;
                    typeSourceDetails.append("Guessed based on speed to be type bicycle. \n");
                }

            } else {
                typeSourceDetails.append("Did not find speed info. Fallback to default type! \n");
                activityType = GpsTrack.ACTIVITY_TYPE.WALKING;
            }

        }

        return new ActivityClassification(
                activityType,
                GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS,
                typeSourceDetails.toString());

    }

    static GpsTrack.ACTIVITY_TYPE guessBasedOnTrackType(GpsTrack.ACTIVITY_TYPE currentType, String trackType, StringBuilder typeSourceDetails) {

        if (currentType != null || StringUtils.isBlank(trackType)) {
            return currentType;
        }

        for (String token : StringUtils.split(trackType, " ,;/")) {
            GpsTrack.ACTIVITY_TYPE found = mapTrackTypeToken(token);
            if (found != null) {
                typeSourceDetails.append("Found the activity based on GPX/Garmin track type '").append(token).append("'. \n");
                return found;
            }
        }

        typeSourceDetails.append("Could not map GPX/Garmin track type '").append(trackType).append("'. \n");
        return null;
    }

    private static GpsTrack.ACTIVITY_TYPE mapTrackTypeToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }

        String normalized = normalizeTrackTypeToken(token);

        GpsTrack.ACTIVITY_TYPE mapped = GARMIN_TRACK_TYPE_ALIASES.get(normalized);
        if (mapped != null) {
            return mapped;
        }

        try {
            return GpsTrack.ACTIVITY_TYPE.valueOf(StringUtils.upperCase(normalized));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeTrackTypeToken(String token) {
        return StringUtils.lowerCase(StringUtils.trim(token))
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_v\\d+$", "")
                .replaceFirst("^(sport|sub_sport)_", "");
    }

    static GpsTrack.ACTIVITY_TYPE guessBasedOnText(GpsTrack.ACTIVITY_TYPE currentType, String text, StringBuilder typeSourceDetails, String source) {

        // if we already know one, then return
        if (currentType != null) {
            return currentType;
        }

        GpsTrack.ACTIVITY_TYPE found = null;

        if (text != null) {
            text = StringUtils.lowerCase(text);
            text = StringUtils.trim(text);

            if (found == null && matchesAny(text, ".*walking.*", ".*spazieren.*")) {
                found = GpsTrack.ACTIVITY_TYPE.WALKING;
            }
            if (found == null && matchesAny(text, ".*langlauf.*", ".*skiing.*")) {
                found = GpsTrack.ACTIVITY_TYPE.SKIING;
            }
            if (found == null && matchesAny(text, ".*hiking.*", ".*wandern.*")) {
                found = GpsTrack.ACTIVITY_TYPE.HIKING;
            }
            if (found == null && matchesAny(text, ".*running.*", ".*rennen.*", ".*joggen.*")) {
                found = GpsTrack.ACTIVITY_TYPE.RUNNING;
            }
            if (found == null && matchesAny(text, ".*mountain biking.*", ".*mountain_biking.*", ".*\\s*mtb([^a-z]|$).*")) {
                found = GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING;
            }
            if (found == null && matchesAny(text, ".*cycling", ".*cycle", "velo", "fahrrad")) {
                found = GpsTrack.ACTIVITY_TYPE.BICYCLE;
            }
            if (found == null && matchesAny(text, "car([^a-z]|$).*", "\\s*auto([^a-z]|$).*", ".*\\s*motor([^a-z]|$).*", ".*\\s*driving([^a-z]|$).*")) {
                found = GpsTrack.ACTIVITY_TYPE.CAR;
            }
            if (found == null && matchesAny(text,
                    ".*(^|[^a-z])rudern?([^a-z]|$).*",
                    ".*(^|[^a-z])ruderboot([^a-z]|$).*",
                    ".*(^|[^a-z])boot([^a-z]|$).*")) {
                found = GpsTrack.ACTIVITY_TYPE.ROWING;
            }
            // SUP is part of too many other words, hence only if a text starts with it
            if (found == null && matchesAny(text, ".*\\s*sup([^a-z]|$).*", ".*stand up paddle.*")) {
                found = GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE;
            }
        }
        if (found != null) {
            typeSourceDetails.append("Did find the activity based on fuzzy text fragments found in '").append(source).append("'. \n");
        }

        return found;
    }

    private static boolean matchesAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

}
