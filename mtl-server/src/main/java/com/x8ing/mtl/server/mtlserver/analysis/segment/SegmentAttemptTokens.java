package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * Signed server descriptors survive cache eviction and restarts, but still require source revision validation.
 */
@Component
@Slf4j
public class SegmentAttemptTokens {
    private static final String PURPOSE = "mtl-segment-attempt";
    private static final String DESCRIPTOR_CLAIM = "attempt";
    private static final String VERSION_CLAIM = "calculation";
    private static final int DESCRIPTOR_FIELDS = 9;
    private static final int MAX_REFERENCE_CHARACTERS = 8_192;
    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public SegmentAttemptTokens(JwtUtil security) {
        this(security.purposeSigningKey(PURPOSE), Clock.systemUTC());
    }

    SegmentAttemptTokens(SecretKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    record Descriptor(long trackId, String fromZone, int fromVisit, String toZone, int toVisit,
                      double startPosition, double endPosition, String revision, String zoneRevision) {
    }

    SegmentAttemptSummary issue(SegmentAttemptSummary summary, String owner) {
        var issued = clock.instant();
        var identity = summary.identity();
        String reference = Jwts.builder().issuer(PURPOSE).subject(owner)
                .claim(VERSION_CLAIM, CanonicalTrackRevision.CALCULATION_VERSION)
                .claim(DESCRIPTOR_CLAIM, List.of(identity.trackId(), identity.fromZone(), identity.fromVisit(),
                        identity.toZone(), identity.toVisit(), identity.startPosition(), identity.endPosition(),
                        identity.revision(), identity.zoneRevision()))
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plus(LIFETIME)))
                .signWith(key, Jwts.SIG.HS256).compact();
        if (reference.length() > MAX_REFERENCE_CHARACTERS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Segment descriptor is too large");
        return withReference(summary, reference);
    }

    Descriptor read(String reference, String owner) {
        if (reference == null || reference.length() > MAX_REFERENCE_CHARACTERS) throw SegmentAttemptResolver.stale();
        try {
            var signed = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                    .requireIssuer(PURPOSE).require(VERSION_CLAIM, CanonicalTrackRevision.CALCULATION_VERSION)
                    .build().parseSignedClaims(reference);
            if (!Jwts.SIG.HS256.getId().equals(signed.getHeader().getAlgorithm())) throw SegmentAttemptResolver.stale();
            var claims = signed.getPayload();
            if (!owner.equals(claims.getSubject())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found");
            if (claims.getIssuedAt() == null || claims.getExpiration() == null
                || claims.getIssuedAt().after(Date.from(clock.instant()))
                || claims.getExpiration().getTime() - claims.getIssuedAt().getTime() > LIFETIME.toMillis())
                throw SegmentAttemptResolver.stale();
            var values = claims.get(DESCRIPTOR_CLAIM, List.class);
            if (values == null || values.size() != DESCRIPTOR_FIELDS) throw SegmentAttemptResolver.stale();
            var descriptor = new Descriptor(positiveInteger(values.get(0)), text(values.get(1)),
                    Math.toIntExact(positiveInteger(values.get(2))), text(values.get(3)),
                    Math.toIntExact(positiveInteger(values.get(4))), position(values.get(5)), position(values.get(6)),
                    text(values.get(7)), text(values.get(8)));
            if (descriptor.endPosition() < descriptor.startPosition()) throw SegmentAttemptResolver.stale();
            return descriptor;
        } catch (ExpiredJwtException exception) {
            log.warn("Segment reference expired: type={}", exception.getClass().getSimpleName());
            throw SegmentAttemptResolver.stale();
        } catch (JwtException | IllegalArgumentException | ArithmeticException exception) {
            log.warn("Segment reference verification failed: type={}", exception.getClass().getSimpleName());
            throw SegmentAttemptResolver.stale();
        }
    }

    private static long positiveInteger(Object value) {
        if (!(value instanceof Number number) || number.longValue() <= 0
            || number.doubleValue() != number.longValue()) throw SegmentAttemptResolver.stale();
        return number.longValue();
    }

    private static double position(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0)
            throw SegmentAttemptResolver.stale();
        return number.doubleValue();
    }

    private static String text(Object value) {
        if (!(value instanceof String string) || string.isBlank()) throw SegmentAttemptResolver.stale();
        return string;
    }

    private static SegmentAttemptSummary withReference(SegmentAttemptSummary summary, String reference) {
        var i = summary.identity();
        var identity = new SegmentAttemptIdentity(reference, i.trackId(), i.fromZone(), i.fromVisit(), i.toZone(), i.toVisit(),
                i.start(), i.end(), i.startPosition(), i.endPosition(), i.startCoordinate(), i.endCoordinate(),
                i.revision(), i.zoneRevision(), i.timingStatus());
        return new SegmentAttemptSummary(identity, summary.distanceMeters(), summary.elapsedSeconds(), summary.averageSpeedKmh(),
                summary.ascentMeters(), summary.descentMeters(), summary.energyWh(), summary.maximumPowerWatts(), summary.availability());
    }
}
