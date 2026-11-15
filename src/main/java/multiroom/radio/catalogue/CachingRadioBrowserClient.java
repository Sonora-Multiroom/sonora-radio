package multiroom.radio.catalogue;

import multiroom.radio.config.RadioProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers found stations briefly so that a re-lookup of the same station does not go to the
 * catalogue again. Only found stations are cached, at most {@code station-cache-size} of them for
 * {@code station-cache-ttl}; play reports are never cached, so every play is still counted, and a
 * refresh always asks the catalogue.
 */
public class CachingRadioBrowserClient implements RadioBrowserClient {

    private record Entry(Station station, Instant storedAt, long order) {
    }

    private final RadioBrowserClient delegate;
    private final RadioProperties properties;
    private final Clock clock;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong counter = new java.util.concurrent.atomic.AtomicLong();

    /** Decorates a client. */
    public CachingRadioBrowserClient(RadioBrowserClient delegate, RadioProperties properties, Clock clock) {
        this.delegate = delegate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline) {
        Entry entry = entries.get(uuid);
        if (entry != null) {
            if (clock.instant().isBefore(entry.storedAt().plus(properties.getStationCacheTtl()))) {
                return CatalogueAnswer.cached(Optional.of(entry.station()));
            }
            entries.remove(uuid, entry);
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
            entries.remove(uuid);
        }
        return answer;
    }

    @Override
    public CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline) {
        return delegate.reportPlay(uuid, deadline);
    }

    private void remember(UUID uuid, Station station) {
        entries.put(uuid, new Entry(station, clock.instant(), counter.incrementAndGet()));
        while (entries.size() > properties.getStationCacheSize()) {
            entries.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().order(), b.getValue().order()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
    }
}
