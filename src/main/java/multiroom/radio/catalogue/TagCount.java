package multiroom.radio.catalogue;

/**
 * A tag as the catalogue lists it (untrusted text).
 *
 * @param name         the tag, raw
 * @param stationCount how many stations carry it
 */
public record TagCount(String name, long stationCount) {
}
