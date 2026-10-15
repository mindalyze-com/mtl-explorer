package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHStatusResponse;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthQueryService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.BRouterStatusDto;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerStatusService;
import com.x8ing.mtl.server.mtlserver.sourceissues.IssueAccount;
import com.x8ing.mtl.server.mtlserver.web.services.freshness.DataFreshnessResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.freshness.DataFreshnessService;
import com.x8ing.mtl.server.mtlserver.web.services.map.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Slf4j
@Service
@JsonPropertyOrder({
        "dataFreshnessService",
        "indexerStatusService",
        "jobStatusService",
        "mapServerStatusService",
        "locationSearchService",
        "mapServerProperties",
        "plannerProperties",
        "plannerStatusServiceProvider",
        "healthQueryService",
        "appProperties",
        "executor"
})
public class ApplicationStatusService {

    private static final String ERROR_CODE_UNAVAILABLE = "unavailable";
    private static final String ERROR_CODE_UNAVAILABLE_IN_DEMO = "unavailable-in-demo";

    private final DataFreshnessService dataFreshnessService;
    private final IndexerStatusService indexerStatusService;
    private final JobStatusService jobStatusService;
    private final MapServerStatusService mapServerStatusService;
    private final LocationSearchService locationSearchService;
    private final MapServerProperties mapServerProperties;
    private final PlannerProperties plannerProperties;
    private final ObjectProvider<PlannerStatusService> plannerStatusServiceProvider;
    private final HealthQueryService healthQueryService;
    private final MtlAppProperties appProperties;
    private final ExecutorService executor;

    public ApplicationStatusService(
            DataFreshnessService dataFreshnessService,
            IndexerStatusService indexerStatusService,
            JobStatusService jobStatusService,
            MapServerStatusService mapServerStatusService,
            LocationSearchService locationSearchService,
            MapServerProperties mapServerProperties,
            PlannerProperties plannerProperties,
            ObjectProvider<PlannerStatusService> plannerStatusServiceProvider,
            HealthQueryService healthQueryService,
            MtlAppProperties appProperties,
            @Qualifier(ApplicationStatusExecutorConfig.APPLICATION_STATUS_EXECUTOR) ExecutorService executor
    ) {
        this.dataFreshnessService = dataFreshnessService;
        this.indexerStatusService = indexerStatusService;
        this.jobStatusService = jobStatusService;
        this.mapServerStatusService = mapServerStatusService;
        this.locationSearchService = locationSearchService;
        this.mapServerProperties = mapServerProperties;
        this.plannerProperties = plannerProperties;
        this.plannerStatusServiceProvider = plannerStatusServiceProvider;
        this.healthQueryService = healthQueryService;
        this.appProperties = appProperties;
        this.executor = executor;
    }

    public ApplicationStatusResponseDto getStatus(Set<ApplicationStatusSection> included) {
        return getStatus(included, null);
    }

    public ApplicationStatusResponseDto getStatus(Set<ApplicationStatusSection> included, String dependenciesVersion) {
        String account = IssueAccount.current();
        Map<ApplicationStatusSection, CompletableFuture<SectionResult<?>>> futures =
                new EnumMap<>(ApplicationStatusSection.class);

        submitIfIncluded(included, futures, ApplicationStatusSection.FRESHNESS, dataFreshnessService::getDataFreshness);
        submitIfIncluded(included, futures, ApplicationStatusSection.PROCESSING, () -> processingStatus(account, dependenciesVersion));
        submitIfIncluded(included, futures, ApplicationStatusSection.SERVICES, this::servicesStatus);
        submitIfIncluded(included, futures, ApplicationStatusSection.GOOGLE_HEALTH, healthQueryService::status);

        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        List<ApplicationStatusErrorDto> errors = new ArrayList<>();
        DataFreshnessResponseDto freshness = value(futures, ApplicationStatusSection.FRESHNESS,
                DataFreshnessResponseDto.class, errors);
        ProcessingStatusDto processing = value(futures, ApplicationStatusSection.PROCESSING,
                ProcessingStatusDto.class, errors);
        ServicesStatusDto services = value(futures, ApplicationStatusSection.SERVICES,
                ServicesStatusDto.class, errors);
        GHStatusResponse googleHealth = value(futures, ApplicationStatusSection.GOOGLE_HEALTH,
                GHStatusResponse.class, errors);

        return new ApplicationStatusResponseDto(
                Instant.now(),
                Set.copyOf(included),
                freshness,
                processing,
                services,
                googleHealth,
                List.copyOf(errors)
        );
    }

