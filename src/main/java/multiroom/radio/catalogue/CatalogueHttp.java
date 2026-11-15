package multiroom.radio.catalogue;

import multiroom.radio.RadioVersion;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.RadioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The transport shared by the catalogue client and the server discovery fallback. One JDK
 * {@link HttpClient}, built on first use (building it opens no connection). Each attempt is sent
 * asynchronously and awaited for at most the smaller of {@code connect-timeout + read-timeout} and
 * what is left of the deadline: the JDK client's own timeouts do not cover a body that stalls after
 * the headers, so the wait is what bounds the call.
 */
public class CatalogueHttp {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogueHttp.class);

    /**
     * The outcome of one attempt.
     *
     * @param result one of the {@code RadioMetrics.RESULT_*} values except {@code bad_body}
     * @param body   the response body, only when {@code result} is {@code ok}
     */
    public record Attempt(String result, String body) {

        /** Whether the attempt produced a body. */
        public boolean ok() {
            return RadioMetrics.RESULT_OK.equals(result);
        }
    }

    private final RadioProperties properties;
    private final String userAgent;
    private volatile HttpClient client;

    /** Creates the transport; nothing is opened. */
    public CatalogueHttp(RadioProperties properties) {
        this.properties = properties;
        this.userAgent = properties.getUserAgent() != null ? properties.getUserAgent() : RadioVersion.defaultUserAgent();
    }

    private HttpClient client() {
        HttpClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(properties.getConnectTimeout())
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    /** Sends a GET and classifies what happened. Never throws. */
    public Attempt get(URI uri, Deadline deadline) {
        // Built (once) before the wait is worked out, so that a cold start's cost is charged to the deadline.
        HttpClient httpClient = client();
        Duration wait = deadline.cap(properties.getConnectTimeout().plus(properties.getReadTimeout()));
        if (wait.isZero()) {
            return new Attempt(RadioMetrics.RESULT_TIMEOUT, null);
        }
        LOG.debug("GET {} User-Agent: {}", uri, userAgent);
        Duration requestTimeout = deadline.cap(properties.getReadTimeout());
        if (requestTimeout.isZero()) {
            requestTimeout = Duration.ofMillis(1);
        }
        CompletableFuture<HttpResponse<String>> future;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(requestTimeout)
                    .build();
            future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (RuntimeException e) {
            return new Attempt(RadioMetrics.RESULT_CONNECT_ERROR, null);
        }
        try {
            HttpResponse<String> response = future.get(wait.toMillis(), TimeUnit.MILLISECONDS);
            return classify(response);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new Attempt(RadioMetrics.RESULT_TIMEOUT, null);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new Attempt(RadioMetrics.RESULT_TIMEOUT, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException) {
                return new Attempt(RadioMetrics.RESULT_TIMEOUT, null);
            }
            if (cause instanceof ConnectException || cause instanceof IOException) {
                return new Attempt(RadioMetrics.RESULT_CONNECT_ERROR, null);
            }
            return new Attempt(RadioMetrics.RESULT_CONNECT_ERROR, null);
        }
    }

    private static Attempt classify(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return new Attempt(RadioMetrics.RESULT_OK, response.body());
        }
        if (status == 429) {
            return new Attempt(RadioMetrics.RESULT_HTTP_429, null);
        }
        if (status >= 400 && status < 500) {
            return new Attempt(RadioMetrics.RESULT_HTTP_4XX, null);
        }
        if (status >= 500) {
            return new Attempt(RadioMetrics.RESULT_HTTP_5XX, null);
        }
        return new Attempt(RadioMetrics.RESULT_HTTP_4XX, null);
    }
}
