package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TcxValidatorTest {

    @TempDir
    Path tempDirectory;

    private final TcxValidator validator = new TcxValidator();

    @Test
    void acceptsExpectedExerciseWithUsableRoute() throws Exception {
        Path file = write("""
                <?xml version="1.0"?>
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities><Activity Sport="Running"><Id>2026-08-28T10:00:00Z</Id><Lap StartTime="2026-08-28T10:00:00Z"><Track>
                    <Trackpoint><Time>2026-08-28T10:01:00Z</Time><Position><LatitudeDegrees>47.1</LatitudeDegrees><LongitudeDegrees>8.1</LongitudeDegrees></Position></Trackpoint>
                    <Trackpoint><Time>2026-08-28T10:02:00Z</Time><Position><LatitudeDegrees>47.2</LatitudeDegrees><LongitudeDegrees>8.2</LongitudeDegrees></Position></Trackpoint>
                  </Track></Lap></Activity></Activities>
                </TrainingCenterDatabase>
                """);

        TcxValidator.ValidationResult result = validator.validate(
                file, Instant.parse("2026-08-28T10:00:00Z"), Instant.parse("2026-08-28T11:00:00Z"));

        assertThat(result.usableRoutePoints()).isEqualTo(2);
    }

    @Test
    void rejectsMalformedAndRouteLessTcx() throws Exception {
        Path malformed = write("<TrainingCenterDatabase><broken>");
        Path noRoute = write("""
                <TrainingCenterDatabase><Activities><Activity><Id>2026-08-28T10:00:00Z</Id>
                </Activity></Activities></TrainingCenterDatabase>
                """);

        assertThatThrownBy(() -> validator.validate(malformed,
                Instant.parse("2026-08-28T10:00:00Z"), Instant.parse("2026-08-28T11:00:00Z")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("malformed");
        assertThatThrownBy(() -> validator.validate(noRoute,
                Instant.parse("2026-08-28T10:00:00Z"), Instant.parse("2026-08-28T11:00:00Z")))
                .isInstanceOf(TcxValidator.MissingRouteException.class);
    }

    @Test
    void disablesExternalEntities() throws Exception {
        Path external = write("""
                <?xml version="1.0"?><!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <TrainingCenterDatabase><Activities><Activity><Id>&secret;</Id></Activity></Activities></TrainingCenterDatabase>
                """);

        assertThatThrownBy(() -> validator.validate(external,
                Instant.parse("2026-08-28T10:00:00Z"), Instant.parse("2026-08-28T11:00:00Z")))
                .isInstanceOf(IOException.class);
    }

    private Path write(String contents) throws IOException {
        Path path = tempDirectory.resolve("exercise-" + System.nanoTime() + ".tcx");
        Files.writeString(path, contents);
        return path;
    }
}
