package multiroom.radio.catalogue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.RadioVersion;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.Wire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogueHttpTest {

    private WireMockServer wire;
    private RadioProperties properties;

    @org.junit.jupiter.api.BeforeAll
    static void warm() {
        Wire.warmUp();
    }

    @BeforeEach
    void start() {
        wire = new WireMockServer(options().dynamicPort());
        wire.start();
        properties = new RadioProperties();
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
    }

    private void shortTimeouts() {
        properties.setConnectTimeout(Duration.ofMillis(300));
        properties.setReadTimeout(Duration.ofMillis(300));
    }

    @AfterEach
    void stop() {
        wire.stop();
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + wire.port() + path);
    }

    private static Deadline deadline() {
        return Deadline.after(Clock.systemUTC(), Duration.ofSeconds(5));
    }

    @Test
    void sendsTheDefaultUserAgentAndAcceptHeader() {
        wire.stubFor(get("/x").willReturn(aResponse().withBody("hello")));
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/x"), deadline());
        assertThat(attempt.ok()).isTrue();
        assertThat(attempt.body()).isEqualTo("hello");
        wire.verify(getRequestedFor(urlEqualTo("/x"))
                .withHeader("User-Agent", equalTo("sonora-radio/" + RadioVersion.version()))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void aConfiguredUserAgentReplacesTheDefault() {
        properties.setUserAgent("custom/9");
        wire.stubFor(get("/x").willReturn(aResponse().withBody("{}")));
        new CatalogueHttp(properties).get(url("/x"), deadline());
        wire.verify(getRequestedFor(urlEqualTo("/x")).withHeader("User-Agent", equalTo("custom/9")));
    }

    @Test
    void classifiesStatusCodes() {
        wire.stubFor(get("/404").willReturn(aResponse().withStatus(404)));
        wire.stubFor(get("/429").willReturn(aResponse().withStatus(429)));
        wire.stubFor(get("/503").willReturn(aResponse().withStatus(503)));
        wire.stubFor(get("/302").willReturn(aResponse().withStatus(304)));
        CatalogueHttp http = new CatalogueHttp(properties);
        assertThat(http.get(url("/404"), deadline()).result()).isEqualTo("http_4xx");
        assertThat(http.get(url("/429"), deadline()).result()).isEqualTo("http_429");
        assertThat(http.get(url("/503"), deadline()).result()).isEqualTo("http_5xx");
        assertThat(http.get(url("/302"), deadline()).result()).isEqualTo("http_4xx");
    }

    @Test
    void aClosedPortIsAConnectError() {
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties)
                .get(URI.create("http://localhost:" + Wire.closedPort() + "/x"), deadline());
        assertThat(attempt.result()).isEqualTo("connect_error");
        assertThat(attempt.body()).isNull();
    }

    @Test
    void aSlowAnswerIsATimeoutWithinTheLimit() {
        shortTimeouts();
        wire.stubFor(get("/slow").willReturn(aResponse().withFixedDelay(3000).withBody("late")));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/slow"), deadline());
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void aBodyThatStallsAfterTheHeadersIsATimeoutWithinTheLimit() {
        shortTimeouts();
        wire.stubFor(get("/stall").willReturn(aResponse().withBody("x".repeat(100)).withChunkedDribbleDelay(10, 5000)));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/stall"), deadline());
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void theWaitNeverExceedsWhatIsLeftOfTheDeadline() {
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(5));
        wire.stubFor(get("/slow").willReturn(aResponse().withFixedDelay(3000).withBody("late")));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties)
                .get(url("/slow"), Deadline.after(Clock.systemUTC(), Duration.ofMillis(400)));
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void anExpiredDeadlineSendsNothing() {
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties)
                .get(url("/x"), Deadline.after(Clock.systemUTC(), Duration.ZERO));
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(wire.getAllServeEvents()).isEmpty();
    }

    @Test
    void debugLogsTheUrlAndTheUserAgent() {
        Logger logger = (Logger) LoggerFactory.getLogger(CatalogueHttp.class);
        Level before = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            wire.stubFor(get("/x").willReturn(aResponse().withBody("{}")));
            new CatalogueHttp(properties).get(url("/x"), deadline());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(before);
        }
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("/x").contains("sonora-radio/");
        });
    }

    @Test
    void constructingOpensNoConnection() {
        new CatalogueHttp(properties);
        assertThat(wire.getAllServeEvents()).isEmpty();
    }

    @Test
    void anAttemptLimitGivesUpOnAStalledServerNearTheLimitAndCountsATimeout() {
        wire.stubFor(get("/slow").willReturn(aResponse().withFixedDelay(3000).withBody("late")));
        Deadline limited = Deadline.after(Clock.systemUTC(), Duration.ofSeconds(5), Duration.ofMillis(300));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/slow"), limited);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(250)).isLessThan(Duration.ofMillis(1000));
    }

    @Test
    void withNoAttemptLimitTheConfiguredTimeoutsStillApply() {
        properties.setConnectTimeout(Duration.ofMillis(300));
        properties.setReadTimeout(Duration.ofMillis(500));
        wire.stubFor(get("/slow").willReturn(aResponse().withFixedDelay(3000).withBody("late")));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/slow"), deadline());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(500)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void aBodyThatStallsAfterTheHeadersIsAlsoBoundedByTheAttemptLimit() {
        wire.stubFor(get("/stall").willReturn(aResponse().withBody("x".repeat(100)).withChunkedDribbleDelay(10, 5000)));
        Deadline limited = Deadline.after(Clock.systemUTC(), Duration.ofSeconds(5), Duration.ofMillis(300));
        long start = System.nanoTime();
        CatalogueHttp.Attempt attempt = new CatalogueHttp(properties).get(url("/stall"), limited);
        assertThat(attempt.result()).isEqualTo("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1200));
    }
}
