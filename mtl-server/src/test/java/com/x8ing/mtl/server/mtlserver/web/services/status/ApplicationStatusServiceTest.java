package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHStatusResponse;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthQueryService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerStatusService;
import com.x8ing.mtl.server.mtlserver.web.services.freshness.DataFreshnessResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.freshness.DataFreshnessService;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchService;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApplicationStatusServiceTest {

    private final DataFreshnessService dataFreshnessService = mock(DataFreshnessService.class);
    private final IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
    private final JobStatusService jobStatusService = mock(JobStatusService.class);
    private final MapServerStatusService mapServerStatusService = mock(MapServerStatusService.class);
    private final LocationSearchService locationSearchService = mock(LocationSearchService.class);
    private final MapServerProperties mapServerProperties = new MapServerProperties();
    private final PlannerProperties plannerProperties = new PlannerProperties();
    private final ObjectProvider<PlannerStatusService> plannerStatusServiceProvider = mock(ObjectProvider.class);
    private final HealthQueryService healthQueryService = mock(HealthQueryService.class);
    private final MtlAppProperties appProperties = new MtlAppProperties();

    private ExecutorService executor;
    private ApplicationStatusService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        service = new ApplicationStatusService(
                dataFreshnessService,
                indexerStatusService,
                jobStatusService,
                mapServerStatusService,
                locationSearchService,
                mapServerProperties,
                plannerProperties,
                plannerStatusServiceProvider,
                healthQueryService,
                appProperties,
                executor
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        executor.shutdownNow();
    }

    @Test
    void onlyInvokesRequestedSections() {
        DataFreshnessResponseDto freshness = mock(DataFreshnessResponseDto.class);
        when(dataFreshnessService.getDataFreshness()).thenReturn(freshness);

        ApplicationStatusResponseDto response = service.getStatus(
                EnumSet.of(ApplicationStatusSection.FRESHNESS));

        assertThat(response.freshness()).isSameAs(freshness);
        assertThat(response.processing()).isNull();
        assertThat(response.services()).isNull();
        assertThat(response.googleHealth()).isNull();
        assertThat(response.errors()).isEmpty();
        verify(indexerStatusService, never()).getIndexSummaries(anyString());
        verify(jobStatusService, never()).getJobSummaries();
        verify(mapServerStatusService, never()).getStatus();
        verify(locationSearchService, never()).getStatus();
        verify(healthQueryService, never()).status();
    }

    @Test
    void preservesHealthySectionsWhenAnotherSectionFails() {
        when(dataFreshnessService.getDataFreshness()).thenThrow(new IllegalStateException("database unavailable"));
        when(indexerStatusService.getIndexSummaries(anyString())).thenReturn(List.of());
        when(jobStatusService.getJobSummaries()).thenReturn(List.of());

        ApplicationStatusResponseDto response = service.getStatus(EnumSet.of(
                ApplicationStatusSection.FRESHNESS,
                ApplicationStatusSection.PROCESSING
        ));

        assertThat(response.freshness()).isNull();
        assertThat(response.processing()).isEqualTo(new ProcessingStatusDto(List.of(), List.of(), null, List.of()));
        assertThat(response.errors()).containsExactly(new ApplicationStatusErrorDto(
                ApplicationStatusSection.FRESHNESS,
                "unavailable",
                "Status section is unavailable."
        ));
    }

    @Test
    void preservesAccountAcknowledgementsAcrossBackgroundStatusReads() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ReViEwEr", "", List.of()));
        var index = new IndexerStatusService.IndexSummaryDto("GPS", 4, 0, 0, 3, 0, 0, 100, false, 1, 0, 3, 0);
        when(indexerStatusService.getIndexSummaries("reviewer")).thenAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return List.of(index);
        });
        when(jobStatusService.getJobSummaries()).thenReturn(List.of());

        var response = service.getStatus(EnumSet.of(ApplicationStatusSection.PROCESSING));

        assertThat(response.processing().indexers()).containsExactly(index);
        assertThat(response.errors()).isEmpty();
        verify(indexerStatusService).getIndexSummaries("reviewer");
    }

    @Test
    void exposesOrdinaryReadOnlyHealthStatusInDemoMode() {
        appProperties.setDemoMode(true);
        service.getStatus(java.util.Set.of(ApplicationStatusSection.GOOGLE_HEALTH));
        org.mockito.Mockito.verify(healthQueryService).status();
    }

    @Test
    void returnsGoogleHealthStatusOutsideDemoMode() {
        GHStatusResponse health = mock(GHStatusResponse.class);
        when(healthQueryService.status()).thenReturn(health);

        ApplicationStatusResponseDto response = service.getStatus(
                EnumSet.of(ApplicationStatusSection.GOOGLE_HEALTH));

        assertThat(response.googleHealth()).isSameAs(health);
        assertThat(response.errors()).isEmpty();
        verify(healthQueryService).status();
    }

    @Test
    void runsSelectedSectionsConcurrently() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        DataFreshnessResponseDto freshness = mock(DataFreshnessResponseDto.class);
        GHStatusResponse health = mock(GHStatusResponse.class);
        when(dataFreshnessService.getDataFreshness()).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return freshness;
        });
        when(healthQueryService.status()).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return health;
        });

        CompletableFuture<ApplicationStatusResponseDto> response = CompletableFuture.supplyAsync(() ->
                service.getStatus(EnumSet.of(
                        ApplicationStatusSection.FRESHNESS,
                        ApplicationStatusSection.GOOGLE_HEALTH)));

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(response.get(1, TimeUnit.SECONDS).freshness()).isSameAs(freshness);
    }

    @Test
    void doesNotResolvePlannerServiceWhenPlannerIsDisabled() {
        plannerProperties.setEnabled(false);

        ApplicationStatusResponseDto response = service.getStatus(
                EnumSet.of(ApplicationStatusSection.SERVICES));

        assertThat(response.services()).isNotNull();
        assertThat(response.services().plannerEnabled()).isFalse();
        assertThat(response.services().planner()).isNull();
        verify(plannerStatusServiceProvider, never()).getIfAvailable();
    }
    @Test
    void knownDependencyVersionOmitsStaticEdgesButKeepsLiveReports() {
        when(indexerStatusService.getIndexSummaries(anyString())).thenReturn(List.of());
        when(jobStatusService.getJobSummaries()).thenReturn(List.of());
        when(jobStatusService.getDependencies()).thenReturn(List.of());
        when(jobStatusService.dependenciesVersion(List.of())).thenReturn("version-1");
        var first = service.getStatus(EnumSet.of(ApplicationStatusSection.PROCESSING));
        assertThat(first.processing().dependencies()).isEmpty();
        var second = service.getStatus(EnumSet.of(ApplicationStatusSection.PROCESSING), "version-1");
        assertThat(second.processing().dependencies()).isNull();
        assertThat(second.processing().dependenciesVersion()).isEqualTo("version-1");
        assertThat(second.processing().jobs()).isEmpty();
    }

}
