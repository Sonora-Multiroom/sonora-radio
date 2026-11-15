package multiroom.radio.catalogue;

/**
 * One search, already validated by the REST layer: text filters are trimmed and in the case the
 * catalogue stores them, an absent filter is {@code null}.
 *
 * @param name        part of the station name, or {@code null}
 * @param tag         one whole tag, lower case, or {@code null}
 * @param language    one whole language name, lower case, or {@code null}
 * @param countryCode two upper-case ASCII letters, or {@code null}
 * @param codec       the catalogue's codec label, or {@code null}
 * @param bitrateMin  the lowest bitrate in kbit/s, or {@code null}
 * @param order       what to sort by
 * @param direction   which way
 * @param limit       page size
 * @param offset      where the page starts
 */
public record StationQuery(String name, String tag, String language, String countryCode, String codec,
                           Integer bitrateMin, StationOrder order, SortDirection direction, int limit, int offset) {
}
