package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.MtlServerApplication;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootTest(classes = MtlServerApplication.class)
@Slf4j
class GPXReaderTest {

    @Autowired
    private GPXStoreService GPXStoreService;

    @Test()
    @Disabled // TODO some time ;-)
    @Transactional
    @Rollback(false)
    void importGpxFile() {


        // test duplicate
        GpsTrack gpsTrack1 = readAndSave("2011-09-24 1244.gpx");
        Assertions.assertEquals(false, gpsTrack1.getDidFilterOutlierByDistance());
        GpsTrack gpsTrack1Duplicate = readAndSave("2011-09-24 1244_simplified.gpx");


        GpsTrack gpsTrack2 = readAndSave("2011-03-09 1933.gpx");
        Assertions.assertEquals(false, gpsTrack2.getDidFilterOutlierByDistance());

        GpsTrack gpsTrack3 = readAndSave("19_04_2012 19_43.gpx");
        Assertions.assertEquals(false, gpsTrack3.getDidFilterOutlierByDistance());

        GpsTrack gpsTrack4 = readAndSave("17_03_2012 12_02.gpx");
        Assertions.assertEquals(false, gpsTrack4.getDidFilterOutlierByDistance());

        GpsTrack gpsTrack5 = readAndSave("2018-12-13_12-40-56_Walking.tcx.gpx");
        Assertions.assertEquals(false, gpsTrack5.getDidFilterOutlierByDistance());

        // test outlier
        GpsTrack outlier1 = readAndSave("Uetli20100715_Outlier.gpx");
        Assertions.assertEquals(true, outlier1.getDidFilterOutlierByDistance());


    }

    private GpsTrack readAndSave(String file) {

        Path directory = Paths.get("src/test/java/com/x8ing/mtl/server/mtlserver/gpx/resource");
        IndexedFile f = new IndexedFile();
        f.setFullPath(directory.toString());
        List<GPXReader.LoadResult> results = GPXStoreService.readAndSave(f);
        return results.isEmpty() ? null : results.get(0).gpsTrack;
    }

}