package multiroom.radio.metrics;

import java.time.Duration;

/**
 * What the extension reports. No Micrometer types, so the module loads without Micrometer. No method
 * throws. Tag values are closed sets (see the observability contract), except the server tag.
 */
public interface RadioMetrics {

    String OUTCOME_OK = "ok";
    String OUTCOME_FALLBACK = "fallback";
    String OUTCOME_INVALID_UUID = "invalid_uuid";
    String OUTCOME_UNKNOWN_STATION = "unknown_station";
    String OUTCOME_UNSUPPORTED_CODEC = "unsupported_codec";
    String OUTCOME_HLS_REFUSED = "hls_refused";
    String OUTCOME_UNSUPPORTED_ADDRESS = "unsupported_address";
    String OUTCOME_UPSTREAM_UNAVAILABLE = "upstream_unavailable";
    String OUTCOME_UPSTREAM_PROTOCOL_ERROR = "upstream_protocol_error";

    String SERVER_NONE = "none";

    String ENDPOINT_STATION = "station";
    String ENDPOINT_PLAY = "play";
    String ENDPOINT_SERVERS = "servers";

    String RESULT_OK = "ok";
    String RESULT_TIMEOUT = "timeout";
    String RESULT_CONNECT_ERROR = "connect_error";
    String RESULT_HTTP_429 = "http_429";
    String RESULT_HTTP_4XX = "http_4xx";
    String RESULT_HTTP_5XX = "http_5xx";
    String RESULT_BAD_BODY = "bad_body";

    String SOURCE_DNS = "dns";
    String SOURCE_SERVER_LIST = "server_list";
    String SOURCE_PINNED = "pinned";

    String DISCOVERY_OK = "ok";
    String DISCOVERY_EMPTY = "empty";
    String DISCOVERY_TIMEOUT = "timeout";
    String DISCOVERY_ERROR = "error";

    /** One resolution finished. */
    void resolution(String outcome, String serverTag, Duration elapsed);

    /** One HTTP request to a catalogue server finished. */
    void catalogueRequest(String serverTag, String endpoint, String result);

    /** One server discovery finished. */
    void discovery(String source, String result);
}
