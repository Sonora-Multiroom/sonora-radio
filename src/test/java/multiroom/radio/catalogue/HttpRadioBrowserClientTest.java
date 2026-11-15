package multiroom.radio.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.Fixtures;
import multiroom.radio.support.RecordingMetrics;
import multiroom.radio.support.Wire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRadioBrowserClientTest {

    private static final UUID STATION = Fixtures.STATION;
    private static final String BYUUID = "/json/stations/byuuid";

    private WireMockServer wire;
    private WireMockServer second;
    private RadioProperties properties;
    private RecordingMetrics metrics;

    @org.junit.jupiter.api.BeforeAll
    static void warm() {
        Wire.warmUp();
    }

    @BeforeEach
    void start() {
        wire = new WireMockServer(options().dynamicPort());
        wire.start();
        second = new WireMockServer(options().dynamicPort());
        second.start();
        properties = new RadioProperties();
        properties.setConnectTimeout(Duration.ofMillis(300));
        properties.setReadTimeout(Duration.ofMillis(300));
        metrics = new RecordingMetrics();
    }

    @AfterEach
    void stop() {
        wire.stop();
        second.stop();
    }

    private static String base(WireMockServer server) {
        return "http://localhost:" + server.port();
    }

    private static Deadline deadline() {
        return Deadline.after(Clock.systemUTC(), Duration.ofSeconds(5));
    }

    private HttpRadioBrowserClient client(StubServerSource source) {
        return new HttpRadioBrowserClient(new CatalogueHttp(properties), source, properties, metrics, new ObjectMapper());
    }

    private void stationOk(WireMockServer server) {
        server.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(Fixtures.load("station-mp3.json"))));
    }

    @Test
    void stationRequestsByUuidWithHidebrokenOffAndMapsTheFixture() {
        stationOk(wire);
        StubServerSource source = new StubServerSource(base(wire));
        CatalogueAnswer<java.util.Optional<Station>> answer = client(source).station(STATION, deadline());

        wire.verify(getRequestedFor(urlEqualTo(BYUUID + "?uuids=" + STATION + "&hidebroken=false")));
        Station station = answer.value().orElseThrow();
        assertThat(station.uuid()).isEqualTo(STATION);
        assertThat(station.name()).isEqualTo("Test FM");
        assertThat(station.streamUrl()).isEqualTo("https://stream.example.test/live.mp3");
        assertThat(station.codecLabel()).isEqualTo("MP3");
        assertThat(station.hls()).isFalse();
        assertThat(answer.serverTag()).contains("localhost:" + wire.port());
        assertThat(source.answered).hasSize(1);
        assertThat(metrics.requests).containsExactly(List.of("localhost:" + wire.port(), "station", "ok"));
    }

    @Test
    void urlIsUsedWhenUrlResolvedIsMissingOrBlank() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(
                "[{\"stationuuid\":\"" + STATION + "\",\"url\":\"http://raw.example.test/s\",\"url_resolved\":\"\"}]")));
        Station station = client(new StubServerSource(base(wire))).station(STATION, deadline()).value().orElseThrow();
        assertThat(station.streamUrl()).isEqualTo("http://raw.example.test/s");
        assertThat(station.name()).isNull();
        assertThat(station.codecLabel()).isEmpty();
    }

    @Test
    void refreshStationMakesTheSameRequestAsStation() {
        stationOk(wire);
        client(new StubServerSource(base(wire))).refreshStation(STATION, deadline());
        wire.verify(getRequestedFor(urlEqualTo(BYUUID + "?uuids=" + STATION + "&hidebroken=false")));
    }

    @Test
    void hlsFlagIsReadFromNumbersAndBooleansOnly() {
        String template = "[{\"stationuuid\":\"" + STATION + "\",\"hls\":%s}]";
        for (String[] c : new String[][]{{"1", "true"}, {"true", "true"}, {"0", "false"}, {"\"1\"", "false"}, {"null", "false"}}) {
            wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(template.formatted(c[0]))));
            Station station = client(new StubServerSource(base(wire))).station(STATION, deadline()).value().orElseThrow();
            assertThat(station.hls()).as(c[0]).isEqualTo(Boolean.parseBoolean(c[1]));
        }
    }

    @Test
    void anEmptyArrayIsAnUnknownStation() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(Fixtures.load("station-empty.json"))));
        assertThat(client(new StubServerSource(base(wire))).station(STATION, deadline()).value()).isEmpty();
    }

    @Test
    void anEntryForAnotherUuidIsNotThisStation() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(
                "[{\"stationuuid\":\"00000000-0000-0000-0000-000000000000\",\"name\":\"Other\"}]")));
        assertThat(client(new StubServerSource(base(wire))).station(STATION, deadline()).value()).isEmpty();
    }

    @Test
    void reportPlayMapsTheFixture() {
        wire.stubFor(get("/json/url/" + STATION).willReturn(aResponse().withBody(Fixtures.load("url-ok.json"))));
        CatalogueAnswer<PlayReport> answer = client(new StubServerSource(base(wire))).reportPlay(STATION, deadline());
        assertThat(answer.value()).isEqualTo(new PlayReport(true, "https://stream.example.test/live-current.mp3"));
        assertThat(answer.serverTag()).isPresent();
        assertThat(metrics.requests).containsExactly(List.of("localhost:" + wire.port(), "play", "ok"));
    }

    @Test
    void okIsFalseOnlyForAnExplicitFalse() {
        for (String[] c : new String[][]{{"url-ok.json", "true"}, {"url-ok-missing.json", "true"},
                {"url-ok-string.json", "true"}, {"url-not-ok.json", "false"}}) {
            wire.stubFor(get("/json/url/" + STATION).willReturn(aResponse().withBody(Fixtures.load(c[0]))));
            PlayReport report = client(new StubServerSource(base(wire))).reportPlay(STATION, deadline()).value();
            assertThat(report.ok()).as(c[0]).isEqualTo(Boolean.parseBoolean(c[1]));
        }
        wire.stubFor(get("/json/url/" + STATION).willReturn(aResponse().withBody("{\"ok\":\"FALSE\",\"url\":\"http://a.test/\"}")));
        assertThat(client(new StubServerSource(base(wire))).reportPlay(STATION, deadline()).value().ok()).isFalse();
        wire.stubFor(get("/json/url/" + STATION).willReturn(aResponse().withBody("{\"ok\":5,\"url\":\"http://a.test/\"}")));
        assertThat(client(new StubServerSource(base(wire))).reportPlay(STATION, deadline()).value().ok()).isTrue();
    }

    @Test
    void aNonArrayStationBodyIsAProtocolError() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody(Fixtures.load("station-malformed.json"))));
        assertThatThrownBy(() -> client(new StubServerSource(base(wire))).station(STATION, deadline()))
                .isInstanceOf(CatalogueProtocolException.class);
        assertThat(metrics.requests).containsExactly(List.of("localhost:" + wire.port(), "station", "bad_body"));
    }

    @Test
    void unreadableJsonIsAProtocolError() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withBody("<html>oops")));
        assertThatThrownBy(() -> client(new StubServerSource(base(wire))).station(STATION, deadline()))
                .isInstanceOf(CatalogueProtocolException.class);
    }

    @Test
    void aPlayReportThatIsNotAnObjectIsAProtocolError() {
        wire.stubFor(get("/json/url/" + STATION).willReturn(aResponse().withBody("[1,2]")));
        assertThatThrownBy(() -> client(new StubServerSource(base(wire))).reportPlay(STATION, deadline()))
                .isInstanceOf(CatalogueProtocolException.class);
    }

    @Test
    void a4xxOtherThan429IsAProtocolErrorWithoutTryingAnotherServer() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withStatus(400)));
        stationOk(second);
        assertThatThrownBy(() -> client(new StubServerSource(base(wire), base(second))).station(STATION, deadline()))
                .isInstanceOf(CatalogueProtocolException.class);
        assertThat(second.getAllServeEvents()).isEmpty();
    }

    // ---- failover -------------------------------------------------------------------------------

    @Test
    void movesOnAfterAConnectError() {
        stationOk(second);
        StubServerSource source = new StubServerSource("http://localhost:" + Wire.closedPort(), base(second));
        CatalogueAnswer<java.util.Optional<Station>> answer = client(source).station(STATION, deadline());
        assertThat(answer.value()).isPresent();
        assertThat(answer.serverTag()).contains("localhost:" + second.port());
        assertThat(source.answered).hasSize(1);
        assertThat(metrics.requests).extracting(r -> r.get(2)).containsExactly("connect_error", "ok");
    }

    @Test
    void movesOnAfterATimeout() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withFixedDelay(1000).withBody("[]")));
        stationOk(second);
        assertThat(client(new StubServerSource(base(wire), base(second))).station(STATION, deadline()).value()).isPresent();
        assertThat(metrics.requests).extracting(r -> r.get(2)).containsExactly("timeout", "ok");
    }

    @Test
    void movesOnAfterABodyThatStallsAfterTheHeaders() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(
                aResponse().withBody("[" + " ".repeat(200) + "]").withChunkedDribbleDelay(10, 5000)));
        stationOk(second);
        assertThat(client(new StubServerSource(base(wire), base(second))).station(STATION, deadline()).value()).isPresent();
    }

    @Test
    void movesOnAfter5xxAnd429() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withStatus(503)));
        second.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withStatus(429)));
        WireMockServer third = new WireMockServer(options().dynamicPort());
        third.start();
        try {
            stationOk(third);
            assertThat(client(new StubServerSource(base(wire), base(second), base(third))).station(STATION, deadline())
                    .value()).isPresent();
            assertThat(metrics.requests).extracting(r -> r.get(2)).containsExactly("http_5xx", "http_429", "ok");
        } finally {
            third.stop();
        }
    }

    @Test
    void triesAtMostMaxServersPerRequestAndThenReportsEveryoneFailed() {
        properties.setMaxServersPerRequest(2);
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withStatus(500)));
        second.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withStatus(500)));
        WireMockServer third = new WireMockServer(options().dynamicPort());
        third.start();
        try {
            stationOk(third);
            StubServerSource source = new StubServerSource(base(wire), base(second), base(third));
            assertThatThrownBy(() -> client(source).station(STATION, deadline()))
                    .isInstanceOfSatisfying(CatalogueUnavailableException.class,
                            e -> assertThat(e.serversTried()).hasSize(2));
            assertThat(third.getAllServeEvents()).isEmpty();
            assertThat(source.allFailedCalls).isEqualTo(1);
            assertThat(source.answered).isEmpty();
        } finally {
            third.stop();
        }
    }

    @Test
    void stopsWhenTheDeadlineExpires() {
        wire.stubFor(get(urlPathEqualTo(BYUUID)).willReturn(aResponse().withFixedDelay(2000).withBody("[]")));
        stationOk(second);
        Deadline shortDeadline = Deadline.after(Clock.systemUTC(), Duration.ofMillis(250));
        assertThatThrownBy(() -> client(new StubServerSource(base(wire), base(second))).station(STATION, shortDeadline))
                .isInstanceOfSatisfying(CatalogueUnavailableException.class,
                        e -> assertThat(e.serversTried()).containsExactly("localhost:" + wire.port()));
        assertThat(second.getAllServeEvents()).isEmpty();
    }
}
