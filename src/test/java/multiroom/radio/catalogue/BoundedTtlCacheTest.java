package multiroom.radio.catalogue;

import multiroom.radio.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedTtlCacheTest {

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
    }

    @Test
    void aValueIsReturnedBeforeItsTimeToLiveEndsAndAbsentAfter() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 10, clock);
        cache.put("a", "one");
        clock.advance(Duration.ofMinutes(4));
        assertThat(cache.get("a")).contains("one");
        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void aMissingKeyIsEmpty() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 10, clock);
        assertThat(cache.get("nothing")).isEmpty();
    }

    @Test
    void atTheSizeBoundTheOldestEntryIsDropped() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 2, clock);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.get("c")).contains("3");
    }

    @Test
    void aReinsertedKeyReplacesTheOldValueAndCountsAsNewest() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 2, clock);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("a", "1b");
        cache.put("c", "3");              // drops b, the oldest now
        assertThat(cache.get("a")).contains("1b");
        assertThat(cache.get("b")).isEmpty();
    }

    @Test
    void removeForgetsAKey() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 2, clock);
        cache.put("a", "1");
        cache.remove("a");
        assertThat(cache.get("a")).isEmpty();
    }

    @Test
    void concurrentPutAndGetLoseNoEntryWithinTheBound() throws Exception {
        BoundedTtlCache<Integer, Integer> cache = new BoundedTtlCache<>(Duration.ofMinutes(5), 1000, clock);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            IntStream.range(0, 8).forEach(t -> pool.execute(() -> {
                for (int i = 0; i < 100; i++) {
                    int key = t * 100 + i;
                    cache.put(key, key);
                    cache.get(key);
                }
            }));
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        for (int key = 0; key < 800; key++) {
            assertThat(cache.get(key)).contains(key);
        }
        assertThat(cache.size()).isEqualTo(800);
    }
}
