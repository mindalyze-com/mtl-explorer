package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GPXReaderFallbackTimeTest {

    @Test
    void usesMetadataTimeWhenTrackPointsHaveNoTimestamps() {
        Date metadataTime = Date.from(Instant.parse("2026-05-26T06:06:00Z"));
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setName("sample.kml");
        indexedFile.setPath("/sample.kml");
        indexedFile.setFullPath("/sample.kml");
        indexedFile.setLastModifiedDate(Date.from(Instant.parse("2026-05-27T06:06:00Z")));

        String gpxXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="gpsbabel" xmlns="http://www.topografix.com/GPX/1/1">
                  <metadata>
                    <time>2026-05-26T06:06:00Z</time>
                  </metadata>
                  <trk>
                    <name>KML line without point times</name>
                    <trkseg>
                      <trkpt lat="47.3769" lon="8.5417"><ele>410</ele></trkpt>
                      <trkpt lat="47.3779" lon="8.5427"><ele>412</ele></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """;

        List<GPXReader.LoadResult> results = new GPXReader().importGpxXml(indexedFile, gpxXml);

        assertEquals(1, results.size());
        GpsTrack track = results.getFirst().gpsTrack;
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, track.getLoadStatus());
        assertEquals(metadataTime, track.getStartDate());
        assertEquals(metadataTime, track.getEndDate());
    }
}
