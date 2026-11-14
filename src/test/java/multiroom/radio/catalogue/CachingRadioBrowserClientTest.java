package multiroom.radio.catalogue;

import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingRadioBrowserClientTest {

    private static final Deadline DEADLINE = Deadline.after(Clock.systemUTC(), Duration.ofMinutes(5));

    /** Answers from a map; counts calls. */
    private static final class FakeDelegate implements RadioBrowserClient {
        final java.util.Map<UUID, Station> stations = new java.util.HashMap<>();
        final List<String> calls = new ArrayList<>();
        RuntimeException failure;

        @Override
        public CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline) {
            calls.add("station");
            if (failure != null) {
                throw failure;
            }
            return CatalogueAnswer.from(Optional.ofNullable(stations.get(uuid)), "srv");
        }

        @Override
        public CatalogueAnswer<Optional<Station>> refreshStation(UUID uuid, Deadline deadline) {
            calls.add("refresh");
            return CatalogueAnswer.from(Optional.ofNullable(stations.get(uuid)), "fresh");
        }

        @Override
        public CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline) {
            calls.add("play");
            return CatalogueAnswer.from(new PlayReport(true, "http://x.test/"), "srv");
        }
    }

    private FakeDelegate delegate;
    private MutableClock clock;
    private RadioProperties properties;
    private CachingRadioBrowserClient client;
    private final UUID id = UUID.randomUUID();

    private static Station station(UUID uuid, String name) {
        return new Station(uuid, name, "http://s.test/" + name, "MP3", false);
    }

    @BeforeEach
    void setUp() {
        delegate = new FakeDelegate();
        clock = new MutableClock();
        properties = new RadioProperties();
        properties.setStationCacheTtl(Duration.ofMinutes(5));
        properties.setStationCacheSize(2);
        client = new CachingRadioBrowserClient(delegate, properties, clock);
        delegate.stations.put(id, station(id, "one"));
    }

    @Test
    void aSecondLookupWithinTheTtlIsAnsweredFromTheCacheWithNoServerTag() {
        assertThat(client.station(id, DEADLINE).serverTag()).contains("srv");
        CatalogueAnswer<Optional<Station>> second = client.station(id, DEADLINE);
        assertThat(second.value()).isPresent();
        assertThat(second.serverTag()).isEmpty();
        assertThat(delegate.calls).containsExactly("station");
    }

    @Test
    void afterTheTtlTheCatalogueIsAskedAgain() {
        client.station(id, DEADLINE);
        clock.advance(Duration.ofMinutes(6));
        assertThat(client.station(id, DEADLINE).serverTag()).contains("srv");
        assertThat(delegate.calls).containsExactly("station", "station");
    }

    @Test
    void anEmptyAnswerAndAnExceptionAreNeverCached() {
        UUID unknown = UUID.randomUUID();
        client.station(unknown, DEADLINE);
        client.station(unknown, DEADLINE);
        assertThat(delegate.calls).containsExactly("station", "station");

        delegate.failure = new CatalogueUnavailableException("down", List.of());
        assertThatThrownBy(() -> client.station(id, DEADLINE)).isInstanceOf(CatalogueUnavailableException.class);
        delegate.failure = null;
        assertThat(client.station(id, DEADLINE).serverTag()).contains("srv");
    }

    @Test
    void atTheSizeLimitTheOldestEntryIsDropped() {
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        delegate.stations.put(b, station(b, "two"));
        delegate.stations.put(c, station(c, "three"));
        client.station(id, DEADLINE);
        client.station(b, DEADLINE);
        client.station(c, DEADLINE);      // drops id
        delegate.calls.clear();
        client.station(b, DEADLINE);
        client.station(c, DEADLINE);
        assertThat(delegate.calls).isEmpty();
        client.station(id, DEADLINE);
        assertThat(delegate.calls).containsExactly("station");
    }

    @Test
    void refreshAlwaysAsksKeepsTheDelegatesTagAndReplacesTheEntry() {
        client.station(id, DEADLINE);
        delegate.stations.put(id, station(id, "renamed"));
        CatalogueAnswer<Optional<Station>> fresh = client.refreshStation(id, DEADLINE);
        assertThat(fresh.serverTag()).contains("fresh");
        assertThat(client.station(id, DEADLINE).value().orElseThrow().name()).isEqualTo("renamed");
    }

    @Test
    void refreshRemovesTheEntryWhenTheStationIsGone() {
        client.station(id, DEADLINE);
        delegate.stations.remove(id);
        assertThat(client.refreshStation(id, DEADLINE).value()).isEmpty();
        delegate.calls.clear();
        assertThat(client.station(id, DEADLINE).value()).isEmpty();
        assertThat(delegate.calls).containsExactly("station");
    }

    @Test
    void playReportsAreDelegatedEveryTime() {
        client.station(id, DEADLINE);
        client.reportPlay(id, DEADLINE);
        client.reportPlay(id, DEADLINE);
        assertThat(delegate.calls).containsExactly("station", "play", "play");
    }
}
