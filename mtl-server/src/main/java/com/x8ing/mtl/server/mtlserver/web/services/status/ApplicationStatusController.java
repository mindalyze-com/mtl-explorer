package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
@JsonPropertyOrder({
        "applicationStatusService"
})
public class ApplicationStatusController {

    private final ApplicationStatusService applicationStatusService;

    @Operation(operationId = "getApplicationStatus")
    @GetMapping
    public ApplicationStatusResponseDto getStatus(
            @Parameter(
                    description = "Status sections to include. Omit to include every section.",
                    array = @ArraySchema(schema = @Schema(
                            allowableValues = {"freshness", "processing", "services", "google-health"}))
            )
            @RequestParam(value = "include", required = false) List<String> include,
            @Parameter(description = "Previously received processing dependency version. Unchanged edges are omitted.")
            @RequestParam(value = "dependenciesVersion", required = false) String dependenciesVersion
    ) {
        return applicationStatusService.getStatus(parseIncludedSections(include), dependenciesVersion);
    }

    static Set<ApplicationStatusSection> parseIncludedSections(List<String> requested) {
        if (requested == null) {
            return EnumSet.allOf(ApplicationStatusSection.class);
        }
        if (requested.isEmpty()) {
            throw badInclude();
        }

        EnumSet<ApplicationStatusSection> included = EnumSet.noneOf(ApplicationStatusSection.class);
        try {
            for (String raw : requested) {
                String[] values = raw == null ? new String[0] : raw.split(",", -1);
                if (values.length == 0) {
                    throw new IllegalArgumentException();
                }
                for (String value : values) {
                    if (value.isBlank()) {
                        throw new IllegalArgumentException();
                    }
                    included.add(ApplicationStatusSection.fromApiValue(value));
                }
            }
        } catch (IllegalArgumentException exception) {
            throw badInclude();
        }

        if (included.isEmpty()) {
            throw badInclude();
        }
        return included;
    }

    private static ResponseStatusException badInclude() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "include must contain freshness, processing, services, or google-health");
    }
}
