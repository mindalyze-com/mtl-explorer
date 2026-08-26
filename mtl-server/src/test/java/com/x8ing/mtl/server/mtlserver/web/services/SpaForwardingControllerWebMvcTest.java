package com.x8ing.mtl.server.mtlserver.web.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SpaForwardingController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {
        SpaForwardingControllerWebMvcTest.TestApplication.class,
        SpaForwardingController.class
})
@TestPropertySource(properties = "server.servlet.context-path=/mtl")
class SpaForwardingControllerWebMvcTest {

    private static final String CONTEXT_PATH = "/mtl";
    private static final String TEST_INDEX_MARKER = "mtl-spa-test-index";

    @Autowired
    private MockMvc mockMvc;

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Test
    void contextRootUsesSpringWelcomePageForward() throws Exception {
        mockMvc.perform(get("/mtl/").contextPath(CONTEXT_PATH).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void servesExplicitContextIndexFromStaticResources() throws Exception {
        mockMvc.perform(get("/mtl/index.html").contextPath(CONTEXT_PATH).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(ResourceHttpRequestHandler.class))
                .andExpect(content().string(containsString(TEST_INDEX_MARKER)));
    }

    @Test
    void forwardsContextSpaRoutesToStaticIndex() throws Exception {
        mockMvc.perform(get("/mtl/admin").contextPath(CONTEXT_PATH).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
