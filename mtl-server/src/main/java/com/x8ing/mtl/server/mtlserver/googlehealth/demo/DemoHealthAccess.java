package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Demo-only opt-in used exclusively by the generator.
 */
@Component
@RequiredArgsConstructor
public class DemoHealthAccess {
    private final MtlAppProperties appProperties;
    private final DemoHealthProperties properties;

    public boolean enabled() {
        return appProperties.isDemoMode() && properties.isEnabled();
    }

}
