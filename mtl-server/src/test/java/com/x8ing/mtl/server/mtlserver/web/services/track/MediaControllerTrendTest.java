package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaTrendQueryRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTrendTest {

    private final MediaTrendQueryRepository queryRepository = mock(MediaTrendQueryRepository.class);
    private final MediaTrendService trendService = new MediaTrendService(queryRepository);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(
            mock(MediaRepository.class),
            mock(TrackMediaService.class),
            mock(MediaPositionService.class),
            trendService,
            mock(VideoThumbnailService.class),
            mediaProcessLimiter())).build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"grouping\":\"INVALID\",\"scope\":\"ALL_INDEXED\"}",
            "{\"grouping\":\"YEAR\",\"scope\":\"INVALID\"}",
            "{\"grouping\":\"MONTH\",\"scope\":\"ALL_INDEXED\",\"bucketKey\":\"2026-99\",\"kind\":\"ALL\",\"page\":0,\"pageSize\":60}",
            "{\"grouping\":\"MONTH\",\"scope\":\"ALL_INDEXED\",\"bucketKey\":\"2026-08\",\"kind\":\"INVALID\",\"page\":0,\"pageSize\":60}",
            "{\"grouping\":\"MONTH\",\"scope\":\"ALL_INDEXED\",\"bucketKey\":\"2026-08\",\"kind\":\"ALL\",\"page\":0,\"pageSize\":101}"
    })
    void rejectsInvalidTrendRequests(String body) throws Exception {
        String endpoint = body.contains("bucketKey") ? "/api/media/trends/items" : "/api/media/trends";
        mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static MediaProcessLimiter mediaProcessLimiter() {
        return new MediaProcessLimiter(new MediaProcessProperties());
    }
}
