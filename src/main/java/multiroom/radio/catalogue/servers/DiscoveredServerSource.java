package multiroom.radio.catalogue.servers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import multiroom.radio.catalogue.CatalogueHttp;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.RadioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds the catalogue's mirrors lazily, on the first request: DNS first (forward lookup of the
 * discovery host, reverse lookup of each address), then {@code /json/servers} when DNS gave no
 * usable names. The list is replaced whole and never locked across a lookup. A list older than
 * {@code server-list-ttl} is still used at once while one background refresh (at most half of
 * {@code resolve-timeout}) replaces it; with no list, or when every server failed, discovery runs inside
 * the request and may use at most half of what that request has left. The DNS lookup runs off the
 * calling thread: JDK lookups have no timeout, and a late answer is kept for the next request instead
 * of being thrown away.
 */
public class DiscoveredServerSource implements ServerSource, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveredServerSource.class);
    private static final String NAME_SUFFIX = ".api.radio-browser.info";
    private static final Duration REFRESH_PAUSE = Duration.ofSeconds(60);

    /** A discovered list. {@code seq} orders discoveries by when they started. */
    private record ServerList(List<CatalogueServer> servers, Instant discoveredAt, long seq, int next, boolean stale) {

        ServerList withNext(int index) {
            return new ServerList(servers, discoveredAt, seq, index, stale);
        }

        ServerList asStale() {
            return new ServerList(servers, discoveredAt, seq, next, true);
        }
    }

    private final RadioProperties properties;
    private final DnsResolver dns;
    private final CatalogueHttp http;
    private final ObjectMapper mapper;
    private final RadioMetrics metrics;
    private final Clock clock;
    private final Random random;
    private final URI fallbackBase;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "radio-discovery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<ServerList> list = new AtomicReference<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicReference<Instant> refreshPausedUntil = new AtomicReference<>(Instant.MIN);

    /** Creates the source; nothing is looked up until {@link #servers(Deadline)} is first called. */
    public DiscoveredServerSource(RadioProperties properties, DnsResolver dns, CatalogueHttp http, ObjectMapper mapper,
                                  RadioMetrics metrics, Clock clock, Random random, URI fallbackBase) {
        this.properties = properties;
        this.dns = dns;
        this.http = http;
        this.mapper = mapper;
        this.metrics = metrics;
        this.clock = clock;
        this.random = random;
        this.fallbackBase = fallbackBase;
    }

    @Override
    public List<CatalogueServer> servers(Deadline deadline) {
        ServerList current = list.get();
        if (current != null && !current.stale()) {
            if (Duration.between(current.discoveredAt(), clock.instant()).compareTo(properties.getServerListTtl()) >= 0) {
                // Past its time-to-live the list is still good enough: use it now and look again in the
                // background, so that an hourly lookup on a network with slow DNS never makes a
                // request (a play, a search) wait for it.
                startRefresh();
            }
            return rotated(current);
        }
        return discover(deadline);
    }

    /** Whether a background refresh is running; for tests, which wait for it to finish. */
    boolean refreshInProgress() {
        return refreshing.get();
    }

    private void startRefresh() {
        if (clock.instant().isBefore(refreshPausedUntil.get()) || !refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::refresh);
        } catch (RuntimeException e) {
            refreshing.set(false);
        }
    }

    /**
     * One background refresh: DNS, then the server-list fallback, bounded by half of
     * {@code resolve-timeout}. A failure leaves the old list in place and pauses further tries for a
     * minute, so a broken network is not hammered by every request.
     */
    private void refresh() {
        boolean found = false;
        try {
            long seq = sequence.incrementAndGet();
            Deadline discovery = Deadline.after(clock, properties.getResolveTimeout().dividedBy(2));
            found = !dnsDiscovery(seq, discovery).isEmpty() || !serverListDiscovery(seq, discovery).isEmpty();
        } catch (RuntimeException e) {
            LOG.debug("Background refresh of the catalogue server list failed", e);
        } finally {
            if (!found) {
                refreshPausedUntil.set(clock.instant().plus(REFRESH_PAUSE));
            }
            refreshing.set(false);
        }
    }

    /** What a request may spend on discovery: half of what it has left, so some is left for its own call. */
    static Duration shareOf(Duration remaining) {
        return remaining.dividedBy(2);
    }

    @Override
    public void answered(CatalogueServer server) {
        list.updateAndGet(current -> {
            if (current == null) {
                return null;
            }
            int index = current.servers().indexOf(server);
            return index < 0 ? current : current.withNext(index);
        });
    }

    @Override
    public void allFailed() {
        list.updateAndGet(current -> current == null ? null : current.asStale());
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static List<CatalogueServer> rotated(ServerList current) {
        List<CatalogueServer> servers = current.servers();
        if (current.next() == 0) {
            return servers;
        }
        List<CatalogueServer> out = new ArrayList<>(servers.subList(current.next(), servers.size()));
        out.addAll(servers.subList(0, current.next()));
        return out;
    }

    private List<CatalogueServer> discover(Deadline callerDeadline) {
        long seq = sequence.incrementAndGet();
        Deadline discovery = Deadline.after(clock, shareOf(callerDeadline.remaining()));

        List<CatalogueServer> fromDns = dnsDiscovery(seq, discovery);
        if (!fromDns.isEmpty()) {
            return fromDns;
        }
        List<CatalogueServer> fromList = serverListDiscovery(seq, discovery);
        if (!fromList.isEmpty()) {
            return fromList;
        }
        throw new CatalogueUnavailableException("No catalogue servers could be discovered", List.of());
    }

    private List<CatalogueServer> dnsDiscovery(long seq, Deadline discovery) {
        CompletableFuture<List<CatalogueServer>> lookup = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    List<CatalogueServer> names = dnsNames();
                    if (!names.isEmpty()) {
                        store(names, seq, true);
                    }
                    lookup.complete(names);
                } catch (RuntimeException e) {
                    lookup.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            metrics.discovery(RadioMetrics.SOURCE_DNS, RadioMetrics.DISCOVERY_ERROR);
            return List.of();
        }
        Duration wait = discovery.cap(properties.getDiscoveryTimeout());
        try {
            List<CatalogueServer> names = lookup.get(wait.toMillis(), TimeUnit.MILLISECONDS);
            metrics.discovery(RadioMetrics.SOURCE_DNS, names.isEmpty() ? RadioMetrics.DISCOVERY_EMPTY : RadioMetrics.DISCOVERY_OK);
            if (!names.isEmpty()) {
                LOG.info("Discovered {} catalogue servers through dns", names.size());
            }
            return names;
        } catch (TimeoutException e) {
            metrics.discovery(RadioMetrics.SOURCE_DNS, RadioMetrics.DISCOVERY_TIMEOUT);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.discovery(RadioMetrics.SOURCE_DNS, RadioMetrics.DISCOVERY_TIMEOUT);
            return List.of();
        } catch (ExecutionException e) {
            metrics.discovery(RadioMetrics.SOURCE_DNS, RadioMetrics.DISCOVERY_ERROR);
            return List.of();
        }
    }

    private List<CatalogueServer> dnsNames() {
        Set<String> names = new LinkedHashSet<>();
        for (InetAddress address : dns.lookup(properties.getDiscoveryHost())) {
            Optional<String> name = dns.reverse(address);
            name.map(n -> n.toLowerCase(Locale.ROOT)).filter(DiscoveredServerSource::usableName).ifPresent(names::add);
        }
        return toServers(names);
    }

    private List<CatalogueServer> serverListDiscovery(long seq, Deadline discovery) {
        if (discovery.expired()) {
            metrics.discovery(RadioMetrics.SOURCE_SERVER_LIST, RadioMetrics.DISCOVERY_TIMEOUT);
            return List.of();
        }
        CatalogueHttp.Attempt attempt = http.get(
                URI.create(trimTrailingSlash(fallbackBase.toString()) + "/json/servers"), discovery);
        if (!attempt.ok()) {
            metrics.discovery(RadioMetrics.SOURCE_SERVER_LIST,
                    RadioMetrics.RESULT_TIMEOUT.equals(attempt.result())
                            ? RadioMetrics.DISCOVERY_TIMEOUT : RadioMetrics.DISCOVERY_ERROR);
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        try {
            JsonNode root = mapper.readTree(attempt.body());
            if (root != null && root.isArray()) {
                for (JsonNode entry : root) {
                    JsonNode name = entry.get("name");
                    if (name != null && name.isTextual() && usableName(name.textValue().toLowerCase(Locale.ROOT))) {
                        names.add(name.textValue().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            metrics.discovery(RadioMetrics.SOURCE_SERVER_LIST, RadioMetrics.DISCOVERY_ERROR);
            return List.of();
        }
        List<CatalogueServer> servers = toServers(names);
        if (servers.isEmpty()) {
            metrics.discovery(RadioMetrics.SOURCE_SERVER_LIST, RadioMetrics.DISCOVERY_EMPTY);
            return List.of();
        }
        metrics.discovery(RadioMetrics.SOURCE_SERVER_LIST, RadioMetrics.DISCOVERY_OK);
        LOG.info("Discovered {} catalogue servers through server_list", servers.size());
        return store(servers, seq, false);
    }

    private static boolean usableName(String name) {
        return name.endsWith(NAME_SUFFIX) && name.length() > NAME_SUFFIX.length();
    }

    private List<CatalogueServer> toServers(Set<String> names) {
        List<CatalogueServer> servers = new ArrayList<>();
        for (String name : names) {
            try {
                servers.add(CatalogueServer.of(URI.create("https://" + name)));
            } catch (IllegalArgumentException e) {
                // a name that is not a valid host is skipped
            }
        }
        Collections.shuffle(servers, random);
        return servers;
    }

    /**
     * Stores a discovered list unless a fresher one is held: a DNS answer replaces the list of its own
     * discovery (the fallback's) and of earlier ones; the fallback replaces only earlier ones.
     * Returns the list now held.
     */
    private List<CatalogueServer> store(List<CatalogueServer> servers, long seq, boolean fromDns) {
        ServerList candidate = new ServerList(List.copyOf(servers), clock.instant(), seq, 0, false);
        ServerList held = list.updateAndGet(current -> {
            if (current == null || current.seq() < seq || (fromDns && current.seq() == seq)) {
                return candidate;
            }
            return current;
        });
        return rotated(held);
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
