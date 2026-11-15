package multiroom.radio.catalogue;

/**
 * A country as the catalogue lists it (untrusted text).
 *
 * @param code         {@code iso_3166_1}, raw
 * @param name         the English name, raw, possibly {@code null}
 * @param stationCount how many stations it has
 */
public record Country(String code, String name, long stationCount) {
}
