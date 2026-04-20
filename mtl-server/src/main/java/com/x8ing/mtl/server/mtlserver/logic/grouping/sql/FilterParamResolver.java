package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Translates between typed client filter params ({@link FilterParamsRequest}) and
 * the flat {@code Map<String, String>} expected by {@link com.x8ing.mtl.server.mtlserver.db.readonly.DynamicSqlService}.
 * <p>
 * Also analyses raw SQL param names to produce typed {@link ParamDefinition} descriptors
 * that tell the client which widget to render for each parameter.
 */
@Slf4j
@Component
public class FilterParamResolver {

    private static final String PREFIX_DATE_TIME = "DATE_TIME";
    private static final String PREFIX_GEO_CIRCLE = "GEO_CIRCLE";
    private static final String PREFIX_GEO_RECTANGLE = "GEO_RECTANGLE";
    private static final String PREFIX_GEO_POLYGON = "GEO_POLYGON";

    // Pattern to detect numbered geo params: e.g. GEO_CIRCLE_1_POINT, GEO_CIRCLE_1_RADIUS
    private static final Pattern GEO_CIRCLE_PATTERN = Pattern.compile("^(GEO_CIRCLE_\\d+)_(POINT|RADIUS)$");
    private static final Pattern GEO_RECTANGLE_PATTERN = Pattern.compile("^(GEO_RECTANGLE_\\d+)_(MIN_LAT|MAX_LAT|MIN_LNG|MAX_LNG)$");
    private static final Pattern GEO_POLYGON_PATTERN = Pattern.compile("^(GEO_POLYGON_\\d+)$");

    /**
     * Analyse raw SQL parameter names and produce typed {@link ParamDefinition} descriptors.
     * Groups related params (e.g. GEO_CIRCLE_1_POINT + GEO_CIRCLE_1_RADIUS) into a single definition.
     *
     * @param paramsInSQL the raw param names extracted from the SQL template
     * @return ordered list of param definitions for the client
     */
    public List<ParamDefinition> analyze(Set<String> paramsInSQL) {
        if (paramsInSQL == null || paramsInSQL.isEmpty()) {
            return Collections.emptyList();
        }

        List<ParamDefinition> definitions = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        // Sort for deterministic output
        List<String> sortedParams = new ArrayList<>(paramsInSQL);
        Collections.sort(sortedParams);

        for (String param : sortedParams) {
            if (processed.contains(param)) {
                continue;
            }

            // Check GEO_CIRCLE_N_POINT / GEO_CIRCLE_N_RADIUS
            Matcher circleMatcher = GEO_CIRCLE_PATTERN.matcher(param);
            if (circleMatcher.matches()) {
                String baseName = circleMatcher.group(1); // e.g. GEO_CIRCLE_1
                validateCircleParams(baseName, paramsInSQL);
                processed.add(baseName + "_POINT");
                processed.add(baseName + "_RADIUS");
                definitions.add(ParamDefinition.builder()
                        .name(baseName)
                        .type(ParamType.GEO_CIRCLE)
                        .label(humanLabel(baseName))
                        .build());
                continue;
            }

            // Check GEO_RECTANGLE_N_*
            Matcher rectMatcher = GEO_RECTANGLE_PATTERN.matcher(param);
            if (rectMatcher.matches()) {
                String baseName = rectMatcher.group(1); // e.g. GEO_RECTANGLE_1
                validateRectangleParams(baseName, paramsInSQL);
                processed.add(baseName + "_MIN_LAT");
                processed.add(baseName + "_MAX_LAT");
                processed.add(baseName + "_MIN_LNG");
                processed.add(baseName + "_MAX_LNG");
                definitions.add(ParamDefinition.builder()
                        .name(baseName)
                        .type(ParamType.GEO_RECTANGLE)
                        .label(humanLabel(baseName))
                        .build());
                continue;
            }

            // Check GEO_POLYGON_N (single param, no suffixes)
            Matcher polygonMatcher = GEO_POLYGON_PATTERN.matcher(param);
            if (polygonMatcher.matches()) {
                processed.add(param);
                definitions.add(ParamDefinition.builder()
                        .name(param)
                        .type(ParamType.GEO_POLYGON)
                        .label(humanLabel(param))
                        .build());
                continue;
            }

            // Check DATE_TIME_*
            if (param.startsWith(PREFIX_DATE_TIME)) {
                processed.add(param);
                definitions.add(ParamDefinition.builder()
                        .name(param)
                        .type(ParamType.DATE_TIME)
                        .label(humanLabel(param))
                        .build());
                continue;
            }

            // Default: STRING
            processed.add(param);
            definitions.add(ParamDefinition.builder()
                    .name(param)
                    .type(ParamType.STRING)
                    .label(humanLabel(param))
                    .build());
        }

        return definitions;
    }