    private ProcessingStatusDto processingStatus(String account, String knownVersion) {
        CompletableFuture<List<IndexerStatusService.IndexSummaryDto>> indexers =
                CompletableFuture.supplyAsync(() -> indexerStatusService.getIndexSummaries(account), executor);
        CompletableFuture<List<JobStatusService.JobSummaryDto>> jobs =
                CompletableFuture.supplyAsync(jobStatusService::getJobSummaries, executor);
        var dependencies = jobStatusService.getDependencies();
        String version = jobStatusService.dependenciesVersion(dependencies);
        return new ProcessingStatusDto(indexers.join(), jobs.join(), version,
                version != null && version.equals(knownVersion) ? null : dependencies);
    }

    private ServicesStatusDto servicesStatus() {
        CompletableFuture<MapServerStatusDto> map =
                CompletableFuture.supplyAsync(mapServerStatusService::getStatus, executor);
        CompletableFuture<LocationSearchStatusDto> locationSearch =
                CompletableFuture.supplyAsync(locationSearchService::getStatus, executor);
        CompletableFuture<BRouterStatusDto> planner = plannerProperties.isEnabled()
                ? CompletableFuture.supplyAsync(this::plannerStatus, executor)
                : CompletableFuture.completedFuture(null);
        return new ServicesStatusDto(
                mapServerProperties.getTileMode(),
                plannerProperties.isEnabled(),
                map.join(),
                locationSearch.join(),
                planner.join()
        );
    }

    private BRouterStatusDto plannerStatus() {
        PlannerStatusService plannerStatusService = plannerStatusServiceProvider.getIfAvailable();
        return plannerStatusService == null
                ? BRouterStatusDto.unavailable("planner-disabled")
                : plannerStatusService.fetchStatus();
    }

    private <T> void submitIfIncluded(
            Set<ApplicationStatusSection> included,
            Map<ApplicationStatusSection, CompletableFuture<SectionResult<?>>> futures,
            ApplicationStatusSection section,
            Supplier<T> supplier
    ) {
        if (!included.contains(section)) {
            return;
        }
        futures.put(section, CompletableFuture.supplyAsync(() -> {
            try {
                return SectionResult.success(supplier.get());
            } catch (RuntimeException exception) {
                log.warn("Application status section failed: section={}, error={}",
                        section.apiValue(), safeError(exception));
                return SectionResult.failure(ERROR_CODE_UNAVAILABLE, "Status section is unavailable.");
            }
        }, executor));
    }

    private static <T> T value(
            Map<ApplicationStatusSection, CompletableFuture<SectionResult<?>>> futures,
            ApplicationStatusSection section,
            Class<T> type,
            List<ApplicationStatusErrorDto> errors
    ) {
        CompletableFuture<SectionResult<?>> future = futures.get(section);
        if (future == null) {
            return null;
        }
        SectionResult<?> result = future.join();
        if (result.errorCode() != null) {
            errors.add(new ApplicationStatusErrorDto(section, result.errorCode(), result.errorMessage()));
            return null;
        }
        return type.cast(result.value());
    }

    private static String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n\\t]", " ");
    }

    private record SectionResult<T>(T value, String errorCode, String errorMessage) {
        private static <T> SectionResult<T> success(T value) {
            return new SectionResult<>(value, null, null);
        }

        private static <T> SectionResult<T> failure(String code, String message) {
            return new SectionResult<>(null, code, message);
        }
    }
}
