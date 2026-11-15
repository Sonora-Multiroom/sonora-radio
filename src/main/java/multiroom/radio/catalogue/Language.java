package multiroom.radio.catalogue;

/**
 * A language as the catalogue lists it (untrusted text).
 *
 * @param name         the name, raw
 * @param code         {@code iso_639}, raw; {@code null} for most entries
 * @param stationCount how many stations it has
 */
public record Language(String name, String code, long stationCount) {
}
