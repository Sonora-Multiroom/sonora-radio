package multiroom.radio.text;

import java.util.Optional;

/**
 * Turns a catalogue station name into the title handed to the hub. The name is untrusted text:
 * control characters and line and paragraph separators are removed, whitespace runs collapse to one
 * space, and the result is cut at 256 code points. Core owns the display length.
 */
public final class StationTitle {

    static final int MAX_CODE_POINTS = 256;

    private StationTitle() {
    }

    /** The cleaned title, or empty when nothing is left. */
    public static Optional<String> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder(name.length());
        boolean pendingSpace = false;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.length() > 0;
            } else if (Character.isISOControl(cp) || cp == ' ' || cp == ' ') {
                // dropped
            } else {
                if (pendingSpace) {
                    out.append(' ');
                    pendingSpace = false;
                }
                out.appendCodePoint(cp);
            }
        }
        String cleaned = Text.truncate(out.toString(), MAX_CODE_POINTS).trim();
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned);
    }
}
