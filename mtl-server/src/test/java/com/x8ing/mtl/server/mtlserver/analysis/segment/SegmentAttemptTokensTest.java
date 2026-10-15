package com.x8ing.mtl.server.mtlserver.analysis.segment;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.core;
import static org.junit.jupiter.api.Assertions.*;

class SegmentAttemptTokensTest {
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private final javax.crypto.SecretKey key = Jwts.SIG.HS256.key().build();

    private SegmentAttemptTokens codec(Instant time) {
        return new SegmentAttemptTokens(key, Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test
    void signedDescriptorsRetainCanonicalPositionsAfterRestart() {
        var source = core(60, 600, new double[][]{{0, 0}, {600, 60}}).summary();
        var signed = codec(NOW).issue(source, "owner");
        var descriptor = codec(NOW.plusSeconds(1)).read(signed.identity().reference(), "owner");
        assertEquals(source.identity().trackId(), descriptor.trackId());
        assertEquals(source.identity().startPosition(), descriptor.startPosition());
        assertEquals(source.identity().endPosition(), descriptor.endPosition());
        assertEquals(source.identity().revision(), descriptor.revision());
        var claims = Jwts.parser().verifyWith(key).clock(() -> java.util.Date.from(NOW)).build().parseSignedClaims(signed.identity().reference()).getPayload();
        assertFalse(claims.containsKey("analysis"));
        assertEquals(9, ((java.util.List<?>) claims.get("attempt")).size());
        assertEquals(source.elapsedSeconds(), signed.elapsedSeconds());
        assertEquals(source.identity().start(), signed.identity().start());
    }

    @Test
    void moreThanFiveThousandNewAttemptsRemainUsableForBothOwners() {
        var tokens = codec(NOW);
        var source = core(60, 600, new double[][]{{0, 0}, {600, 60}}).summary();
        var i = source.identity();
        var published = new ArrayList<SegmentAttemptSummary>();
        for (int visit = 1; visit <= 5_001; visit++) {
            var identity = new SegmentAttemptIdentity(null, i.trackId(), "A", visit, "B", visit,
                    i.start(), i.end(), i.startPosition(), i.endPosition(), i.startCoordinate(), i.endCoordinate(),
                    i.revision(), i.zoneRevision(), i.timingStatus());
            published.add(tokens.issue(new SegmentAttemptSummary(identity, 600d, 60d, 36d,
                    null, null, null, null, source.availability()), visit % 2 == 0 ? "two" : "one"));
        }
        for (int index = 0; index < published.size(); index++) {
            var entry = published.get(index);
            var descriptor = tokens.read(entry.identity().reference(), index % 2 == 0 ? "one" : "two");
            assertEquals(entry.identity().fromVisit(), descriptor.fromVisit());
            assertEquals(entry.identity().toVisit(), descriptor.toVisit());
            assertEquals(entry.identity().revision(), descriptor.revision());
        }
    }

    @Test
    void expiryTamperingWrongOwnerAndOtherSigningPurposesAreRejected() {
        var signed = codec(NOW).issue(core(60, 600, new double[][]{{0, 0}, {600, 60}}).summary(), "owner");
        var reference = signed.identity().reference();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> codec(NOW).read(reference, "other")).getStatusCode());
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> codec(NOW.plusSeconds(1_801)).read(reference, "owner")).getStatusCode());
        int changed = reference.indexOf('.') + 2;
        var tampered = reference.substring(0, changed) + (reference.charAt(changed) == 'A' ? 'B' : 'A') + reference.substring(changed + 1);
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> codec(NOW).read(tampered, "owner")).getStatusCode());
        var other = Jwts.builder().issuer("login").subject("owner").signWith(key).compact();
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> codec(NOW).read(other, "owner")).getStatusCode());
    }
}
