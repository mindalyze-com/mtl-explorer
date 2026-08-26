package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GPXReaderFileParsingTest {

    @TempDir
    Path tempDirectory;

    @Test
    void directFileParsing_acceptsUtf8BomAndStylesheetInstruction() throws Exception {
        Path gpxFile = tempDirectory.resolve("bom-and-stylesheet.gpx");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <?xml-stylesheet type="text/xsl" href="details.xsl"?>
                <gpx version="1.1" creator="MTL Explorer test" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><name>Synthetic track</name><trkseg>
                    <trkpt lat="47.0000" lon="8.0000"><time>2026-01-01T00:00:00Z</time></trkpt>
                    <trkpt lat="47.0001" lon="8.0001"><time>2026-01-01T00:00:10Z</time></trkpt>
                  </trkseg></trk>
                </gpx>
                """;
        Files.write(gpxFile, ("\uFEFF" + xml).getBytes(StandardCharsets.UTF_8));

        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setName(gpxFile.getFileName().toString());
        indexedFile.setFullPath(gpxFile.toString());

        List<GPXReader.LoadResult> results = new GPXReader().importGpxFile(indexedFile);

        assertEquals(1, results.size());
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, results.getFirst().gpsTrack.getLoadStatus());
        assertNotNull(results.getFirst().trackCleaned);
        assertEquals(2, results.getFirst().trackCleaned.getNumPoints());
    }
}
