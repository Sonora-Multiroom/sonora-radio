package multiroom.radio.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.Fixtures;
import multiroom.radio.support.RecordingMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** The fields the API shows are read tolerantly: a wrong type counts as missing (the catalogue is untrusted). */
class StationParsingTest {

    private WireMockServer wire;
    private HttpRadioBrowserClient client;

    @BeforeEach
    void start() {
        wire = new WireMockServer(options().dynamicPort());
        wire.start();
        RadioProperties properties = new RadioProperties();
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setReadTimeout(Duration.ofMillis(500));
        client = new HttpRadioBrowserClient(new CatalogueHttp(properties),
                new StubServerSource("http://localhost:" + wire.port()), properties, new RecordingMetrics(),
                new ObjectMapper());
    }

    @AfterEach
    void stop() {
        wire.stop();
    }

    private Station lookup(String body) {
        wire.stubFor(get(urlPathEqualTo("/json/stations/byuuid")).willReturn(aResponse().withBody(body)));
        return client.station(Fixtures.STATION, Deadline.after(Clock.systemUTC(), Duration.ofSeconds(5)))
                .value().orElseThrow();
    }

    private static String record(String fields) {
        return "[{\"stationuuid\":\"" + Fixtures.STATION + "\",\"name\":\"N\"" + (fields.isEmpty() ? "" : "," + fields) + "}]";
    }

    @Test
    void theFixtureFieldsAreRead() {
        Station station = lookup(Fixtures.load("station-mp3.json"));
        assertThat(station.favicon()).isEqualTo("https://example.test/icon.png");
        assertThat(station.homepage()).isEqualTo("https://example.test/");
        assertThat(station.countryCode()).isEqualTo("XX");
        assertThat(station.languages()).isEqualTo("english");
        assertThat(station.tags()).isEqualTo("pop,test");
        assertThat(station.bitrate()).isEqualTo(128);
        assertThat(station.lastCheckOk()).isTrue();
        assertThat(station.votes()).isEqualTo(12);
        assertThat(station.clickCount()).isEqualTo(3);
        assertThat(station.hls()).isFalse();
    }

    @Test
    void hlsAndLastCheckOkAcceptZeroOneAndBooleans() {
        assertThat(lookup(record("\"hls\":1,\"lastcheckok\":0"))).matches(s -> s.hls() && !s.lastCheckOk());
        assertThat(lookup(record("\"hls\":true,\"lastcheckok\":false"))).matches(s -> s.hls() && !s.lastCheckOk());
        assertThat(lookup(record("\"hls\":false,\"lastcheckok\":true"))).matches(s -> !s.hls() && s.lastCheckOk());
        assertThat(lookup(record(""))).matches(s -> !s.hls() && !s.lastCheckOk());
    }

    @Test
    void aFieldOfTheWrongTypeCountsAsMissing() {
        Station station = lookup(record("\"favicon\":5,\"homepage\":[],\"countrycode\":{},\"language\":7,"
                + "\"tags\":false,\"bitrate\":\"fast\",\"votes\":\"many\",\"clickcount\":null,\"lastcheckok\":\"yes\""));
        assertThat(station.favicon()).isNull();
        assertThat(station.homepage()).isNull();
        assertThat(station.countryCode()).isNull();
        assertThat(station.languages()).isNull();
        assertThat(station.tags()).isNull();
        assertThat(station.bitrate()).isZero();
        assertThat(station.votes()).isZero();
        assertThat(station.clickCount()).isZero();
        assertThat(station.lastCheckOk()).isFalse();
    }

    @Test
    void missingNumbersAreZeroAndUnknownFieldsAreIgnored() {
        Station station = lookup(record("\"surprise\":{\"a\":1}"));
        assertThat(station.bitrate()).isZero();
        assertThat(station.votes()).isZero();
        assertThat(station.clickCount()).isZero();
    }
}
