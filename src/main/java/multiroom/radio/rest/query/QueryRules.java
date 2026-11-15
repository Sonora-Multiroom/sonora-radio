package multiroom.radio.rest.query;

import multiroom.radio.text.Text;

import java.util.Locale;

/** The validation rules the query parsers share, so a rule is written once. */
final class QueryRules {

    static final int TEXT_LIMIT = 100;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    static final int MAX_OFFSET = 10000;

    private QueryRules() {
    }

    /**
     * A trimmed text filter: blank is absent; control characters and {@code %} are refused (the
     * catalogue's own matching treats them in ways this extension does not want to depend on).
     */
    static String text(String parameter, String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxCodePoints) {
            throw new InvalidParameterException(parameter, "must be at most " + maxCodePoints + " characters");
        }
        if (!Text.withoutControl(trimmed).equals(trimmed)) {
            throw new InvalidParameterException(parameter, "must not contain control characters");
        }
        if (trimmed.indexOf('%') >= 0) {
            throw new InvalidParameterException(parameter, "must not contain %");
        }
        return trimmed;
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    static int limit(Integer value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw new InvalidParameterException("limit", "must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    static int offset(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0 || value > MAX_OFFSET) {
            throw new InvalidParameterException("offset", "must be between 0 and " + MAX_OFFSET);
        }
        return value;
    }
}
