package multiroom.radio.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.RadioAutoConfiguration;
import multiroom.radio.support.Fixtures;
import multiroom.radio.support.Wire;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 2-second promise of the spec, scaled to a 600 ms budget so that no test waits for seconds: a
 * fast first mirror answers well inside the budget, a stalled or refused catalogue answers 503 within
 * it, a slow first mirror does not eat the second one's turn, and no shared lock is held across a
 * catalogue call.
 */
@SpringBootTest(classes = RadioApiTimeBudgetTest.App.class, properties = "multiroom.radio.request-timeout=600ms")
@AutoConfigureMockMvc
class RadioApiTimeBudgetTest {

    private static final Duration BUDGET = Duration.ofMillis(600);
    private static final Duration MARGIN = Duration.ofMillis(500);
    private static final String SEARCH = "/json/stations/search";

    // A plain @Configuration: a second @SpringBootConfiguration under multiroom.radio would confuse the
    // @WebMvcTest slices, which look for one by walking up the packages.
    @org.springframework.context.annotation.Configuration
    @ImportAutoConfiguration({DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
            RadioAutoConfiguration.class})
    static class App {
    }

    private static final WireMockServer FIRST = new WireMockServer(options().dynamicPort());
    private static final WireMockServer SECOND = new WireMockServer(options().dynamicPort());

    @DynamicPropertySource
    static void servers(DynamicPropertyRegistry registry) {
        if (!FIRST.isRunning()) {
            FIRST.start();
            SECOND.start();
        }
        registry.add("multiroom.radio.servers[0]", () -> "http://localhost:" + FIRST.port());
        registry.add("multiroom.radio.servers[1]", () -> "http://localhost:" + SECOND.port());
    }

    @BeforeAll
    static void warm() {
        Wire.warmUp();
    }

    @AfterAll
    static void stop() {
        FIRST.stop();
        SECOND.stop();
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() {
        FIRST.resetAll();
        SECOND.resetAll();
    }

    private static void answers(WireMockServer server, String fixture) {
        server.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withBody(Fixtures.load(fixture))));
    }

    private static void stalls(WireMockServer server) {
        server.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withFixedDelay(5000).withBody("[]")));
    }

    private Duration timed(String path, int expectedStatus) throws Exception {
        long start = System.nanoTime();
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andReturn();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        return elapsed;
    }

    @Test
    void aFastFirstServerAnswersWellInsideTheBudget() throws Exception {
        answers(FIRST, "search-page.json");
        timed("/api/radio/stations", 200);                         // one request to warm the context's own paths
        Duration elapsed = timed("/api/radio/stations", 200);
        assertThat(elapsed).isLessThan(BUDGET.dividedBy(2));
        assertThat(SECOND.getAllServeEvents()).isEmpty();
    }

    @Test
    void everyServerStallingAnswers503WithinTheBudgetPlusAMargin() throws Exception {
        stalls(FIRST);
        stalls(SECOND);
        Duration elapsed = timed("/api/radio/stations", 503);
        assertThat(elapsed).isLessThan(BUDGET.plus(MARGIN));
    }

    @Test
    void everyServerRefusingAnswers503Quickly() throws Exception {
        FIRST.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withStatus(503)));
        SECOND.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withStatus(500)));
        assertThat(timed("/api/radio/stations", 503)).isLessThan(BUDGET.plus(MARGIN));
    }

    @Test
    void aSlowFirstServerLeavesTimeForTheSecond() throws Exception {
        stalls(FIRST);
        answers(SECOND, "search-page.json");
        Duration elapsed = timed("/api/radio/stations", 200);
        assertThat(elapsed).isLessThan(BUDGET.plus(MARGIN));
    }

    @Test
    void a4xxFromTheCatalogueIs502WithNoFailover() throws Exception {
        FIRST.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withStatus(400)));
        answers(SECOND, "search-page.json");
        timed("/api/radio/stations", 502);
        assertThat(SECOND.getAllServeEvents()).isEmpty();
    }

    @Test
    void anUnreadableBodyIs502() throws Exception {
        FIRST.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(aResponse().withBody("<html>not json")));
        timed("/api/radio/stations", 502);
    }

    @Test
    void whileASlowSearchRunsAnotherRequestIsAnsweredAtOnce() throws Exception {
        stalls(FIRST);
        stalls(SECOND);
        CompletableFuture<Void> slow = CompletableFuture.runAsync(() -> {
            try {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(100);
        Duration other = timed("/api/radio/nothing", 404);
        assertThat(other).isLessThan(Duration.ofMillis(300));
        slow.get(5, TimeUnit.SECONDS);
    }
}
