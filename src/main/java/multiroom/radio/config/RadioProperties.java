package multiroom.radio.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code multiroom.radio.*} configuration. Every property has a working default held in its
 * field initialiser (the extension ships no {@code application.yml}); a value that breaks a rule
 * fails start-up with a message naming the property.
 */
@ConfigurationProperties("multiroom.radio")
@Validated
public class RadioProperties {

    private static final Pattern PLAIN_HOST =
            Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*");

    /** {@code false} contributes no beans at all. */
    private boolean enabled = true;

    /** The {@code User-Agent} sent to the catalogue; {@code null} means {@code sonora-radio/<version>}. */
    private String userAgent;

    /** How long connecting to one catalogue server may take. */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** How long one catalogue server may take to answer once connected. */
    private Duration readTimeout = Duration.ofSeconds(4);

    /** The budget of one whole resolution, discovery included. */
    private Duration resolveTimeout = Duration.ofSeconds(10);

    /** How many servers one request may try. */
    private int maxServersPerRequest = 3;

    /** Pinned catalogue servers, tried in the order given. Empty means discover them. */
    private List<URI> servers = new ArrayList<>();

    /** The host whose addresses name the catalogue's mirrors. */
    private String discoveryHost = "all.api.radio-browser.info";

    /** How long one play waits for the DNS lookup at most. */
    private Duration discoveryTimeout = Duration.ofSeconds(3);

    /** How long a discovered server list is used before it is discovered again. */
    private Duration serverListTtl = Duration.ofHours(1);

    /** How long a found station's lookup is reused. Play reports are never cached. */
    private Duration stationCacheTtl = Duration.ofMinutes(5);

    /** How many station lookups are kept; the oldest is dropped first. */
    private int stationCacheSize = 100;

    /**
     * The budget of one whole {@code /api/radio/**} request, server discovery and failover included.
     * Each attempt on one server gets at most half of it.
     */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /** {@code false} refuses stations marked HLS. */
    private boolean allowHls = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getResolveTimeout() {
        return resolveTimeout;
    }

    public void setResolveTimeout(Duration resolveTimeout) {
        this.resolveTimeout = resolveTimeout;
    }

    public int getMaxServersPerRequest() {
        return maxServersPerRequest;
    }

    public void setMaxServersPerRequest(int maxServersPerRequest) {
        this.maxServersPerRequest = maxServersPerRequest;
    }

    public List<URI> getServers() {
        return servers;
    }

    public void setServers(List<URI> servers) {
        this.servers = servers == null ? new ArrayList<>() : servers;
    }

    public String getDiscoveryHost() {
        return discoveryHost;
    }

    public void setDiscoveryHost(String discoveryHost) {
        this.discoveryHost = discoveryHost;
    }

    public Duration getDiscoveryTimeout() {
        return discoveryTimeout;
    }

    public void setDiscoveryTimeout(Duration discoveryTimeout) {
        this.discoveryTimeout = discoveryTimeout;
    }

    public Duration getServerListTtl() {
        return serverListTtl;
    }

    public void setServerListTtl(Duration serverListTtl) {
        this.serverListTtl = serverListTtl;
    }

    public Duration getStationCacheTtl() {
        return stationCacheTtl;
    }

    public void setStationCacheTtl(Duration stationCacheTtl) {
        this.stationCacheTtl = stationCacheTtl;
    }

    public int getStationCacheSize() {
        return stationCacheSize;
    }

    public void setStationCacheSize(int stationCacheSize) {
        this.stationCacheSize = stationCacheSize;
    }

    public boolean isAllowHls() {
        return allowHls;
    }

    public void setAllowHls(boolean allowHls) {
        this.allowHls = allowHls;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    // ---- validation: explicit checks so the message names the property --------------------------

    @AssertTrue(message = "multiroom.radio.connect-timeout: Positive")
    boolean isConnectTimeoutValid() {
        return positive(connectTimeout);
    }

    @AssertTrue(message = "multiroom.radio.read-timeout: Positive")
    boolean isReadTimeoutValid() {
        return positive(readTimeout);
    }

    @AssertTrue(message = "multiroom.radio.resolve-timeout: Positive")
    boolean isResolveTimeoutPositive() {
        return positive(resolveTimeout);
    }

    @AssertTrue(message = "multiroom.radio.resolve-timeout: at least connect-timeout")
    boolean isResolveTimeoutAtLeastConnectTimeout() {
        return !positive(resolveTimeout) || !positive(connectTimeout) || resolveTimeout.compareTo(connectTimeout) >= 0;
    }

    @AssertTrue(message = "multiroom.radio.max-servers-per-request: 1–10")
    boolean isMaxServersPerRequestValid() {
        return maxServersPerRequest >= 1 && maxServersPerRequest <= 10;
    }

    @AssertTrue(message = "multiroom.radio.discovery-timeout: Positive")
    boolean isDiscoveryTimeoutValid() {
        return positive(discoveryTimeout);
    }

    @AssertTrue(message = "multiroom.radio.server-list-ttl: Positive")
    boolean isServerListTtlValid() {
        return positive(serverListTtl);
    }

    @AssertTrue(message = "multiroom.radio.station-cache-ttl: Positive")
    boolean isStationCacheTtlValid() {
        return positive(stationCacheTtl);
    }

    @AssertTrue(message = "multiroom.radio.station-cache-size: 1–10000")
    boolean isStationCacheSizeValid() {
        return stationCacheSize >= 1 && stationCacheSize <= 10000;
    }

    @AssertTrue(message = "multiroom.radio.request-timeout: Positive")
    boolean isRequestTimeoutValid() {
        return positive(requestTimeout);
    }

    @AssertTrue(message = "multiroom.radio.servers: each entry must be an absolute http/https URL with a host, "
            + "no path beyond /, no query or fragment")
    boolean isServersValid() {
        return servers.stream().allMatch(RadioProperties::validServer);
    }

    @AssertTrue(message = "multiroom.radio.discovery-host: a plain host name (no scheme, port or path)")
    boolean isDiscoveryHostValid() {
        return discoveryHost != null && PLAIN_HOST.matcher(discoveryHost).matches();
    }

    @AssertTrue(message = "multiroom.radio.user-agent: Not blank, no CR or LF, at most 200 characters")
    boolean isUserAgentValid() {
        return userAgent == null
                || (!userAgent.isBlank() && userAgent.length() <= 200
                && userAgent.indexOf('\r') < 0 && userAgent.indexOf('\n') < 0);
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }

    private static boolean validServer(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        String path = uri.getRawPath();
        return (path == null || path.isEmpty() || path.equals("/"))
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
    }
}
