package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-freshness")
public class DataFreshnessController {

    private final DataFreshnessService dataFreshnessService;

    public DataFreshnessController(DataFreshnessService dataFreshnessService) {
        this.dataFreshnessService = dataFreshnessService;
    }

    @Operation(operationId = "getDataFreshness")
    @GetMapping
    public DataFreshnessResponseDto getDataFreshness() {
        return dataFreshnessService.getDataFreshness();
    }
}
