package multiroom.radio.catalogue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import multiroom.radio.catalogue.servers.CatalogueServer;
import multiroom.radio.catalogue.servers.ServerSource;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.RadioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Talks to radio-browser.info over HTTP: tries the servers of a {@link ServerSource} in order, moves
 * on after a connect error, a timeout, a 5xx or a 429, and maps the JSON field by field, tolerant of
 * unknown, missing and wrongly typed fields (the catalogue is community-edited and untrusted).
 */
public class HttpRadioBrowserClient implements RadioBrowserClient {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRadioBrowserClient.class);

    /** The most the catalogue is asked for in one list: countries and languages are a few hundred entries. */
    private static final int LIST_LIMIT = 2000;

    private final CatalogueHttp http;
    private final ServerSource servers;
    private final RadioProperties properties;
    private final RadioMetrics metrics;
    private final ObjectMapper mapper;

    /** Creates the client; nothing is contacted. */
    public HttpRadioBrowserClient(CatalogueHttp http, ServerSource servers, RadioProperties properties,
                                  RadioMetrics metrics, ObjectMapper mapper) {
        this.http = http;
        this.servers = servers;
        this.properties = properties;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    @Override
    public CatalogueAnswer<Optional<Station>> station(UUID uuid, Deadline deadline) {
        return call(RadioMetrics.ENDPOINT_STATION, "/json/stations/byuuid?uuids=" + uuid + "&hidebroken=false",
                deadline, body -> parseStation(body, uuid));
    }

    @Override
    public CatalogueAnswer<Optional<Station>> refreshStation(UUID uuid, Deadline deadline) {
        return station(uuid, deadline);
    }

    @Override
    public CatalogueAnswer<PlayReport> reportPlay(UUID uuid, Deadline deadline) {
        return call(RadioMetrics.ENDPOINT_PLAY, "/json/url/" + uuid, deadline, this::parsePlayReport);
    }

    @Override
    public CatalogueAnswer<StationPage> search(StationQuery query, Deadline deadline) {
        UrlParams params = new UrlParams()
                .add("hidebroken", "true")
                .add("limit", Integer.toString(query.limit()))
                .add("offset", Integer.toString(query.offset()))
                .add("order", query.order().catalogueWord())
                .add("reverse", Boolean.toString(query.direction() == SortDirection.DESC))
                .addIfPresent("name", query.name())
                .addIfPresent("tag", query.tag())
                .addIf("tagExact", "true", query.tag() != null)
                .addIfPresent("language", query.language())
                .addIf("languageExact", "true", query.language() != null)
                .addIfPresent("countrycode", query.countryCode())
                .addIfPresent("codec", query.codec())
                .addIfPresent("bitrateMin", query.bitrateMin() == null ? null : query.bitrateMin().toString());
        return call(RadioMetrics.ENDPOINT_SEARCH, "/json/stations/search?" + params, deadline, this::parseStationPage);
    }

    @Override
    public CatalogueAnswer<List<Country>> countries(Deadline deadline) {
        return call(RadioMetrics.ENDPOINT_COUNTRIES, "/json/countries?" + listParams(LIST_LIMIT), deadline,
                body -> parseList(body, "country", entry -> text(entry, "iso_3166_1") == null ? null
                        : new Country(text(entry, "iso_3166_1"), text(entry, "name"), number(entry, "stationcount"))).kept());
    }

    @Override
    public CatalogueAnswer<List<Language>> languages(Deadline deadline) {
        return call(RadioMetrics.ENDPOINT_LANGUAGES, "/json/languages?" + listParams(LIST_LIMIT), deadline,
                body -> parseList(body, "language", entry -> text(entry, "name") == null ? null
                        : new Language(text(entry, "name"), text(entry, "iso_639"), number(entry, "stationcount"))).kept());
    }

    @Override
    public CatalogueAnswer<TagPage> tags(TagQuery query, Deadline deadline) {
        String filter = query.name() == null ? "" : "/" + UrlParams.encode(query.name());
        String path = "/json/tags" + filter + "?" + listParams(query.limit()) + "&offset=" + query.offset();
        return call(RadioMetrics.ENDPOINT_TAGS, path, deadline, body -> {
            Parsed<TagCount> parsed = parseList(body, "tag", entry -> text(entry, "name") == null ? null
                    : new TagCount(text(entry, "name"), number(entry, "stationcount")));
            return new TagPage(parsed.kept(), parsed.received());
        });
    }

    private static String listParams(int limit) {
        return new UrlParams().add("hidebroken", "true").add("order", "stationcount").add("reverse", "true")
                .add("limit", Integer.toString(limit)).toString();
    }

    /** What survived of a list, and how many entries the answer held. */
    private record Parsed<T>(List<T> kept, int received) {
    }

    /** Keeps the entries {@code mapper} accepts (it returns {@code null} for one with no identity). */
    private <T> Parsed<T> parseList(String body, String what, Function<JsonNode, T> mapper) {
        JsonNode root = read(body);
        if (root == null || !root.isArray()) {
            throw new CatalogueProtocolException("The " + what + " list did not answer with a JSON array");
        }
        List<T> kept = new ArrayList<>();
        for (JsonNode entry : root) {
            if (entry.isObject()) {
                T value = mapper.apply(entry);
                if (value != null) {
                    kept.add(value);
                }
            }
        }
        return new Parsed<>(List.copyOf(kept), root.size());
    }

    private <T> CatalogueAnswer<T> call(String endpoint, String path, Deadline deadline, Function<String, T> parser) {
        List<CatalogueServer> candidates = servers.servers(deadline);
        List<String> tried = new ArrayList<>();
        int limit = Math.min(properties.getMaxServersPerRequest(), candidates.size());
        for (int i = 0; i < limit && !deadline.expired(); i++) {
            CatalogueServer server = candidates.get(i);
            tried.add(server.tag());
            URI uri = URI.create(trimTrailingSlash(server.baseUri().toString()) + path);
            CatalogueHttp.Attempt attempt = http.get(uri, deadline);
            if (attempt.ok()) {
                T value;
                try {
                    value = parser.apply(attempt.body());
                } catch (CatalogueProtocolException e) {
                    metrics.catalogueRequest(server.tag(), endpoint, RadioMetrics.RESULT_BAD_BODY);
                    servers.answered(server);
                    throw e;
                }
                metrics.catalogueRequest(server.tag(), endpoint, RadioMetrics.RESULT_OK);
                servers.answered(server);
                return CatalogueAnswer.from(value, server.tag());
            }
            metrics.catalogueRequest(server.tag(), endpoint, attempt.result());
            if (RadioMetrics.RESULT_HTTP_4XX.equals(attempt.result())) {
                servers.answered(server);
                throw new CatalogueProtocolException("The catalogue refused the request (" + endpoint + ")");
            }
            LOG.warn("Catalogue server {} failed for {}: {}", server.tag(), endpoint, attempt.result());
        }
        servers.allFailed();
        LOG.warn("No catalogue server answered for {} (tried {})", endpoint, tried);
        throw new CatalogueUnavailableException("No catalogue server answered (" + endpoint + ")", tried);
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private JsonNode read(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new CatalogueProtocolException("The catalogue answered with unreadable JSON", e);
        }
    }

    private StationPage parseStationPage(String body) {
        JsonNode root = read(body);
        if (root == null || !root.isArray()) {
            throw new CatalogueProtocolException("The station search did not answer with a JSON array");
        }
        List<Station> kept = new ArrayList<>();
        for (JsonNode entry : root) {
            if (!entry.isObject()) {
                continue;
            }
            StationUuid.parse(text(entry, "stationuuid"))
                    .ifPresent(uuid -> kept.add(station(entry, uuid, streamUrl(entry))));
        }
        return new StationPage(List.copyOf(kept), root.size());
    }

    private static String streamUrl(JsonNode entry) {
        String url = text(entry, "url_resolved");
        return url == null || url.isBlank() ? text(entry, "url") : url;
    }

    private Optional<Station> parseStation(String body, UUID requested) {
        JsonNode root = read(body);
        if (root == null || !root.isArray()) {
            throw new CatalogueProtocolException("The station lookup did not answer with a JSON array");
        }
        for (JsonNode entry : root) {
            String text = text(entry, "stationuuid");
            if (text != null && text.trim().toLowerCase(Locale.ROOT).equals(requested.toString())) {
                return Optional.of(station(entry, requested, streamUrl(entry)));
            }
        }
        return Optional.empty();
    }

    private static Station station(JsonNode entry, UUID uuid, String streamUrl) {
        String codec = text(entry, "codec");
        return new Station(uuid, text(entry, "name"), streamUrl, codec == null ? "" : codec,
                flag(entry.get("hls")), text(entry, "favicon"), text(entry, "homepage"),
                text(entry, "countrycode"), text(entry, "language"), text(entry, "tags"),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0, number(entry, "bitrate"))),
                flag(entry.get("lastcheckok")), number(entry, "votes"), number(entry, "clickcount"));
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? Math.max(0, value.longValue()) : 0;
    }

    private PlayReport parsePlayReport(String body) {
        JsonNode root = read(body);
        if (root == null || !root.isObject()) {
            throw new CatalogueProtocolException("The play report was not a JSON object");
        }
        JsonNode ok = root.get("ok");
        boolean explicitFalse = ok != null
                && ((ok.isBoolean() && !ok.booleanValue()) || (ok.isTextual() && "false".equalsIgnoreCase(ok.textValue().trim())));
        return new PlayReport(!explicitFalse, text(root, "url"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static boolean flag(JsonNode value) {
        if (value == null) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.isNumber() && value.intValue() == 1;
    }
}
