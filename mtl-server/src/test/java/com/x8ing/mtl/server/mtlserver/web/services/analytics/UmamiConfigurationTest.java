package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import com.x8ing.mtl.server.mtlserver.config.UmamiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UmamiConfigurationTest {
    private static final String SCRIPT_URL = "https://stats.example.com:8443/script.js";
    private static final String WEBSITE_ID = "12345678-1234-1234-1234-123456789abc";

    @Test
    void defaultsAreDisabled() {
        assertDisabled(new UmamiConfiguration(new UmamiProperties()));
    }

    @Test
    void explicitOffSwitchOverridesCompleteSettings() {
        UmamiProperties settings = enabledSettings();
        settings.setEnabled(false);
        assertDisabled(new UmamiConfiguration(settings));
    }

    @Test
    void enabledSettingsWorkWithoutDemoConfigurationAndExposeOnlyTheConfiguredScriptOrigin() {
        UmamiConfiguration configuration = new UmamiConfiguration(enabledSettings());
        assertEquals(new UmamiConfigResponse(true, SCRIPT_URL, WEBSITE_ID), configuration.getClientConfig());
        assertEquals("https://stats.example.com:8443", configuration.getScriptSource());
        var response = new UmamiAnalyticsController(configuration).getUmamiConfig();
        assertEquals(configuration.getClientConfig(), response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"http://stats.example.com/script.js", "//stats.example.com/script.js",
            "javascript:alert(1)", "https://user:password@stats.example.com/script.js",
            "https://stats.example.com/script.js?token=secret", "https://stats.example.com/script.js#hash",
            "https://stats.example.com/; script-src *", "not a URL"})
    void invalidScriptUrlsDisableAnalyticsWithoutWeakeningCsp(String scriptUrl) {
        UmamiProperties settings = enabledSettings();
        settings.setScriptUrl(scriptUrl);
        assertDisabled(new UmamiConfiguration(settings));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-id", "1-1-1-1-1"})
    void missingOrInvalidWebsiteIdsDisableAnalytics(String websiteId) {
        UmamiProperties settings = enabledSettings();
        settings.setWebsiteId(websiteId);
        assertDisabled(new UmamiConfiguration(settings));
    }

    private static void assertDisabled(UmamiConfiguration configuration) {
        assertEquals(UmamiConfigResponse.DISABLED, configuration.getClientConfig());
        assertEquals("", configuration.getScriptSource());
    }

    private static UmamiProperties enabledSettings() {
        UmamiProperties settings = new UmamiProperties();
        settings.setEnabled(true);
        settings.setScriptUrl(SCRIPT_URL);
        settings.setWebsiteId(WEBSITE_ID);
        return settings;
    }
}
