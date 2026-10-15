package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import com.x8ing.mtl.server.mtlserver.web.services.info.ImageVersionInfoDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MapTileProxyForwardingTest {

    private final BlockingQueue<UpstreamRequest> requests = new LinkedBlockingQueue<>();
    private HttpServer upstream;
    private MapTileProxyController controller;
    private MtlAppProperties appProperties;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/prod/planet.pmtiles", exchange -> {
            Headers headers = new Headers();
            headers.putAll(exchange.getRequestHeaders());
            requests.add(new UpstreamRequest(exchange.getRequestURI(), headers));
            byte[] body = "tile".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes 0-3/100");
            exchange.sendResponseHeaders(206, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();
        MapUpstreamResolver resolver = mock(MapUpstreamResolver.class);
        when(resolver.resolveUpstream()).thenReturn(new MapUpstream(
                MapUpstreamSource.PUBLIC, "http://127.0.0.1:" + upstream.getAddress().getPort()));
        ServerIdentityService identity = mock(ServerIdentityService.class);
        when(identity.getServerId()).thenReturn("test-server");
        DockerImageInfoService imageInfoService = mock(DockerImageInfoService.class);
        when(imageInfoService.getImageInfo()).thenReturn(
                ImageVersionInfoDto.of("1.519", null, "2026-10-06T10:20:30Z"));
        appProperties = new MtlAppProperties();
        controller = new MapTileProxyController(new MapServerProperties(), appProperties, resolver,
                mock(MapServerStatusService.class), identity,
                new UpstreamBuildVersionService(imageInfoService, Optional.empty()));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.shutdownHttpClient();
        }
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "198.51.100.23, 198.51.100.23",
            "2001:db8::42, 2001:db8:0:0:0:0:0:42"
    })
    void forwardsDirectClientAddressAndPreservesRangeAndIdentifiers(String clientIp, String expectedIp) throws Exception {
        UpstreamRequest sent = perform(mapRequest(clientIp));

        assertClientIp(sent, expectedIp);
        assertThat(sent.headers().getFirst(HttpHeaders.RANGE)).isEqualTo("bytes=0-3");
        assertThat(sent.uri().getQuery()).contains("mtl-version=1.519&mtl-build=2026-10-06T10_20_30Z",
                "mtl-server-id=test-server", "mtl-client-login-ref=test-login");
        assertThat(sent.uri().getQuery()).doesNotContain(clientIp, expectedIp);
        assertThat(sent.headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(sent.headers().getFirst(HttpHeaders.COOKIE)).isNull();
    }

    @Test
    void usesRunningContainerIdentityInsteadOfBrowserSuppliedVersion() throws Exception {
        UpstreamRequest sent = perform(mapRequest("198.51.100.23")
                .param(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM, "client-supplied-version")
                .param(MapProxyConstants.UPSTREAM_BUILD_PARAM, "client-supplied-build"));

        assertThat(sent.uri().getQuery()).contains("mtl-version=1.519&mtl-build=2026-10-06T10_20_30Z")
                .doesNotContain("client-supplied-version", "client-supplied-build");
    }

    @ParameterizedTest
    @CsvSource({"true, demo, standard", "false, standard, demo"})
    void instanceTypeComesFromDemoModeInsteadOfBrowserQuery(
            boolean demoMode, String expectedType, String browserSuppliedType) throws Exception {
        appProperties.setDemoMode(demoMode);

        UpstreamRequest sent = perform(mapRequest("198.51.100.23")
                .param(MapProxyConstants.UPSTREAM_INSTANCE_TYPE_PARAM, browserSuppliedType));

        assertThat(sent.uri().getQuery())
                .contains("mtl-instance-type=" + expectedType)
                .doesNotContain("mtl-instance-type=" + browserSuppliedType);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "X-Forwarded-For | 198.51.100.23, 10.0.0.8 | 198.51.100.23",
            "X-Forwarded-For | 2001:db8::42, 10.0.0.8 | 2001:db8:0:0:0:0:0:42",
            "Forwarded | for=198.51.100.23;proto=https | 198.51.100.23",
            "Forwarded | for=\"[2001:db8::42]:54321\";proto=https | 2001:db8:0:0:0:0:0:42"
    })
    void forwardsAddressResolvedByConfiguredSpringFilter(String name, String value, String expectedIp) throws Exception {
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new ForwardedHeaderFilter()).build();

        UpstreamRequest sent = perform(mapRequest("10.0.0.9").header(name, value));

        assertClientIp(sent, expectedIp);
    }

    @Test
    void doesNotCopyUnprocessedClientSuppliedForwardingHeaders() throws Exception {
        UpstreamRequest sent = perform(mapRequest("198.51.100.23")
                .header("X-Forwarded-For", "203.0.113.99, 10.0.0.8")
                .header("X-Real-IP", "203.0.113.99")
                .header("Forwarded", "for=203.0.113.99"));

        assertClientIp(sent, "198.51.100.23");
        assertThat(sent.headers().getFirst("Forwarded")).isNull();
    }

    @Test
    void respectsForwardingFilterConfiguredToRemoveUntrustedHeaders() throws Exception {
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        filter.setRemoveOnly(true);
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(filter).build();

        UpstreamRequest sent = perform(mapRequest("198.51.100.23")
                .header("X-Forwarded-For", "203.0.113.99"));

        assertClientIp(sent, "198.51.100.23");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown", "not-an-ip.example.test", "198.51.100.23, 203.0.113.1"})
    void missingOrInvalidAddressDoesNotBreakMapTransfer(String clientIp) throws Exception {
        UpstreamRequest sent = perform(mapRequest(clientIp));

        assertThat(sent.headers().getFirst("X-Forwarded-For")).isNull();
        assertThat(sent.headers().getFirst("X-Real-IP")).isNull();
    }

    private static MockHttpServletRequestBuilder mapRequest(String remoteAddress) {
        return get("/api/map-proxy/prod/planet.pmtiles")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "test-login")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.AUTHORIZATION, "Bearer browser-token")
                .header(HttpHeaders.COOKIE, "browser-session=private");
    }

    private UpstreamRequest perform(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/100"))
                .andExpect(content().string("tile"));
        UpstreamRequest sent = requests.poll(2, TimeUnit.SECONDS);
        assertThat(sent).isNotNull();
        return sent;
    }

    private static void assertClientIp(UpstreamRequest request, String expectedIp) {
        assertThat(request.headers().getFirst("X-Forwarded-For")).isEqualTo(expectedIp);
        assertThat(request.headers().getFirst("X-Real-IP")).isEqualTo(expectedIp);
    }

    private record UpstreamRequest(URI uri, Headers headers) {
    }
}
