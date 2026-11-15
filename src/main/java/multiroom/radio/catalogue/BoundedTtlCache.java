package multiroom.radio.catalogue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A small in-memory map whose entries expire after a fixed time and which never holds more than a
 * fixed number of them, dropping the oldest first. Backed by a concurrent map and holding no lock
 * while a caller does anything slow, so a request thread is never blocked behind a catalogue call.
 *
 * @param <K> the key
 * @param <V> the value
 */
public final class BoundedTtlCache<K, V> {

    private record Entry<V>(V value, Instant storedAt, long order) {
    }

    private final Duration ttl;
    private final int maxSize;
    private final Clock clock;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    /** Creates an empty cache. */
    public BoundedTtlCache(Duration ttl, int maxSize, Clock clock) {
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.clock = clock;
    }

    /** The value stored under {@code key} if it has not expired; an expired entry is forgotten. */
    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.instant().isBefore(entry.storedAt().plus(ttl))) {
            return Optional.of(entry.value());
        }
        entries.remove(key, entry);
        return Optional.empty();
    }

    /** Stores a value, replacing any earlier one, then drops the oldest entries above the bound. */
    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.instant(), counter.incrementAndGet()));
        while (entries.size() > maxSize) {
            entries.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().order(), b.getValue().order()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    /** Forgets a key. */
    public void remove(K key) {
        entries.remove(key);
    }

    /** How many entries are held, expired ones included until they are next asked for. */
    public int size() {
        return entries.size();
    }
}
