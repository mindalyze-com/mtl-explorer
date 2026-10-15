package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SourceReviewControllerTest {
    @Test
    void failedDownloadsReturnAnActionableProblemEvenWhenGpxWasRequested() throws Exception {
        var access = mock(TrackSourceAccess.class);
        String reason = "Source could not be read. Check the file and server read access.";
        when(access.locate(1)).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason));
        var mvc = MockMvcBuilders.standaloneSetup(new SourceReviewController(mock(SourceReviewService.class), access, new DemoWriteAccess(new MtlAppProperties()))).build();
        mvc.perform(get("/api/track-review/1/gpx").accept("application/gpx+xml"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(reason));
    }
}
