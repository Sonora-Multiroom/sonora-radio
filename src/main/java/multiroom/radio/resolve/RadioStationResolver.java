package multiroom.radio.resolve;

import multiroom.api.io.resolve.EndpointRefreshMode;
import multiroom.api.io.resolve.InputEndpointResolver;
import multiroom.api.io.resolve.InputResolutionException;
import multiroom.api.io.resolve.InputResolutionFailure;
import multiroom.api.io.resolve.ResolvedInputEndpoint;
import multiroom.api.model.AudioInputDefinition;
import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.CatalogueProtocolException;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.PlayReport;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.Station;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.RadioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves {@code radiobrowser://<station uuid>} to the station's {@code http(s)} stream address.
 *
 * <p>A resolution looks the station up, applies the codec and HLS policy, reports the play and hands
 * the hub the address together with the station's name as title. A refused station is never reported
 * as played. If the play report fails, the address from the lookup is played instead (looked up
 * afresh when the lookup came from the cache). Only {@link InputResolutionException} leaves
 * {@link #resolve}: the hub removes the ephemeral input it made only for that exception.
 */
public class RadioStationResolver implements InputEndpointResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RadioStationResolver.class);

    static final String SCHEME = "radiobrowser";
    static final String RESOLVER_ID = "radiobrowser";
    private static final int RESOLVER_ORDER = 120;
    private static final int MESSAGE_VALUE_LIMIT = 64;

    private final RadioBrowserClient client;
    private final CodecPolicy codecPolicy;
    private final RadioProperties properties;
    private final RadioMetrics metrics;
    private final Clock clock;

    /** Creates the resolver; nothing is contacted. */
    public RadioStationResolver(RadioBrowserClient client, CodecPolicy codecPolicy, RadioProperties properties,
                                RadioMetrics metrics, Clock clock) {
        this.client = client;
        this.codecPolicy = codecPolicy;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return RESOLVER_ORDER;
    }

    /** Cheap and side-effect free: the hub also asks at start-up, for configured inputs. */
    @Override
    public boolean supports(AudioInputDefinition input) {
        try {
            String uri = input == null ? null : input.getUri();
            if (uri == null || uri.isBlank()) {
                return false;
            }
            String scheme = new URI(uri.trim()).getScheme();
            return SCHEME.equalsIgnoreCase(scheme);
        } catch (URISyntaxException | RuntimeException e) {
            return false;
        }
    }

    /** What one resolution learns on its way: the outcome and which server last answered. */
    private static final class State {
        String outcome = RadioMetrics.OUTCOME_UPSTREAM_PROTOCOL_ERROR;
        String server = RadioMetrics.SERVER_NONE;
        String uuid = "invalid";

        void saw(CatalogueAnswer<?> answer) {
            answer.serverTag().ifPresent(tag -> server = tag);
        }

        InputResolutionException fail(String outcome, InputResolutionFailure failure, String message) {
            this.outcome = outcome;
            return new InputResolutionException(failure, message);
        }
    }

    @Override
    public ResolvedInputEndpoint resolve(AudioInputDefinition input) throws InputResolutionException {
        long started = System.nanoTime();
        State state = new State();
        try {
            ResolvedInputEndpoint endpoint = doResolve(input, state);
            return endpoint;
        } catch (InputResolutionException e) {
            throw e;
        } catch (RuntimeException e) {
            state.outcome = RadioMetrics.OUTCOME_UPSTREAM_PROTOCOL_ERROR;
            throw new InputResolutionException(InputResolutionFailure.UPSTREAM_PROTOCOL_ERROR,
                    "Station " + state.uuid + " could not be resolved: unexpected " + e.getClass().getSimpleName(), e);
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            LOG.info("radio resolution uuid={} outcome={} server={} elapsedMs={}", state.uuid, state.outcome,
                    state.server, elapsed.toMillis());
            metrics.resolution(state.outcome, state.server, elapsed);
        }
    }

    private ResolvedInputEndpoint doResolve(AudioInputDefinition input, State state) {
        String original = input == null ? null : input.getUri();
        StationReference reference;
        try {
            reference = StationReference.parse(original);
        } catch (IllegalArgumentException e) {
            throw state.fail(RadioMetrics.OUTCOME_INVALID_UUID, InputResolutionFailure.INVALID_INPUT,
                    "Not a valid radiobrowser:// station reference '" + Text.bounded(original, MESSAGE_VALUE_LIMIT)
                            + "': " + e.getMessage());
        }
        UUID uuid = reference.uuid();
        state.uuid = uuid.toString();
        Deadline deadline = Deadline.after(clock, properties.getResolveTimeout());

        CatalogueAnswer<Optional<Station>> lookup = lookup(uuid, deadline, state, false);
        Station station = lookup.value().orElseThrow(() -> state.fail(RadioMetrics.OUTCOME_UNKNOWN_STATION,
                InputResolutionFailure.INVALID_INPUT,
                "Station radiobrowser://" + uuid + " not found in radio-browser"));
        checkPolicy(station, state);

        String address;
        String outcome;
        PlayReport report = playReport(uuid, deadline, state);
        if (report != null && report.ok() && report.url() != null && !report.url().isBlank()) {
            address = report.url();
            outcome = RadioMetrics.OUTCOME_OK;
        } else {
            if (lookup.serverTag().isEmpty()) {
                station = lookup(uuid, deadline, state, true).value().orElseThrow(() -> state.fail(
                        RadioMetrics.OUTCOME_UNKNOWN_STATION, InputResolutionFailure.INVALID_INPUT,
                        "Station radiobrowser://" + uuid + " not found in radio-browser"));
                checkPolicy(station, state);
            }
            address = station.streamUrl();
            outcome = RadioMetrics.OUTCOME_FALLBACK;
        }

        URI effective;
        try {
            effective = StreamAddress.parse(address);
        } catch (IllegalArgumentException e) {
            throw state.fail(RadioMetrics.OUTCOME_UNSUPPORTED_ADDRESS, InputResolutionFailure.UNSUPPORTED_RESOURCE,
                    "Station " + uuid + " has a stream address with an unsupported scheme: " + e.getMessage());
        }
        state.outcome = outcome;
        return new ResolvedInputEndpoint(input, effective, Map.of(), null,
                EndpointRefreshMode.RE_RESOLVE_ORIGINAL_INPUT, RESOLVER_ID,
                StationTitle.of(station.name()).orElse(null), null);
    }

    private CatalogueAnswer<Optional<Station>> lookup(UUID uuid, Deadline deadline, State state, boolean refresh) {
        try {
            CatalogueAnswer<Optional<Station>> answer =
                    refresh ? client.refreshStation(uuid, deadline) : client.station(uuid, deadline);
            state.saw(answer);
            return answer;
        } catch (CatalogueUnavailableException e) {
            throw state.fail(RadioMetrics.OUTCOME_UPSTREAM_UNAVAILABLE, InputResolutionFailure.UPSTREAM_UNAVAILABLE,
                    "Station " + uuid + ": radio-browser unavailable");
        } catch (CatalogueProtocolException e) {
            throw state.fail(RadioMetrics.OUTCOME_UPSTREAM_PROTOCOL_ERROR, InputResolutionFailure.UPSTREAM_PROTOCOL_ERROR,
                    "Station " + uuid + ": radio-browser answered with something unreadable");
        }
    }

    private void checkPolicy(Station station, State state) {
        Optional<String> refused = codecPolicy.refusal(station.codecLabel());
        if (refused.isPresent()) {
            throw state.fail(RadioMetrics.OUTCOME_UNSUPPORTED_CODEC, InputResolutionFailure.UNSUPPORTED_RESOURCE,
                    "Station " + station.uuid() + " uses the codec " + refused.get() + ", which this hub cannot decode");
        }
        if (station.hls() && !properties.isAllowHls()) {
            throw state.fail(RadioMetrics.OUTCOME_HLS_REFUSED, InputResolutionFailure.UNSUPPORTED_RESOURCE,
                    "Station " + station.uuid() + " is an HLS stream and HLS is switched off");
        }
    }

    /** The play report, or {@code null} when it could not be had (the caller falls back). */
    private PlayReport playReport(UUID uuid, Deadline deadline, State state) {
        try {
            CatalogueAnswer<PlayReport> answer = client.reportPlay(uuid, deadline);
            state.saw(answer);
            return answer.value();
        } catch (CatalogueUnavailableException | CatalogueProtocolException e) {
            LOG.debug("Play report failed for {}: {}", uuid, e.getClass().getSimpleName());
            return null;
        }
    }
}
