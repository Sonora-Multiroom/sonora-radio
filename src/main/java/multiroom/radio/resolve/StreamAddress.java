package multiroom.radio.resolve;

import multiroom.radio.text.Text;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates the address handed to the hub: absolute, {@code http} or {@code https}, with a host. */
public final class StreamAddress {

    private StreamAddress() {
    }

    /**
     * Parses a stream address.
     *
     * @throws IllegalArgumentException naming the scheme only, never the full address
     */
    public static URI parse(String address) {
        URI uri;
        try {
            uri = new URI(address == null ? "" : address.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("unparseable");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("none");
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!lower.equals("http") && !lower.equals("https")) {
            throw new IllegalArgumentException(Text.bounded(lower, 16));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(lower + " without a host");
        }
        return uri;
    }
}
