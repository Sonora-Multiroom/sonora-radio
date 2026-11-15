package multiroom.radio.catalogue;

/**
 * The answer to reporting a play.
 *
 * @param ok  {@code false} only for an explicit {@code false} in the answer
 * @param url the address to play, possibly {@code null}
 */
public record PlayReport(boolean ok, String url) {
}
