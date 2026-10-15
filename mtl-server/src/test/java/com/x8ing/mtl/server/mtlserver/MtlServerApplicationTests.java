package com.x8ing.mtl.server.mtlserver;

import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MtlServerApplicationTests {

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void contextLoads() {
    }

}
