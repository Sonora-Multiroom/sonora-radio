package multiroom.radio.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.Set;

/**
 * Records into the host's {@link MeterRegistry}. The only class importing Micrometer. Values outside a
 * tag's closed set are recorded as {@code unknown}; the server tag is bounded by the mirror list.
 * Nothing here throws.
 */
public class MicrometerRadioMetrics implements RadioMetrics {

    private static final String UNKNOWN = "unknown";

    private static final Set<String> OUTCOMES = Set.of(OUTCOME_OK, OUTCOME_FALLBACK, OUTCOME_INVALID_UUID,
            OUTCOME_UNKNOWN_STATION, OUTCOME_UNSUPPORTED_CODEC, OUTCOME_HLS_REFUSED, OUTCOME_UNSUPPORTED_ADDRESS,
            OUTCOME_UPSTREAM_UNAVAILABLE, OUTCOME_UPSTREAM_PROTOCOL_ERROR);
    private static final Set<String> ENDPOINTS = Set.of(ENDPOINT_STATION, ENDPOINT_PLAY, ENDPOINT_SERVERS);
    private static final Set<String> RESULTS = Set.of(RESULT_OK, RESULT_TIMEOUT, RESULT_CONNECT_ERROR,
            RESULT_HTTP_429, RESULT_HTTP_4XX, RESULT_HTTP_5XX, RESULT_BAD_BODY);
    private static final Set<String> SOURCES = Set.of(SOURCE_DNS, SOURCE_SERVER_LIST, SOURCE_PINNED);
    private static final Set<String> DISCOVERY_RESULTS =
            Set.of(DISCOVERY_OK, DISCOVERY_EMPTY, DISCOVERY_TIMEOUT, DISCOVERY_ERROR);

    private final MeterRegistry registry;

    /** Creates the recorder. */
    public MicrometerRadioMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void resolution(String outcome, String serverTag, Duration elapsed) {
        try {
            String safeOutcome = closed(OUTCOMES, outcome);
            registry.counter("radio.resolutions", "outcome", safeOutcome, "server", server(serverTag)).increment();
            registry.timer("radio.resolution.duration", "outcome", safeOutcome).record(elapsed);
        } catch (RuntimeException e) {
            // metrics must never break playback
        }
    }

    @Override
    public void catalogueRequest(String serverTag, String endpoint, String result) {
        try {
            registry.counter("radio.catalogue.requests", "server", server(serverTag),
                    "endpoint", closed(ENDPOINTS, endpoint), "result", closed(RESULTS, result)).increment();
        } catch (RuntimeException e) {
            // metrics must never break playback
        }
    }

    @Override
    public void discovery(String source, String result) {
        try {
            registry.counter("radio.discoveries", "source", closed(SOURCES, source),
                    "result", closed(DISCOVERY_RESULTS, result)).increment();
        } catch (RuntimeException e) {
            // metrics must never break playback
        }
    }

    private static String closed(Set<String> allowed, String value) {
        return value != null && allowed.contains(value) ? value : UNKNOWN;
    }

    private static String server(String tag) {
        return tag == null || tag.isBlank() ? SERVER_NONE : tag;
    }
}
