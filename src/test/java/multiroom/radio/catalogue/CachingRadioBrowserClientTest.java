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
        public CatalogueAnswer<StationPage> search(StationQuery query, Deadline deadline) {
            calls.add("search");
            return CatalogueAnswer.from(new StationPage(List.of(), 0), "srv");
        }

        @Override
        public CatalogueAnswer<List<Country>> countries(Deadline deadline) {
            calls.add("countries");
            if (failure != null) {
                throw failure;
            }
            return CatalogueAnswer.from(List.of(new Country("UA", "Ukraine", 1)), "srv");
        }

        @Override
        public CatalogueAnswer<List<Language>> languages(Deadline deadline) {
            calls.add("languages");
            if (failure != null) {
                throw failure;
            }
            return CatalogueAnswer.from(List.of(new Language("english", "en", 1)), "srv");
        }

        @Override
        public CatalogueAnswer<TagPage> tags(TagQuery query, Deadline deadline) {
            calls.add("tags:" + query.name() + ":" + query.limit() + ":" + query.offset());
            if (failure != null) {
                throw failure;
            }
            return CatalogueAnswer.from(new TagPage(List.of(new TagCount("jazz", 1)), 1), "srv");
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
        return multiroom.radio.support.Fixtures.station(uuid, name, "http://s.test/" + name, "MP3", false);
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

    @Test
    void searchIsNeverCached() {
        StationQuery query = new StationQuery(null, null, null, null, null, null, StationOrder.VOTES,
                SortDirection.DESC, 20, 0);
        client.search(query, DEADLINE);
        client.search(query, DEADLINE);
        assertThat(delegate.calls).containsExactly("search", "search");
    }

    // ---- remembered lists (RADIO-002) ----------------------------------------------------------

    @Test
    void aSecondCountriesCallWithinSixHoursMakesNoCatalogueRequestAndAskingAgainAfterThemDoes() {
        CatalogueAnswer<List<Country>> first = client.countries(DEADLINE);
        assertThat(first.serverTag()).contains("srv");
        clock.advance(Duration.ofHours(5).plusMinutes(59));
        CatalogueAnswer<List<Country>> second = client.countries(DEADLINE);
        assertThat(second.serverTag()).isEmpty();
        assertThat(second.value()).isEqualTo(first.value());
        assertThat(delegate.calls).containsExactly("countries");
        clock.advance(Duration.ofMinutes(2));
        client.countries(DEADLINE);
        assertThat(delegate.calls).containsExactly("countries", "countries");
    }

    @Test
    void languagesAreRememberedForSixHoursToo() {
        client.languages(DEADLINE);
        client.languages(DEADLINE);
        assertThat(delegate.calls).containsExactly("languages");
        clock.advance(Duration.ofHours(6).plusSeconds(1));
        client.languages(DEADLINE);
        assertThat(delegate.calls).containsExactly("languages", "languages");
    }

    @Test
    void tagPagesAreKeyedByNameLimitAndOffset() {
        client.tags(new TagQuery("jazz", 20, 0), DEADLINE);
        client.tags(new TagQuery("jazz", 20, 0), DEADLINE);
        assertThat(delegate.calls).containsExactly("tags:jazz:20:0");
        client.tags(new TagQuery("jazz", 50, 0), DEADLINE);
        client.tags(new TagQuery("jazz", 20, 20), DEADLINE);
        client.tags(new TagQuery(null, 20, 0), DEADLINE);
        assertThat(delegate.calls).hasSize(4);
    }

    @Test
    void atMostTwoHundredTagPagesAreKeptOldestDroppedFirst() {
        for (int offset = 0; offset < 201; offset++) {
            client.tags(new TagQuery(null, 1, offset), DEADLINE);
        }
        delegate.calls.clear();
        client.tags(new TagQuery(null, 1, 200), DEADLINE);   // newest: still there
        client.tags(new TagQuery(null, 1, 1), DEADLINE);     // second oldest: still there
        assertThat(delegate.calls).isEmpty();
        client.tags(new TagQuery(null, 1, 0), DEADLINE);     // oldest: dropped
        assertThat(delegate.calls).containsExactly("tags:null:1:0");
    }

    @Test
    void anExceptionStoresNothingAndTheNextCallAsksAgain() {
        delegate.failure = new CatalogueUnavailableException("down", List.of());
        assertThatThrownBy(() -> client.countries(DEADLINE)).isInstanceOf(CatalogueUnavailableException.class);
        assertThatThrownBy(() -> client.languages(DEADLINE)).isInstanceOf(CatalogueUnavailableException.class);
        assertThatThrownBy(() -> client.tags(new TagQuery(null, 1, 0), DEADLINE))
                .isInstanceOf(CatalogueUnavailableException.class);
        delegate.failure = null;
        client.countries(DEADLINE);
        client.languages(DEADLINE);
        client.tags(new TagQuery(null, 1, 0), DEADLINE);
        assertThat(delegate.calls).hasSize(6);
    }
}