    /**
     * Expand typed client params into a flat {@code Map<String, String>} suitable for SQL binding.
     *
     * @param request the typed params from the client (may be null)
     * @return flat map with WKT values for geo shapes, pass-through for strings/dates
     */
    public Map<String, String> expand(FilterParamsRequest request) {
        Map<String, String> sqlParams = new HashMap<>();

        if (request == null) {
            return sqlParams;
        }

        // Pass-through: string params
        if (request.getStringParams() != null) {
            sqlParams.putAll(request.getStringParams());
        }

        // Pass-through: date-time params
        if (request.getDateTimeParams() != null) {
            sqlParams.putAll(request.getDateTimeParams());
        }

        // Convert circles to WKT POINT + radius string
        if (request.getGeoCircles() != null) {
            for (Map.Entry<String, GeoCircle> entry : request.getGeoCircles().entrySet()) {
                String name = entry.getKey();
                GeoCircle circle = entry.getValue();
                if (circle != null && circle.getLat() != null && circle.getLng() != null && circle.getRadiusM() != null) {
                    sqlParams.put(name + "_POINT", "POINT(%s %s)".formatted(circle.getLng(), circle.getLat()));
                    sqlParams.put(name + "_RADIUS", String.valueOf(circle.getRadiusM()));
                }
            }
        }

        // Convert rectangles to individual bound params
        if (request.getGeoRectangles() != null) {
            for (Map.Entry<String, GeoRectangle> entry : request.getGeoRectangles().entrySet()) {
                String name = entry.getKey();
                GeoRectangle rect = entry.getValue();
                if (rect != null && rect.getMinLat() != null && rect.getMaxLat() != null
                    && rect.getMinLng() != null && rect.getMaxLng() != null) {
                    sqlParams.put(name + "_MIN_LAT", String.valueOf(rect.getMinLat()));
                    sqlParams.put(name + "_MAX_LAT", String.valueOf(rect.getMaxLat()));
                    sqlParams.put(name + "_MIN_LNG", String.valueOf(rect.getMinLng()));
                    sqlParams.put(name + "_MAX_LNG", String.valueOf(rect.getMaxLng()));
                }
            }
        }

        // Convert polygons to WKT POLYGON string
        if (request.getGeoPolygons() != null) {
            for (Map.Entry<String, GeoPolygon> entry : request.getGeoPolygons().entrySet()) {
                String name = entry.getKey();
                GeoPolygon polygon = entry.getValue();
                if (polygon != null && polygon.getCoordinates() != null && polygon.getCoordinates().size() >= 3) {
                    sqlParams.put(name, toWktPolygon(polygon.getCoordinates()));
                }
            }
        }

        return sqlParams;
    }

    /**
     * Convert a list of [lng, lat] coordinate pairs to a WKT POLYGON string.
     * Auto-closes the polygon if the last point doesn't match the first.
     */
    private String toWktPolygon(List<double[]> coordinates) {
        List<double[]> coords = new ArrayList<>(coordinates);

        // Auto-close polygon
        double[] first = coords.getFirst();
        double[] last = coords.getLast();
        if (first[0] != last[0] || first[1] != last[1]) {
            coords.add(first);
        }

        String ring = coords.stream()
                .map(c -> c[0] + " " + c[1])
                .collect(Collectors.joining(", "));

        return "POLYGON((" + ring + "))";
    }

    private void validateCircleParams(String baseName, Set<String> paramsInSQL) {
        String pointParam = baseName + "_POINT";
        String radiusParam = baseName + "_RADIUS";
        if (!paramsInSQL.contains(pointParam)) {
            throw new IllegalArgumentException(
                    "GEO_CIRCLE parameter '%s' is missing the required '%s' param in SQL. A circle requires both %s and %s."
                            .formatted(baseName, pointParam, pointParam, radiusParam));
        }
        if (!paramsInSQL.contains(radiusParam)) {
            throw new IllegalArgumentException(
                    "GEO_CIRCLE parameter '%s' is missing the required '%s' param in SQL. A circle requires both %s and %s."
                            .formatted(baseName, radiusParam, pointParam, radiusParam));
        }
    }

    private void validateRectangleParams(String baseName, Set<String> paramsInSQL) {
        String[] required = {"_MIN_LAT", "_MAX_LAT", "_MIN_LNG", "_MAX_LNG"};
        for (String suffix : required) {
            String fullParam = baseName + suffix;
            if (!paramsInSQL.contains(fullParam)) {
                throw new IllegalArgumentException(
                        "GEO_RECTANGLE parameter '%s' is missing the required '%s' param in SQL."
                                .formatted(baseName, fullParam));
            }
        }
    }

    /**
     * Convert param name like "GEO_CIRCLE_1" or "DATE_TIME_FROM" to a human-readable label.
     */
    private String humanLabel(String paramName) {
        // GEO_CIRCLE_1 → "Circle 1", DATE_TIME_FROM → "From", SEARCH_WORD → "Search Word"
        String label = paramName;
        if (label.startsWith("GEO_CIRCLE_")) {
            label = "Circle " + label.substring("GEO_CIRCLE_".length());
        } else if (label.startsWith("GEO_RECTANGLE_")) {
            label = "Rectangle " + label.substring("GEO_RECTANGLE_".length());
        } else if (label.startsWith("GEO_POLYGON_")) {
            label = "Polygon " + label.substring("GEO_POLYGON_".length());
        } else if (label.startsWith("DATE_TIME_")) {
            label = label.substring("DATE_TIME_".length());
        }
        // Convert underscores to spaces, title case
        return Arrays.stream(label.split("_"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
