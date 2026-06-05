package com.x8ing.mtl.server.mtlserver.web.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaForwardingControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SpaForwardingController()).build();

    @Test
    void forwardsSingleSegmentSpaRoute() throws Exception {
        mockMvc.perform(get("/admin").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsSingleSegmentSpaRouteWithContextPath() throws Exception {
        mockMvc.perform(get("/mtl/admin").contextPath("/mtl").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsNestedSpaRoute() throws Exception {
        mockMvc.perform(get("/track/100000").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void leavesExplicitIndexPathToStaticResourceHandling() throws Exception {
        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void doesNotForwardApiRoute() throws Exception {
        mockMvc.perform(get("/api/unknown").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotForwardStaticLookingRoute() throws Exception {
        mockMvc.perform(get("/unknown/app.js").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotForwardNonHtmlRequest() throws Exception {
        mockMvc.perform(get("/admin").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
