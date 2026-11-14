package multiroom.radio.catalogue;

import java.util.List;

/** No catalogue server answered within the budget. */
public class CatalogueUnavailableException extends RuntimeException {

    private final transient List<String> serversTried;

    /** Creates the exception with the tags of the servers that were tried. */
    public CatalogueUnavailableException(String message, List<String> serversTried) {
        super(message);
        this.serversTried = List.copyOf(serversTried);
    }

    /** The tags of the servers that were tried, in order. */
    public List<String> serversTried() {
        return serversTried;
    }
}
