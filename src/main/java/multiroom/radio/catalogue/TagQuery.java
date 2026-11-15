package multiroom.radio.catalogue;

/**
 * One tag listing. Also the key of a remembered tag page, hence the value-based {@code equals}.
 *
 * @param name   part of the tag name, lower case, or {@code null}
 * @param limit  page size
 * @param offset where the page starts
 */
public record TagQuery(String name, int limit, int offset) {
}
