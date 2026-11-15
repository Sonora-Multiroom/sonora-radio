package multiroom.radio.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.RadioAutoConfiguration;
import multiroom.radio.support.Fixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the API asks the real catalogue for, seen from the WireMock journal: never a play report, a
 * vote or a lookup by address; nothing for a refused request or one answered from memory; nothing at
 * all just because the context started.
 */
@SpringBootTest(classes = RadioApiCatalogueCallsTest.App.class)
// A fresh context per test: what is remembered (stations, lists) must not carry over between tests.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
class RadioApiCatalogueCallsTest {

    // A plain @Configuration: a second @SpringBootConfiguration under multiroom.radio would confuse the
    // @WebMvcTest slices, which look for one by walking up the packages.
    @org.springframework.context.annotation.Configuration
    @ImportAutoConfiguration({DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
            RadioAutoConfiguration.class})
    static class App {
    }

    private static final String ID = "960e57c5-0601-11e8-ae97-52543be04c81";
    private static final WireMockServer WIRE = new WireMockServer(options().dynamicPort());

    @DynamicPropertySource
    static void servers(DynamicPropertyRegistry registry) {
        if (!WIRE.isRunning()) {
            WIRE.start();
        }
        registry.add("multiroom.radio.servers[0]", () -> "http://localhost:" + WIRE.port());
    }

    @AfterAll
    static void stop() {
        WIRE.stop();
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() {
        WIRE.resetAll();
        WIRE.stubFor(get(urlPathEqualTo("/json/stations/search"))
                .willReturn(aResponse().withBody(Fixtures.load("search-page.json"))));
        WIRE.stubFor(get(urlPathEqualTo("/json/stations/byuuid"))
                .willReturn(aResponse().withBody(Fixtures.load("station-mp3.json"))));
        WIRE.stubFor(get(urlPathEqualTo("/json/countries")).willReturn(aResponse().withBody(Fixtures.load("countries.json"))));
        WIRE.stubFor(get(urlPathEqualTo("/json/languages")).willReturn(aResponse().withBody(Fixtures.load("languages.json"))));
        WIRE.stubFor(get(urlPathMatching("/json/tags.*")).willReturn(aResponse().withBody(Fixtures.load("tags-page.json"))));
    }

    private List<String> paths() {
        return WIRE.getAllServeEvents().stream().map(e -> e.getRequest().getUrl()).toList();
    }

    @Test
    void theContextStartedWithoutAnyCatalogueRequest() {
        assertThat(paths()).isEmpty();
    }

    @Test
    void oneRequestOfEachKindNeverReportsAPlayCastsAVoteOrLooksUpByAddress() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations/" + Fixtures.STATION)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/countries")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/languages")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/tags")).andExpect(status().isOk());
        assertThat(paths()).hasSize(5);
        assertThat(paths()).noneMatch(p -> p.startsWith("/json/url/"))
                .noneMatch(p -> p.startsWith("/json/vote/"))
                .noneMatch(p -> p.startsWith("/json/stations/byurl"));
    }

    @Test
    void aRefusedRequestMakesNoCatalogueRequestAtAll() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?limit=0")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?bogus=1")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/tags?name=a/b")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/countries?x=1")).andExpect(status().isBadRequest());
        assertThat(paths()).isEmpty();
    }

    @Test
    void aRequestAnsweredFromMemoryMakesNoCatalogueRequest() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations/" + Fixtures.STATION)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/countries")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/languages")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/tags?limit=7")).andExpect(status().isOk());
        int before = paths().size();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations/" + Fixtures.STATION.toString().toUpperCase())).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/countries")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/languages")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/tags?limit=7")).andExpect(status().isOk());
        assertThat(paths()).hasSize(before);
    }
}
