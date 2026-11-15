package multiroom.radio.catalogue;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The one definition of a well-formed station UUID, shared by the play path and the REST layer so that
 * both refuse exactly the same text. Strict on purpose: {@link UUID#fromString} accepts short and
 * sloppy forms that the catalogue would never have issued.
 */
public final class StationUuid {

    private static final Pattern FORM =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private StationUuid() {
    }

    /** The UUID, or empty when the text is not exactly 36 characters of canonical form (any letter case). */
    public static Optional<UUID> parse(String text) {
        if (text == null || !FORM.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(text.toLowerCase(Locale.ROOT)));
    }
}
