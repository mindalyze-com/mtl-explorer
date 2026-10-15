package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.trackreview.SyntheticTrackSources;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GPXReaderSourceSectionTest {
    private static final int POINTS_PER_SECTION = 90;
    private static final long NEXT_DAY_SECONDS = 86400;

    @Test
    void dailyTracksRetainTheirOriginalSourceSections() {
        var times = SyntheticTrackSources.regular(POINTS_PER_SECTION);
        var results = read(SyntheticTrackSources.document(SyntheticTrackSources.section(times, 0),
                SyntheticTrackSources.section(times.stream().map(t -> t.plusSeconds(NEXT_DAY_SECONDS)).toList(), POINTS_PER_SECTION)));
        assertEquals(List.of(0, 1), results.stream().map(r -> r.gpsTrack.getSourceRecordingSection()).toList());
    }

    @Test
    void aTimeGapInsideOneSourceSectionDoesNotCreateANewSourceSectionIdentity() {
        var times = SyntheticTrackSources.regular(POINTS_PER_SECTION * 2);
        for (int i = POINTS_PER_SECTION; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(NEXT_DAY_SECONDS));
        var results = read(SyntheticTrackSources.source(times));
        assertEquals(List.of(0, 0), results.stream().map(r -> r.gpsTrack.getSourceRecordingSection()).toList());
    }

    @Test
    void aTrackCombiningSourceSectionsCannotClaimToBelongToOnlyOneOfThem() {
        var times = SyntheticTrackSources.regular(POINTS_PER_SECTION);
        var results = read(SyntheticTrackSources.document(SyntheticTrackSources.section(times, 0),
                SyntheticTrackSources.section(times.stream().map(t -> t.plusSeconds(POINTS_PER_SECTION * 5L)).toList(), POINTS_PER_SECTION)));
        assertEquals(1, results.size());
        assertNull(results.getFirst().gpsTrack.getSourceRecordingSection());
    }

    private List<GPXReader.LoadResult> read(byte[] source) {
        var file = new IndexedFile();
        file.setId(1L);
        file.setName("synthetic-sections.gpx");
        return new GPXReader().importGpxXml(file, new String(source, StandardCharsets.UTF_8));
    }
}
