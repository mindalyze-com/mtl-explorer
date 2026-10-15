package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

@Component
public class TcxValidator {

    private static final int MINIMUM_ROUTE_POINTS = 2;
    private static final Duration EXERCISE_TIME_TOLERANCE = Duration.ofHours(2);

    public ValidationResult validate(Path tcxFile, Instant expectedStart, Instant expectedEnd) throws IOException {
        if (expectedStart == null || expectedEnd == null || expectedEnd.isBefore(expectedStart)) {
            throw new IOException("Google Health exercise has no valid physical interval");
        }

        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        int usableRoutePoints = 0;
        boolean insideTrackpoint = false;
        boolean hasLatitude = false;
        boolean hasLongitude = false;
        boolean expectedExerciseTimeFound = false;
        String activityId = null;
        String currentElement = null;

        try (InputStream input = Files.newInputStream(tcxFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        currentElement = reader.getLocalName();
                        if ("Trackpoint".equals(currentElement)) {
                            insideTrackpoint = true;
                            hasLatitude = false;
                            hasLongitude = false;
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS && !reader.isWhiteSpace()) {
                        String value = reader.getText().trim();
                        if (insideTrackpoint && "LatitudeDegrees".equals(currentElement)) {
                            hasLatitude = validCoordinate(value, -90.0, 90.0);
                        } else if (insideTrackpoint && "LongitudeDegrees".equals(currentElement)) {
                            hasLongitude = validCoordinate(value, -180.0, 180.0);
                        } else if (insideTrackpoint && "Time".equals(currentElement)) {
                            expectedExerciseTimeFound |= isExpectedExerciseTime(value, expectedStart, expectedEnd);
                        } else if (!insideTrackpoint && "Id".equals(currentElement) && activityId == null) {
                            activityId = value;
                            expectedExerciseTimeFound |= isExpectedExerciseTime(value, expectedStart, expectedEnd);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if ("Trackpoint".equals(reader.getLocalName())) {
                            if (hasLatitude && hasLongitude) {
                                usableRoutePoints++;
                            }
                            insideTrackpoint = false;
                        }
                        currentElement = null;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            throw new IOException("Google Health TCX is malformed", exception);
        }

        if (usableRoutePoints < MINIMUM_ROUTE_POINTS) {
            throw new MissingRouteException("Google Health exercise does not contain a usable route");
        }
        if (!expectedExerciseTimeFound) {
            throw new IOException("Google Health TCX does not match the expected exercise interval");
        }
        return new ValidationResult(activityId, usableRoutePoints);
    }

    private static boolean validCoordinate(String value, double minimum, double maximum) {
        try {
            double coordinate = Double.parseDouble(value);
            return Double.isFinite(coordinate) && coordinate >= minimum && coordinate <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isExpectedExerciseTime(String value, Instant expectedStart, Instant expectedEnd) {
        try {
            Instant time = Instant.parse(value);
            Instant lowerBound = expectedStart.minus(EXERCISE_TIME_TOLERANCE);
            Instant upperBound = expectedEnd.plus(EXERCISE_TIME_TOLERANCE);
            return !time.isBefore(lowerBound) && !time.isAfter(upperBound);
        } catch (DateTimeException exception) {
            return false;
        }
    }

    public record ValidationResult(String activityId, int usableRoutePoints) {
    }

    public static final class MissingRouteException extends IOException {
        public MissingRouteException(String message) {
            super(message);
        }
    }
}
