package multiroom.radio.rest.query;

import multiroom.radio.catalogue.TagQuery;

/** Validates the parameters of a tag listing, with the same caps and wording as a station search. */
public final class TagQueryParser {

    private TagQueryParser() {
    }

    /**
     * Validates one tag listing.
     *
     * @throws InvalidParameterException naming the first parameter that is wrong
     */
    public static TagQuery parse(String name, Integer limit, Integer offset) {
        String filter = QueryRules.lower(QueryRules.text("name", name, QueryRules.TEXT_LIMIT));
        // The catalogue takes the filter as a path segment, and whether its servers accept an encoded
        // slash there is not documented; refusing can be relaxed later without breaking clients.
        if (filter != null && filter.indexOf('/') >= 0) {
            throw new InvalidParameterException("name", "must not contain /");
        }
        return new TagQuery(filter, QueryRules.limit(limit), QueryRules.offset(offset));
    }
}
