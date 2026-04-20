package com.x8ing.mtl.server.mtlserver.planner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.LegResultDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
public class BRouterClient {

    private final PlannerProperties properties;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Pattern matching BRouter's "datafile E5_N45.rd5 not found" error.
     */
    private static final Pattern SEGMENT_NOT_FOUND = Pattern.compile("datafile\\s+\\S+\\.rd5\\s+not found", Pattern.CASE_INSENSITIVE);

    public BRouterClient(PlannerProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
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

        String url = properties.getBrouterBaseUrl() + PlannerConstants.BROUTER_ROUTE_PATH
                     + "?lonlats=" + lonlats
                     + "&profile=" + profile
                     + "&alternativeidx=0"
                     + "&format=" + PlannerConstants.BROUTER_FORMAT;

        try {
            String body = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new BRouterException("Empty response from BRouter");
            }
            return parseGeoJson(body);
        } catch (BRouterException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (SEGMENT_NOT_FOUND.matcher(responseBody).find()) {
                log.warn("BRouter segment missing for leg [{},{}]→[{},{}]: {}",
                        from.getLat(), from.getLng(), to.getLat(), to.getLng(), responseBody.trim());
                throw new BRouterSegmentMissingException(
                        "Routing data segment not available: " + responseBody.trim(),
                        from.getLat(), from.getLng(), to.getLat(), to.getLng());
            }
            log.warn("BRouter HTTP error url={} status={} body={}", url, e.getStatusCode(), responseBody.trim());
            throw new BRouterException("BRouter call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.warn("BRouter call failed url={} err={}", url, e.toString());
            throw new BRouterException("BRouter call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a BRouter GeoJSON FeatureCollection. BRouter returns one Feature of type
     * LineString with 3D coordinates {@code [lng, lat, ele]} and route-level stats
     * in {@code properties}: {@code track-length}, {@code filtered ascend},
     * {@code plain-ascend}, {@code total-time}.
     */
    private LegResultDto parseGeoJson(String body) {
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
            leg.setAscentM(asDouble(props.path("filtered ascend")));
            leg.setDurationSec(asDouble(props.path("total-time")));

            List<double[]> out = new ArrayList<>(coords.size());
            for (Iterator<JsonNode> it = coords.elements(); it.hasNext(); ) {
                JsonNode c = it.next();
                if (c.isArray() && c.size() >= 2) {
                    double lng = c.get(0).asDouble();
                    double lat = c.get(1).asDouble();
                    double ele = c.size() >= 3 ? c.get(2).asDouble(0.0) : 0.0;
                    out.add(new double[]{lng, lat, ele});
                }
            }
            leg.setCoordinates(out);
            // BRouter doesn't expose a "filtered descent" property, so derive it from 3D geometry.
            leg.setDescentM(computeDescentFromGeometry(out));
            return leg;
        } catch (BRouterException e) {
            throw e;
        } catch (Exception e) {
            throw new BRouterException("Failed to parse BRouter GeoJSON: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback descent calculation directly from 3D geometry when BRouter props are ambiguous.
     */
    private double computeDescentFromGeometry(List<double[]> coords) {
        double descent = 0;
        for (int i = 1; i < coords.size(); i++) {
            double dz = coords.get(i)[2] - coords.get(i - 1)[2];
            if (dz < -PlannerConstants.MIN_ELEVATION_DELTA_M) {
                descent += -dz;
            }
        }
        return descent;
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
