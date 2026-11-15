package multiroom.radio.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.RadioAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Text a client sent, and text the catalogue sent, never reach the log: not the filters of a search,
 * not a station name or tag, whatever the request ends as. A station is identified by its UUID only.
 */
@SpringBootTest(classes = RadioApiLoggingTest.App.class, properties = "multiroom.radio.request-timeout=800ms")
@AutoConfigureMockMvc
class RadioApiLoggingTest {

    // A plain @Configuration: see RadioApiCatalogueCallsTest.
    @org.springframework.context.annotation.Configuration
    @ImportAutoConfiguration({DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
            RadioAutoConfiguration.class})
    static class App {
    }

    private static final String FILTER_MARKER = "FILTERMARKER7731";
    private static final String CATALOGUE_MARKER = "CATALOGUEMARKER9942";
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

    private Logger logger;
    private Level before;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capture() {
        WIRE.resetAll();
        logger = (Logger) LoggerFactory.getLogger("multiroom.radio");
        before = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
        logger.setLevel(before);
    }

    private static String stationJson(String name, String tags) {
        return "[{\"stationuuid\":\"960e57c5-0601-11e8-ae97-52543be04c81\",\"name\":\"" + name + "\",\"tags\":\"" + tags
                + "\",\"url_resolved\":\"https://s.example.test/a.mp3\",\"codec\":\"MP3\",\"bitrate\":128,"
                + "\"lastcheckok\":1,\"votes\":1,\"clickcount\":1}]";
    }

    private void assertNoMarkerInAnyLogLine() {
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            String text = event.getFormattedMessage();
            if (event.getThrowableProxy() != null) {
                text += ThrowableProxyUtil.asString(event.getThrowableProxy());
            }
            assertThat(text).as(event.getLoggerName() + ": " + text)
                    .doesNotContain(FILTER_MARKER).doesNotContain(CATALOGUE_MARKER);
            // nothing the catalogue sent is quoted at length
            assertThat(event.getFormattedMessage().codePointCount(0, event.getFormattedMessage().length()))
                    .isLessThanOrEqualTo(512);
        }
    }

    @Test
    void anAnsweredSearchLogsNeitherTheFiltersNorTheCatalogueText() throws Exception {
        String longName = CATALOGUE_MARKER + "x".repeat(400);
        WIRE.stubFor(get(urlPathMatching("/json/stations/search"))
                .willReturn(aResponse().withBody(stationJson(longName, CATALOGUE_MARKER + "tag"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?name=" + FILTER_MARKER + "&tag=" + FILTER_MARKER)).andExpect(status().isOk());
        assertThat(appender.list).isNotEmpty();
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void aStationLookupLogsItsUuidOnly() throws Exception {
        WIRE.stubFor(get(urlPathMatching("/json/stations/byuuid"))
                .willReturn(aResponse().withBody(stationJson(CATALOGUE_MARKER, CATALOGUE_MARKER))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations/960e57c5-0601-11e8-ae97-52543be04c81")).andExpect(status().isOk());
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void aTagFilterIsNotLoggedEither() throws Exception {
        WIRE.stubFor(get(urlPathMatching("/json/tags.*")).willReturn(aResponse().withBody("[{\"name\":\"" + CATALOGUE_MARKER
                + "\",\"stationcount\":3}]")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/tags?name=" + FILTER_MARKER)).andExpect(status().isOk());
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void a400LogsNothingFromTheClient() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?name=" + FILTER_MARKER + "%25&" + FILTER_MARKER + "=1"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?" + FILTER_MARKER + "=1")).andExpect(status().isBadRequest());
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void a502LogsNoCatalogueText() throws Exception {
        WIRE.stubFor(get(urlPathMatching("/json/stations/search"))
                .willReturn(aResponse().withBody("{\"error\":\"" + CATALOGUE_MARKER + "\"}")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?name=" + FILTER_MARKER)).andExpect(status().isBadGateway());
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void a503LogsNoText() throws Exception {
        WIRE.stubFor(get(urlPathMatching("/json/stations/search")).willReturn(aResponse().withStatus(503)
                .withBody(CATALOGUE_MARKER)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/radio/stations?name=" + FILTER_MARKER)).andExpect(status().isServiceUnavailable());
        assertNoMarkerInAnyLogLine();
    }

    @Test
    void a500LogsTheRequestPathAndTheExceptionButNoRequestText() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/radio/stations");
        request.setQueryString("name=" + FILTER_MARKER);
        new multiroom.radio.rest.advice.RadioExceptionHandler().internal(new IllegalStateException("boom"), request);
        assertThat(appender.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        assertNoMarkerInAnyLogLine();
    }
}
