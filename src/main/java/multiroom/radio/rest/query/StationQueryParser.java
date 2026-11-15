package multiroom.radio.rest.query;

import multiroom.radio.catalogue.SortDirection;
import multiroom.radio.catalogue.StationOrder;
import multiroom.radio.catalogue.StationQuery;

import java.util.Locale;

/**
 * Turns the parameters of a station search into a {@link StationQuery}, or refuses them before any
 * network call is made. Values are validated, never silently corrected: a {@code limit} of 500 is a
 * 400, not a page of 100.
 */
public final class StationQueryParser {

    private static final int CODEC_LIMIT = 32;
    private static final int MAX_BITRATE = 1_000_000;

    private StationQueryParser() {
    }

    /**
     * Validates one search.
     *
     * @throws InvalidParameterException naming the first parameter that is wrong
     */
    public static StationQuery parse(String name, String tag, String language, String countryCode, String codec,
                                     Integer bitrateMin, String order, String direction, Integer limit,
                                     Integer offset) {
        String nameValue = QueryRules.text("name", name, QueryRules.TEXT_LIMIT);
        String tagValue = QueryRules.lower(QueryRules.text("tag", tag, QueryRules.TEXT_LIMIT));
        String languageValue = QueryRules.lower(QueryRules.text("language", language, QueryRules.TEXT_LIMIT));
        String country = countryCode(countryCode);
        String codecValue = QueryRules.text("codec", codec, CODEC_LIMIT);
        if (bitrateMin != null && (bitrateMin < 0 || bitrateMin > MAX_BITRATE)) {
            throw new InvalidParameterException("bitrateMin", "must be between 0 and " + MAX_BITRATE);
        }
        StationOrder orderValue = order(order);
        SortDirection directionValue = direction(direction, orderValue);
        return new StationQuery(nameValue, tagValue, languageValue, country, codecValue, bitrateMin, orderValue,
                directionValue, QueryRules.limit(limit), QueryRules.offset(offset));
    }

    private static String countryCode(String value) {
        String text = QueryRules.text("countryCode", value, 2);
        if (text == null) {
            return null;
        }
        if (text.length() != 2 || !isAsciiLetter(text.charAt(0)) || !isAsciiLetter(text.charAt(1))) {
            throw new InvalidParameterException("countryCode", "must be two letters (ISO 3166-1 alpha-2)");
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static StationOrder order(String value) {
        if (value == null || value.isEmpty()) {
            return StationOrder.VOTES;
        }
        return switch (value) {
            case "name" -> StationOrder.NAME;
            case "votes" -> StationOrder.VOTES;
            case "clickCount" -> StationOrder.CLICK_COUNT;
            case "bitrate" -> StationOrder.BITRATE;
            default -> throw new InvalidParameterException("order", "must be one of name, votes, clickCount, bitrate");
        };
    }

    private static SortDirection direction(String value, StationOrder order) {
        if (value == null || value.isEmpty()) {
            return order == StationOrder.NAME || order == StationOrder.BITRATE ? SortDirection.ASC : SortDirection.DESC;
        }
        return switch (value) {
            case "asc" -> SortDirection.ASC;
            case "desc" -> SortDirection.DESC;
            default -> throw new InvalidParameterException("direction", "must be asc or desc");
        };
    }
}
