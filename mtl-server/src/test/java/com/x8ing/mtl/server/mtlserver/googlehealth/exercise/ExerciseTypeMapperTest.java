package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ExerciseTypeMapperTest {

    private final ExerciseTypeMapper mapper = new ExerciseTypeMapper(new ObjectMapper());

    @Test
    void mapsSupportedGoogleExerciseTypes() {
        assertThat(mapper.map("{\"exercise\":{\"exerciseType\":\"EXERCISE_TYPE_RUNNING\"}}"))
                .get().extracting(ExerciseTypeMapper.MappedType::activityType)
                .isEqualTo(GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(mapper.map("{\"exercise\":{\"exerciseType\":\"mountain-biking\"}}"))
                .get().extracting(ExerciseTypeMapper.MappedType::activityType)
                .isEqualTo(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
    }

    @Test
    void leavesUnsupportedTypesForTheExistingClassifier() {
        assertThat(mapper.map("{\"exercise\":{\"exerciseType\":\"yoga\"}}"))
                .isEmpty();
    }
}
