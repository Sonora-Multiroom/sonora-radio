package multiroom.radio.catalogue;

import multiroom.radio.config.RadioProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers found stations briefly so that a re-lookup of the same station does not go to the
 * catalogue again. Only found stations are cached, at most {@code station-cache-size} of them for
 * {@code station-cache-ttl}; play reports are never cached, so every play is still counted, and a
 * refresh always asks the catalogue. The country and language lists and up to 200 tag pages are
 * remembered for 6 hours; a failed call stores nothing, and searches are never remembered. No lock is
 * held across a catalogue call, so two identical requests on a cold cache both reach the catalogue.
 */
public class CachingRadioBrowserClient implements RadioBrowserClient {

    /** How long a country, language or tag list is remembered; fixed, the catalogue's lists change slowly. */
    static final Duration LIST_TTL = Duration.ofHours(6);
    static final int MAX_TAG_PAGES = 200;
    private static final String ONLY = "all";

    private final RadioBrowserClient delegate;
    private final BoundedTtlCache<UUID, Station> stations;
    private final BoundedTtlCache<String, List<Country>> countries;
    private final BoundedTtlCache<String, List<Language>> languages;
    private final BoundedTtlCache<TagQuery, TagPage> tagPages;

    /** Decorates a client. */
    public CachingRadioBrowserClient(RadioBrowserClient delegate, RadioProperties properties, Clock clock) {
        this.delegate = delegate;
        this.stations = new BoundedTtlCache<>(properties.getStationCacheTtl(), properties.getStationCacheSize(), clock);
        this.countries = new BoundedTtlCache<>(LIST_TTL, 1, clock);
        this.languages = new BoundedTtlCache<>(LIST_TTL, 1, clock);
        this.tagPages = new BoundedTtlCache<>(LIST_TTL, MAX_TAG_PAGES, clock);
    }

    @Override
    public CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline) {
        Optional<Station> cached = stations.get(uuid);
        if (cached.isPresent()) {
            return CatalogueAnswer.cached(cached);
        }
        CatalogueAnswer<Optional<Station>> answer = delegate.station(uuid, deadline);
        answer.value().ifPresent(station -> remember(uuid, station));
        return answer;
    }

    @Override
    public CatalogueAnswer<Optional<Station>> refreshStation(UUID uuid, Deadline deadline) {
        CatalogueAnswer<Optional<Station>> answer = delegate.refreshStation(uuid, deadline);
        if (answer.value().isPresent()) {
            remember(uuid, answer.value().get());
        } else {
            stations.remove(uuid);
        }
        return answer;
    }

    @Override
    public CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline) {
        return delegate.reportPlay(uuid, deadline);
    }

    @Override
    public CatalogueAnswer<StationPage> search(StationQuery query, Deadline deadline) {
        return delegate.search(query, deadline);
    }

    @Override
    public CatalogueAnswer<List<Country>> countries(Deadline deadline) {
        Optional<List<Country>> cached = countries.get(ONLY);
        if (cached.isPresent()) {
            return CatalogueAnswer.cached(cached.get());
        }
        CatalogueAnswer<List<Country>> answer = delegate.countries(deadline);
        countries.put(ONLY, answer.value());
        return answer;
    }

    @Override
    public CatalogueAnswer<List<Language>> languages(Deadline deadline) {
        Optional<List<Language>> cached = languages.get(ONLY);
        if (cached.isPresent()) {
            return CatalogueAnswer.cached(cached.get());
        }
        CatalogueAnswer<List<Language>> answer = delegate.languages(deadline);
        languages.put(ONLY, answer.value());
        return answer;
    }

    @Override
    public CatalogueAnswer<TagPage> tags(TagQuery query, Deadline deadline) {
        Optional<TagPage> cached = tagPages.get(query);
        if (cached.isPresent()) {
            return CatalogueAnswer.cached(cached.get());
        }
        CatalogueAnswer<TagPage> answer = delegate.tags(query, deadline);
        tagPages.put(query, answer.value());
        return answer;
    }

    private void remember(UUID uuid, Station station) {
        stations.put(uuid, station);
    }
}
