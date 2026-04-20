package com.x8ing.mtl.server.mtlserver.gpx;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;


@Slf4j
class GPXReaderSimpleTest {


    /* This is correct ! */
    @Test
    void convertLongLatWgs84ToPlanarWebMercator() {

        // lat: 47.55841
        // long: 8.50784
        double longitude = 8.50784;
        double latitude = 47.55841;

        Point mercator = new GPXReader().convertLongLatWgs84ToPlanarWebMercator(longitude, latitude);

        // mercator: 947088.417, 6033701.91
        log.info(mercator.toText());

        Assertions.assertTrue(mercator.getX() > 947088.415 && mercator.getX() < 947088.418);
        Assertions.assertTrue(mercator.getY() > 6033701.90 && mercator.getY() < 6033701.93);


    }
}