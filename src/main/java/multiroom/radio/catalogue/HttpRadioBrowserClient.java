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

    private Optional<Station> parseStation(String body, UUID requested) {
        JsonNode root = read(body);
        if (root == null || !root.isArray()) {
            throw new CatalogueProtocolException("The station lookup did not answer with a JSON array");
        }
        for (JsonNode entry : root) {
            String text = text(entry, "stationuuid");
            if (text != null && text.trim().toLowerCase(Locale.ROOT).equals(requested.toString())) {
                String url = text(entry, "url_resolved");
                if (url == null || url.isBlank()) {
                    url = text(entry, "url");
                }
                String codec = text(entry, "codec");
                return Optional.of(new Station(requested, text(entry, "name"), url,
                        codec == null ? "" : codec, isHls(entry.get("hls"))));
            }
        }
        return Optional.empty();
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

    private static boolean isHls(JsonNode value) {
        if (value == null) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.isNumber() && value.intValue() == 1;
    }
}
