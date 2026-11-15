package multiroom.radio.catalogue.servers;

import multiroom.radio.catalogue.Deadline;

import java.util.List;

/** Where the catalogue's servers come from: a pinned list or discovery. */
public interface ServerSource {

    /** The servers to try, in order. Discovery happens here, lazily, bounded by the deadline. */
    List<CatalogueServer> servers(Deadline deadline);

    /** Tells the source which server answered. */
    void answered(CatalogueServer server);

    /** Tells the source that every server failed. */
    void allFailed();
}
