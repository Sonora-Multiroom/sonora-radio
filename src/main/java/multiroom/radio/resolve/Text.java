package multiroom.radio.resolve;

/** Bounding helpers for untrusted catalogue text. */
final class Text {

    private Text() {
    }

    /** Removes control characters and line and paragraph separators. */
    static String withoutControl(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && cp != ' ' && cp != ' ')
                .forEach(out::appendCodePoint);
        return out.toString();
    }

    /** Cuts at {@code maxCodePoints}, never inside a surrogate pair. */
    static String truncate(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    /** Safe to put in a message or a log line: no control characters, at most {@code maxCodePoints}. */
    static String bounded(String text, int maxCodePoints) {
        return truncate(withoutControl(text == null ? "" : text), maxCodePoints);
    }
}
