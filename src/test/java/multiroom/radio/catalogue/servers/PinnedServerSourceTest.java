package multiroom.radio.catalogue.servers;

import multiroom.radio.catalogue.Deadline;
import multiroom.radio.support.RecordingMetrics;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PinnedServerSourceTest {

    private final Deadline deadline = Deadline.after(Clock.systemUTC(), Duration.ofSeconds(1));

    @Test
    void returnsTheConfiguredServersInOrderAndNeverChangesThem() {
        PinnedServerSource source = new PinnedServerSource(
                List.of(URI.create("https://b.example.test"), URI.create("http://a.example.test:8080/")), new RecordingMetrics());
        List<CatalogueServer> first = source.servers(deadline);
        assertThat(first).extracting(CatalogueServer::tag).containsExactly("b.example.test", "a.example.test:8080");

        source.answered(first.get(1));
        source.allFailed();
        assertThat(source.servers(deadline)).isEqualTo(first);
    }

    @Test
    void reportsItselfOnceAsAPinnedDiscovery() {
        RecordingMetrics metrics = new RecordingMetrics();
        PinnedServerSource source = new PinnedServerSource(List.of(URI.create("https://a.example.test")), metrics);
        source.servers(deadline);
        source.servers(deadline);
        assertThat(metrics.discoveries).containsExactly(List.of("pinned", "ok"));
    }

    @Test
    void serverTagOmitsDefaultPorts() {
        assertThat(CatalogueServer.of(URI.create("https://a.example.test:443")).tag()).isEqualTo("a.example.test");
        assertThat(CatalogueServer.of(URI.create("http://a.example.test:80")).tag()).isEqualTo("a.example.test");
        assertThat(CatalogueServer.of(URI.create("https://a.example.test:8443")).tag()).isEqualTo("a.example.test:8443");
    }
}
