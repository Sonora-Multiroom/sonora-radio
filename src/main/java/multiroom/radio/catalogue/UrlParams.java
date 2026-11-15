package multiroom.radio.catalogue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds a query string in which every value is percent-encoded as UTF-8. The one place client text
 * becomes part of a URL, so nothing is ever concatenated into one unencoded.
 */
final class UrlParams {

    private final StringBuilder out = new StringBuilder();

    /** Adds {@code name=value}, encoding the value. */
    UrlParams add(String name, String value) {
        if (out.length() > 0) {
            out.append('&');
        }
        out.append(name).append('=').append(encode(value));
        return this;
    }

    /** Adds the parameter only when the value is not {@code null}. */
    UrlParams addIfPresent(String name, String value) {
        return value == null ? this : add(name, value);
    }

    /** Adds the parameter only when {@code condition} holds. */
    UrlParams addIf(String name, String value, boolean condition) {
        return condition ? add(name, value) : this;
    }

    /** Percent-encodes as UTF-8, with {@code %20} for a space (never {@code +}). */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
