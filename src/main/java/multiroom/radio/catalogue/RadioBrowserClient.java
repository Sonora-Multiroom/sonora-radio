package multiroom.radio.catalogue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The catalogue calls this extension makes. Implementations throw only
 * {@link CatalogueUnavailableException} and {@link CatalogueProtocolException}.
 */
public interface RadioBrowserClient {

    /** Looks a station up; may be answered from a cache. Never reports a play. */
    CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline);

    /** Looks a station up, always asking the catalogue, never a cache. */
    CatalogueAnswer<Optional<Station>> refreshStation(UUID uuid, Deadline deadline);

    /** Searches stations; always excludes the ones the catalogue marks broken. Never cached. */
    CatalogueAnswer<StationPage> search(StationQuery query, Deadline deadline);

    /** Every country with stations, most stations first. Remembered for hours by the caching decorator. */
    CatalogueAnswer<List<Country>> countries(Deadline deadline);

    /** Every language with stations, most stations first. Remembered for hours by the caching decorator. */
    CatalogueAnswer<List<Language>> languages(Deadline deadline);

    /** One page of tags, most stations first, optionally filtered by name. */
    CatalogueAnswer<TagPage> tags(TagQuery query, Deadline deadline);

    /** Reports a play (the catalogue counts it) and returns the station's current address. */
    CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline);
}
