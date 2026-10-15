package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GpxUploadService;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexerImpl;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminToolInstallService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueController;
import com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewController;
import com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewService;
import com.x8ing.mtl.server.mtlserver.trackreview.TrackSourceAccess;
import com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.Decision;
import com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.ReviewDecisionRequest;
import com.x8ing.mtl.server.mtlserver.web.services.garmin.GarminExportController;
import com.x8ing.mtl.server.mtlserver.web.services.gpxupload.GpxUploadController;
import com.x8ing.mtl.server.mtlserver.web.services.indexer.IndexerStatusController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoWriteProtectionTest {
    private final MtlAppProperties properties = new MtlAppProperties();
    private final DemoWriteAccess access = new DemoWriteAccess(properties);
    private final GpxUploadService uploads = mock(GpxUploadService.class);
    private final IndexerStatusService indexStatus = mock(IndexerStatusService.class);
    private final GPXDirectoryWatcherService gpsIndexer = mock(GPXDirectoryWatcherService.class);
    private final MediaIndexerService mediaIndexer = mock(MediaIndexerService.class);
    private final SourceIssueRepository issues = mock(SourceIssueRepository.class);
    private final SourceReviewService sourceReviews = mock(SourceReviewService.class);
    private final TrackSourceAccess sourceAccess = mock(TrackSourceAccess.class);
    private final GarminExporter garmin = mock(GarminExporter.class);
    private final GarminToolInstallService helpers = mock(GarminToolInstallService.class);
    private final GpxUploadController uploadController = new GpxUploadController(uploads, access);
    private final IndexerStatusController indexController = new IndexerStatusController(indexStatus, gpsIndexer, mediaIndexer, access);
    private final SourceIssueController issueController = new SourceIssueController(issues, access);
    private final SourceReviewController reviewController = new SourceReviewController(sourceReviews, sourceAccess, access);
    private final GarminExportController garminController = new GarminExportController(garmin, helpers, access);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharedDemoRejectsHttpMutationsBeforeAnyWriteOrExternalWork() throws Exception {
        properties.setDemoMode(true);
        var mvc = MockMvcBuilders.standaloneSetup(uploadController, indexController, issueController, reviewController, garminController).build();

        mvc.perform(multipart("/api/gpx-upload/upload").file(new MockMultipartFile("file", "synthetic.gpx", "application/gpx+xml", new byte[]{1})))
                .andExpect(status().isForbidden());
        for (String index : List.of("GPS", "MEDIA")) {
            mvc.perform(post("/api/indexer/{index}/rescan", index)).andExpect(status().isForbidden());
        }
        for (String action : List.of("acknowledgements", "reopen")) {
            mvc.perform(post("/api/source-issues/" + action).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targets\":[{\"issueId\":17,\"sourceRevision\":\"synthetic\"}]}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/track-review/17/assess")).andExpect(status().isForbidden());
        mvc.perform(post("/api/track-review/17/decision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceHash\":\"synthetic\",\"decision\":\"EXCLUDE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/garmin-export/trigger-export")).andExpect(status().isForbidden());
        mvc.perform(post("/api/garmin-export/install-gcexport").param("version", "v4.6.2"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/garmin-export/install-fit-export").param("profile", "default").param("packages", "fit-export"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(uploads, indexStatus, gpsIndexer, mediaIndexer, issues, sourceReviews, sourceAccess, garmin, helpers);
    }

    @Test
    void sharedDemoKeepsStatusAndIssueReadsAvailable() {
        properties.setDemoMode(true);
        authenticate();
        var summary = new SourceIssueSummary(0, 0, List.of());
        var issuePage = new SourceIssuePage(0, List.of(), summary);
        when(issues.list("demo", null, null, null, 0, 50)).thenReturn(issuePage);

        assertFalse(uploadController.getStatus().available());
        assertTrue(uploadController.getStatus().message().contains("shared demo"));
        assertSame(issuePage, issueController.list(null, null, null, 0, 50));
        indexController.getStatus();
        reviewController.detail(17);
        garminController.getToolStatus();

        verify(indexStatus).getIndexSummaries();
        verify(helpers).getToolStatus();
        verify(sourceReviews).detail(17);
        verifyNoInteractions(uploads, gpsIndexer, mediaIndexer, garmin);
    }

    @Test
    void normalInstallationStillForwardsSupportedMutations() throws Exception {
        authenticate();
        var file = new MockMultipartFile("file", "synthetic.gpx", "application/gpx+xml", new byte[]{1});
        when(uploads.saveFile(file)).thenReturn("synthetic.gpx");
        when(gpsIndexer.requestRescan()).thenReturn(FileIndexerImpl.RescanRequestStatus.STARTED);
        when(mediaIndexer.requestRescan()).thenReturn(FileIndexerImpl.RescanRequestStatus.ALREADY_RUNNING);
        var targets = List.of(new SourceIssueTarget(17, "synthetic"));
        var result = new SourceIssueMutationResult(1, 0, 0, 0, targets, new SourceIssueSummary(0, 1, List.of()));
        when(issues.mutate("demo", targets, false)).thenReturn(result);
        when(issues.mutate("demo", targets, true)).thenReturn(result);
        when(garmin.run()).thenReturn("Export completed");

        assertTrue(uploadController.upload(file).getBody().success());
        assertEquals("GPS", indexController.triggerRescan(" gps ").index());
        indexController.triggerRescan("MEDIA");
        assertSame(result, issueController.acknowledge(new SourceIssueMutationRequest(targets)));
        assertSame(result, issueController.reopen(new SourceIssueMutationRequest(targets)));
        reviewController.assess(17);
        var decision = new ReviewDecisionRequest("synthetic", null, Decision.EXCLUDE);
        reviewController.decide(17, decision);
        assertEquals("Export completed", garminController.triggerExport());
        garminController.installGcexport("v4.6.2");
        garminController.installFitExport("default", "fit-export");

        verify(gpsIndexer).requestRescan();
        verify(mediaIndexer).requestRescan();
        verify(sourceReviews).assess(17);
        verify(sourceReviews).decide(17, decision);
        verify(helpers).installGcexport("v4.6.2");
        verify(helpers).installFitExport("default", "fit-export");
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("Demo", "", List.of()));
    }
}
