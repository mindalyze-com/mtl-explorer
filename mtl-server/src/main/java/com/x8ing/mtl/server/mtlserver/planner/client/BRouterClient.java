package com.x8ing.mtl.server.mtlserver.planner.client;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.planner.PlannerGeometryMetrics;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.LegResultDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Thin HTTP wrapper around the BRouter sidecar. One call = one leg
 * (two adjacent waypoints). BRouter also supports multi-waypoint queries
 * but we deliberately keep legs independent so we can cache each leg
 * individually and only re-route the leg whose endpoint moved.
 *
 * <p>Only instantiated when {@code mtl.planner.enabled=true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
@JsonPropertyOrder({
        "properties",
        "restClient",
        "mapper"
})
public class BRouterClient {

    private final PlannerProperties properties;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Pattern matching BRouter's "datafile E5_N45.rd5 not found" error.
     */
    private static final Pattern SEGMENT_NOT_FOUND = Pattern.compile("datafile\\s+\\S+\\.rd5\\s+not found", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEGMENT_DIRECTORY_MISSING = Pattern.compile("segment directory .+ does not exist", Pattern.CASE_INSENSITIVE);
    private static final int ERROR_BODY_LOG_LIMIT = 2048;
    private static final int ERROR_DETAIL_LIMIT = 500;
    private static final String EMPTY_ERROR_BODY = "<empty>";
    private static final String TRUNCATED_SUFFIX = "...";

    public BRouterClient(PlannerProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getBrouterTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Compute a single leg between {@code from} and {@code to} using the given BRouter profile.
     *
     * @return parsed leg result — geometry + distance/ascent/descent/duration
     * @throws BRouterException on HTTP error, timeout, or malformed response
     */
    public LegResultDto computeLeg(WaypointDto from, WaypointDto to, String profile) {
        // BRouter expects "lonlats=lon,lat|lon,lat" (WGS84).
        String lonlats = from.getLng() + "," + from.getLat() + "|" + to.getLng() + "," + to.getLat();

        String url = UriComponentsBuilder
                .fromUriString(properties.getBrouterBaseUrl())
                .path(PlannerConstants.BROUTER_ROUTE_PATH)
                .queryParam("lonlats", lonlats)
                .queryParam("profile", profile)
                .queryParam("alternativeidx", 0)
                .queryParam("format", PlannerConstants.BROUTER_FORMAT)
                .build()
                .toUriString();

        try {
            String body = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new BRouterException("Routing service returned an empty response. Retry or check the BRouter server logs.");
            }
            return parseGeoJson(body, from, to);
        } catch (BRouterException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String rawResponseBody = e.getResponseBodyAsString();
            String responseBody = rawResponseBody == null ? "" : rawResponseBody;
            String summarizedBody = summarizeErrorBody(responseBody);
            if (SEGMENT_NOT_FOUND.matcher(responseBody).find()) {
                log.warn("BRouter segment missing for requested route leg: status={} body={}", e.getStatusCode(), summarizedBody);
                throw new BRouterSegmentMissingException(
                        "Routing data segment not available",
                        from.getLat(), from.getLng(), to.getLat(), to.getLng());
            }
            log.warn("BRouter HTTP error status={} body={}", e.getStatusCode(), summarizedBody);
            String detail = userErrorBody(responseBody);
            if (SEGMENT_DIRECTORY_MISSING.matcher(responseBody).find()) {
                throw new BRouterException(BRouterException.ROUTING_STORAGE_UNAVAILABLE,
                        "Routing data storage is missing on the server. Restore the BRouter segment directory and restart the routing service."
                        + detailSuffix(detail), e);
            }
            boolean rejectedRoute = e.getStatusCode().is4xxClientError();
            throw new BRouterException(rejectedRoute ? BRouterException.ROUTING_FAILED : BRouterException.ROUTING_UNAVAILABLE,
                    (rejectedRoute ? "BRouter could not calculate this route" : "Routing service failed")
                    + " (HTTP " + e.getStatusCode().value() + ")." + detailSuffix(detail), e);
        } catch (ResourceAccessException e) {
            log.warn("BRouter connection failed: type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("BRouter connection failure", e);
            if (hasTimeoutCause(e)) {
                throw new BRouterException(BRouterException.ROUTING_TIMEOUT,
                        "Routing service timed out. Try a shorter route or retry when the server is less busy.", e);
            }
            throw new BRouterException("Cannot reach the routing service. Check that BRouter is running, then retry.", e);
        } catch (Exception e) {
            log.warn("BRouter call failed: type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("BRouter call failure", e);
            throw new BRouterException("Routing service returned an invalid response. Retry or check the BRouter server logs.", e);
        }
    }

    /**
     * Parse a BRouter GeoJSON FeatureCollection. BRouter returns one Feature of type
     * LineString with 3D coordinates {@code [lng, lat, ele]} and route-level stats
     * in {@code properties}: {@code track-length}, {@code filtered ascend},
     * {@code plain-ascend}, {@code total-time}.
     */
    private LegResultDto parseGeoJson(String body, WaypointDto from, WaypointDto to) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode feature = root.path("features").path(0);
            if (feature.isMissingNode()) {
                throw new BRouterException("BRouter response missing features[0]");
            }

            JsonNode props = feature.path("properties");
            JsonNode coords = feature.path("geometry").path("coordinates");

            LegResultDto leg = new LegResultDto();
            leg.setDistanceM(asDouble(props.path("track-length")));
            leg.setDurationSec(asDouble(props.path("total-time")));

            List<double[]> out = new ArrayList<>(coords.size());
            for (Iterator<JsonNode> it = coords.iterator(); it.hasNext(); ) {
                JsonNode c = it.next();
                if (c.isArray() && c.size() >= 2) {
                    double lng = c.get(0).asDouble();
                    double lat = c.get(1).asDouble();
                    double ele = c.size() >= 3 && !c.get(2).isNull() ? c.get(2).asDouble(Double.NaN) : Double.NaN;
                    out.add(Double.isFinite(ele) ? new double[]{lng, lat, ele} : new double[]{lng, lat});
                }
            }
            if (out.size() >= 2) {
                double[] first = out.getFirst();
                double[] last = out.getLast();
                double forward = endpointDistance(from, first) + endpointDistance(to, last);
                double reverse = endpointDistance(from, last) + endpointDistance(to, first);
                if (reverse < forward) Collections.reverse(out);
            }
            leg.setCoordinates(out);
            var totals = PlannerGeometryMetrics.elevationTotals(out);
            leg.setAscentM(totals.ascentM());
            leg.setDescentM(totals.descentM());
            return leg;
        } catch (BRouterException e) {
            throw e;
        } catch (Exception e) {
            log.warn("BRouter response parsing failed: type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("BRouter response parsing failure", e);
            throw new BRouterException("Routing service returned an invalid response. Retry or check the BRouter server logs.", e);
        }
    }

    private static boolean hasTimeoutCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }

    private static String userErrorBody(String body) {
        String detail = body.strip().replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (detail.startsWith("<")) return "";
        return detail.length() <= ERROR_DETAIL_LIMIT ? detail : detail.substring(0, ERROR_DETAIL_LIMIT) + TRUNCATED_SUFFIX;
    }

    private static String detailSuffix(String detail) {
        return detail.isBlank() ? "" : " BRouter: " + detail;
    }

    private static double endpointDistance(WaypointDto waypoint, double[] coordinate) {
        return GPXReader.getDistanceBetweenTwoWGS84(
                new Coordinate(waypoint.getLng(), waypoint.getLat()),
                new Coordinate(coordinate[0], coordinate[1]));
    }

    static String summarizeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return EMPTY_ERROR_BODY;
        }
        String trimmed = body.strip();
        if (trimmed.length() <= ERROR_BODY_LOG_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, ERROR_BODY_LOG_LIMIT) + TRUNCATED_SUFFIX;
    }

    private static double asDouble(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return 0.0;
        if (n.isNumber()) return n.asDouble();
        try {
            return Double.parseDouble(n.asText());
        } catch (NumberFormatException ignore) {
            return 0.0;
        }
    }
}
