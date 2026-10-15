package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class UmamiAnalyticsController {
    private final UmamiConfiguration umami;

    public UmamiAnalyticsController(UmamiConfiguration umami) {
        this.umami = umami;
    }

    @GetMapping("/umami-config")
    @SecurityRequirements
    public ResponseEntity<UmamiConfigResponse> getUmamiConfig() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(umami.getClientConfig());
    }
}
