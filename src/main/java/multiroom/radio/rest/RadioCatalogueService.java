package multiroom.radio.rest;

import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.Country;
import multiroom.radio.catalogue.Language;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.Station;
import multiroom.radio.catalogue.StationPage;
import multiroom.radio.catalogue.StationUuid;
import multiroom.radio.catalogue.TagPage;
import multiroom.radio.catalogue.TagQuery;
import multiroom.radio.catalogue.StationQuery;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioStationPage;
import multiroom.radio.rest.dto.RadioTagPage;
import multiroom.radio.rest.query.InvalidParameterException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the REST controllers call: owns the time budget of a request, asks the catalogue through
 * {@link RadioBrowserClient}, cleans the answer with {@link RadioStationMapper} and works out paging.
 * It holds no lock, so a slow catalogue only ever occupies the request thread that asked.
 */
public class RadioCatalogueService {

    private static final Logger LOG = LoggerFactory.getLogger(RadioCatalogueService.class);
    private static final int MAX_OFFSET = 10000;

    private final RadioBrowserClient client;
    private final RadioStationMapper mapper;
    private final RadioProperties properties;
    private final Clock clock;

    /** Creates the service. */
    public RadioCatalogueService(RadioBrowserClient client, RadioStationMapper mapper, RadioProperties properties,
                                 Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** One page of search results. */
    public RadioStationPage search(StationQuery query) {
        long started = System.nanoTime();
        CatalogueAnswer<StationPage> answer = client.search(query, deadline());
        StationPage page = answer.value();
        answered("search", page.stations().size(), answer, started);
        return new RadioStationPage(page.stations().stream().map(mapper::station).toList(),
                nextOffset(query.offset(), query.limit(), page.received()));
    }

    /**
     * One station by its UUID, whether or not the catalogue marks it broken. Served from the
     * station cache when it can be (so it may be up to {@code station-cache-ttl} old); never a play
     * report and never a refresh.
     *
     * @throws InvalidParameterException when the text is not a UUID, before any catalogue call
     * @throws StationNotFoundException  when the catalogue does not know it
     */
    public RadioStation station(String uuidText) {
        UUID uuid = StationUuid.parse(uuidText)
                .orElseThrow(() -> new InvalidParameterException("uuid", "must be a station UUID"));
        long started = System.nanoTime();
        CatalogueAnswer<Optional<Station>> answer = client.station(uuid, deadline());
        answered("station", answer.value().isPresent() ? 1 : 0, answer, started);
        return answer.value().map(mapper::station).orElseThrow(() -> new StationNotFoundException(uuid));
    }

    /** Every country with stations, in the catalogue's order. */
    public List<RadioCountry> countries() {
        long started = System.nanoTime();
        CatalogueAnswer<List<Country>> answer = client.countries(deadline());
        List<RadioCountry> countries = mapper.countries(answer.value());
        answered("countries", countries.size(), answer, started);
        return countries;
    }

    /** Every language with stations, in the catalogue's order. */
    public List<RadioLanguage> languages() {
        long started = System.nanoTime();
        CatalogueAnswer<List<Language>> answer = client.languages(deadline());
        List<RadioLanguage> languages = mapper.languages(answer.value());
        answered("languages", languages.size(), answer, started);
        return languages;
    }

    /** One page of tags. */
    public RadioTagPage tags(TagQuery query) {
        long started = System.nanoTime();
        CatalogueAnswer<TagPage> answer = client.tags(query, deadline());
        TagPage page = answer.value();
        answered("tags", page.tags().size(), answer, started);
        return new RadioTagPage(mapper.tags(page.tags()), nextOffset(query.offset(), query.limit(), page.received()));
    }

    /**
     * The whole request gets {@code request-timeout}; one attempt on one server gets half of it, so a
     * slow first mirror always leaves the second one a turn.
     */
    /**
     * One DEBUG line per answered request: the endpoint, how many records, which server (or "cache")
     * and how long. Never the filter text a client sent or any text the catalogue sent; a station is
     * only ever identified by its UUID elsewhere.
     */
    private static void answered(String endpoint, int records, CatalogueAnswer<?> answer, long startedNanos) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("radio api {} records={} server={} elapsedMs={}", endpoint, records,
                    answer.serverTag().orElse("cache"), (System.nanoTime() - startedNanos) / 1_000_000);
        }
    }

    private Deadline deadline() {
        Duration budget = properties.getRequestTimeout();
        return Deadline.after(clock, budget, budget.dividedBy(2));
    }

    /**
     * The next page starts at {@code offset + limit} when this page was full as the catalogue sent it
     * (counted before any record was dropped) and the catalogue's own paging limit is not passed.
     */
    static Integer nextOffset(int offset, int limit, int received) {
        int next = offset + limit;
        return received == limit && next <= MAX_OFFSET ? next : null;
    }
}
