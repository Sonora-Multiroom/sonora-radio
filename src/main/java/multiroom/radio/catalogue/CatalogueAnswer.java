package multiroom.radio.catalogue;

import java.util.Optional;

/**
 * A catalogue result together with where it came from.
 *
 * @param value     the result
 * @param serverTag the tag of the server that answered; empty when the value came from the cache
 * @param <T>       the kind of result
 */
public record CatalogueAnswer<T>(T value, Optional<String> serverTag) {

    /** An answer given by a server. */
    public static <T> CatalogueAnswer<T> from(T value, String serverTag) {
        return new CatalogueAnswer<>(value, Optional.of(serverTag));
    }

    /** An answer that came from the cache. */
    public static <T> CatalogueAnswer<T> cached(T value) {
        return new CatalogueAnswer<>(value, Optional.empty());
    }
}
