package multiroom.radio.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MicrometerRadioMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerRadioMetrics metrics = new MicrometerRadioMetrics(registry);

    @Test
    void resolutionsAreCountedAndTimedByOutcome() {
        metrics.resolution("ok", "de1.api.radio-browser.info", Duration.ofMillis(120));
        assertThat(registry.get("radio.resolutions").tag("outcome", "ok").tag("server", "de1.api.radio-browser.info")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("radio.resolution.duration").tag("outcome", "ok").timer().count()).isEqualTo(1);
    }

    @Test
    void catalogueRequestsCarryServerEndpointAndResult() {
        metrics.catalogueRequest("de1.api.radio-browser.info", "station", "http_429");
        metrics.catalogueRequest("de1.api.radio-browser.info", "station", "http_4xx");
        assertThat(registry.get("radio.catalogue.requests").tag("result", "http_429").tag("endpoint", "station")
                .tag("server", "de1.api.radio-browser.info").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("radio.catalogue.requests").tag("result", "http_4xx").counter().count()).isEqualTo(1.0);
    }

    @Test
    void discoveriesCarrySourceAndResult() {
        metrics.discovery("dns", "ok");
        assertThat(registry.get("radio.discoveries").tag("source", "dns").tag("result", "ok").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void valuesOutsideTheClosedSetsAreRecordedAsUnknown() {
        metrics.resolution("made up", "srv", Duration.ZERO);
        metrics.catalogueRequest("srv", "search", "weird");
        metrics.discovery("carrier pigeon", "lost");
        assertThat(registry.get("radio.resolutions").tag("outcome", "unknown").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("radio.catalogue.requests").tag("endpoint", "unknown").tag("result", "unknown")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("radio.discoveries").tag("source", "unknown").tag("result", "unknown")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void aMissingServerTagIsNone() {
        metrics.resolution("ok", null, Duration.ZERO);
        metrics.catalogueRequest(" ", "play", "ok");
        assertThat(registry.get("radio.resolutions").tag("server", "none").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("radio.catalogue.requests").tag("server", "none").counter().count()).isEqualTo(1.0);
    }

    @Test
    void prometheusNamesAreTheDocumentedOnes() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerRadioMetrics recorder = new MicrometerRadioMetrics(prometheus);
        recorder.resolution("ok", "srv", Duration.ofMillis(5));
        recorder.catalogueRequest("srv", "station", "ok");
        recorder.discovery("dns", "ok");
        String scrape = prometheus.scrape();
        assertThat(scrape).contains("radio_resolutions_total", "radio_resolution_duration_seconds",
                "radio_catalogue_requests_total", "radio_discoveries_total");
    }

    @Test
    void aFailingRegistryNeverThrows() {
        MeterRegistry broken = new SimpleMeterRegistry() {
            @Override
            public io.micrometer.core.instrument.Counter counter(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
                throw new IllegalStateException("registry down");
            }

            @Override
            public io.micrometer.core.instrument.Timer timer(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
                throw new IllegalStateException("registry down");
            }
        };
        MicrometerRadioMetrics recorder = new MicrometerRadioMetrics(broken);
        assertThatCode(() -> {
            recorder.resolution("ok", "srv", Duration.ZERO);
            recorder.catalogueRequest("srv", "play", "ok");
            recorder.discovery("dns", "ok");
        }).doesNotThrowAnyException();
    }
}
