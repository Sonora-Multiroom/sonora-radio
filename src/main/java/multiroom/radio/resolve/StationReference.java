package multiroom.radio.resolve;

import multiroom.radio.catalogue.StationUuid;

import java.util.UUID;

/**
 * The parsed form of a {@code radiobrowser://<uuid>} URI. The label after {@code #} is kept for
 * readability only: it is ignored by equality and never logged.
 *
 * @param uuid  the station UUID, in canonical lower case
 * @param label whatever followed {@code #}, or {@code null}
 */
public record StationReference(UUID uuid, String label) {

    private static final String PREFIX = "radiobrowser://";

    @Override
    public boolean equals(Object other) {
        return other instanceof StationReference that && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    /**
     * Parses a station URI.
     *
     * @throws IllegalArgumentException with the reason, when the text is outside the grammar
     */
    public static StationReference parse(String uri) {
        if (uri == null || !uri.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new IllegalArgumentException("not a radiobrowser:// URI");
        }
        String rest = uri.substring(PREFIX.length());
        String label = null;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            label = rest.substring(hash + 1);
            rest = rest.substring(0, hash);
        }
        if (rest.indexOf('?') >= 0) {
            throw new IllegalArgumentException("a query is not allowed");
        }
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "" : rest.substring(slash);
        if (!path.isEmpty() && !path.equals("/")) {
            throw new IllegalArgumentException("a path is not allowed");
        }
        if (authority.isEmpty()) {
            throw new IllegalArgumentException("the station UUID is missing");
        }
        UUID uuid = StationUuid.parse(authority)
                .orElseThrow(() -> new IllegalArgumentException("not a station UUID"));
        return new StationReference(uuid, label);
    }
}
