package multiroom.radio.rest;

import java.util.UUID;

/** A well-formed station UUID that the catalogue does not know. */
public class StationNotFoundException extends RuntimeException {

    private final UUID uuid;

    /** Creates the exception for the unknown station. */
    public StationNotFoundException(UUID uuid) {
        super("No station with UUID " + uuid);
        this.uuid = uuid;
    }

    /** The UUID that was asked for, lower case. */
    public UUID uuid() {
        return uuid;
    }
}
