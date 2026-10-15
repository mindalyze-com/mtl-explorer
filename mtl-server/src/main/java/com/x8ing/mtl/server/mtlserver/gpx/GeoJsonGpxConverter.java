package com.x8ing.mtl.server.mtlserver.gpx;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts GeoJSON directly because GPSBabel drops coordinate elevations.
 */
@Slf4j
final class GeoJsonGpxConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LONGITUDE_INDEX = 0;
    private static final int LATITUDE_INDEX = 1;
    private static final int ELEVATION_INDEX = 2;
    private static final int MIN_POSITION_SIZE = 2;

    private GeoJsonGpxConverter() {
    }

    static String convert(Path source) throws IOException {
        long started = System.nanoTime();
        log.info("Converting GeoJSON source {} to GPX", source.getFileName());
        try (var input = Files.newInputStream(source)) {
            JsonNode root = JSON.readTree(input);
            if (root == null || !root.isObject()) throw new IOException("GeoJSON object required");
            GPX.Builder builder = GPX.builder().creator("MTL Explorer");
            append(root, null, builder);
            GPX gpx = builder.build();
            long pointCount = gpx.getWayPoints().size() + gpx.getTracks().stream()
                    .flatMap(track -> track.getSegments().stream()).mapToLong(segment -> segment.getPoints().size()).sum();
            if (pointCount == 0) throw new IOException("GeoJSON contains no supported coordinates");
            String xml = GPX.Writer.DEFAULT.toString(gpx);
            log.info("Converted GeoJSON source {}: tracks={} points={} durationMs={}", source.getFileName(),
                    gpx.getTracks().size(), pointCount, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return xml;
        } catch (IOException | RuntimeException e) {
            log.error("GeoJSON conversion failed: file={} type={} message={}", source.getFileName(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new IOException("GeoJSON conversion failed for " + source.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static void append(JsonNode node, JsonNode properties, GPX.Builder gpx) throws IOException {
        if (node == null || node.isNull()) return; // GeoJSON permits a feature with no geometry.
        JsonNode coordinates = node.path("coordinates");
        switch (node.path("type").asText()) {
            case "FeatureCollection" -> {
                for (JsonNode feature : array(node.path("features"))) append(feature, null, gpx);
            }
            case "Feature" -> append(node.path("geometry"), node.path("properties"), gpx);
            case "GeometryCollection" -> {
                for (JsonNode geometry : array(node.path("geometries"))) append(geometry, properties, gpx);
            }
            case "Point" -> gpx.addWayPoint(point(coordinates).toBuilder()
                    .name(property(properties, "name")).desc(property(properties, "description")).build());
            case "MultiPoint" -> {
                for (JsonNode position : array(coordinates))
                    gpx.addWayPoint(point(position).toBuilder()
                            .name(property(properties, "name")).desc(property(properties, "description")).build());
            }
            case "LineString" -> addTrack(List.of(coordinates), properties, gpx);
            case "MultiLineString", "Polygon" -> addTrack(array(coordinates), properties, gpx);
            case "MultiPolygon" -> {
                for (JsonNode polygon : array(coordinates)) addTrack(array(polygon), properties, gpx);
            }
            default -> throw new IOException("Unsupported GeoJSON type: " + node.path("type").asText());
        }
    }

    private static void addTrack(Iterable<JsonNode> lines, JsonNode properties, GPX.Builder gpx) throws IOException {
        Track.Builder track = Track.builder().name(property(properties, "name"))
                .desc(property(properties, "description"));
        for (JsonNode line : lines) {
            TrackSegment.Builder segment = TrackSegment.builder();
            for (JsonNode position : array(line)) segment.addPoint(point(position));
            // Preserve separate lines/rings instead of connecting their endpoints.
            if (!segment.points().isEmpty()) track.addSegment(segment.build());
        }
        if (!track.segments().isEmpty()) gpx.addTrack(track.build());
    }

    private static WayPoint point(JsonNode position) throws IOException {
        if (!position.isArray() || position.size() < MIN_POSITION_SIZE)
            throw new IOException("GeoJSON position requires longitude and latitude");
        WayPoint.Builder point = WayPoint.builder()
                .lon(number(position.get(LONGITUDE_INDEX)))
                .lat(number(position.get(LATITUDE_INDEX)));
        if (position.size() > ELEVATION_INDEX && !position.get(ELEVATION_INDEX).isNull())
            point.ele(number(position.get(ELEVATION_INDEX)));
        return point.build();
    }

    private static double number(JsonNode value) throws IOException {
        if (!value.isNumber() || !Double.isFinite(value.doubleValue()))
            throw new IOException("GeoJSON coordinates must be finite numbers");
        return value.doubleValue();
    }

    private static Iterable<JsonNode> array(JsonNode node) throws IOException {
        if (!node.isArray()) throw new IOException("GeoJSON coordinate/feature array required");
        return node;
    }

    private static String property(JsonNode properties, String name) {
        if (properties == null) return null;
        JsonNode value = properties.path(name);
        return value.isTextual() ? value.asText() : null;
    }
}
