package multiroom.radio.catalogue.servers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import multiroom.radio.catalogue.CatalogueHttp;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.Fixtures;
import multiroom.radio.support.MutableClock;
import multiroom.radio.support.RecordingMetrics;
import multiroom.radio.support.Wire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveredServerSourceTest {

    private static final InetAddress A1 = address(1);
    private static final InetAddress A2 = address(2);
    private static final InetAddress A3 = address(3);
    private static final InetAddress A4 = address(4);

    private static InetAddress address(int last) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, (byte) last});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A DNS stub with a fixed answer; the forward lookup may block. */
    private static class FakeDns implements DnsResolver {
        final AtomicInteger lookups = new AtomicInteger();
        final List<String> hosts = new CopyOnWriteArrayList<>();
        final Map<InetAddress, String> names = Map.of(A1, "de1.api.radio-browser.info", A2, "fi1.api.radio-browser.info",
                A3, "nl1.api.radio-browser.info", A4, "unrelated.example.test");

        @Override
        public List<InetAddress> lookup(String host) {
            lookups.incrementAndGet();
            hosts.add(host);
            return List.of(A1, A2, A3, A4);
        }

        @Override
        public Optional<String> reverse(InetAddress address) {
            return Optional.ofNullable(names.get(address));
        }
    }

    private static final List<String> EXPECTED_NAMES = List.of("de1.api.radio-browser.info",
            "fi1.api.radio-browser.info", "nl1.api.radio-browser.info");

    private WireMockServer wire;
    private RadioProperties properties;
    private RecordingMetrics metrics;
    private final List<DiscoveredServerSource> sources = new ArrayList<>();

    @org.junit.jupiter.api.BeforeAll
    static void warm() {
        Wire.warmUp();
    }

    @BeforeEach
    void start() {
        wire = new WireMockServer(options().dynamicPort());
        wire.start();
        properties = new RadioProperties();
        properties.setConnectTimeout(Duration.ofMillis(300));
        properties.setReadTimeout(Duration.ofMillis(300));
        properties.setDiscoveryTimeout(Duration.ofMillis(300));
        properties.setResolveTimeout(Duration.ofSeconds(4));
        metrics = new RecordingMetrics();
    }

    @AfterEach
    void stop() {
        wire.stop();
        sources.forEach(DiscoveredServerSource::close);
    }

    private DiscoveredServerSource source(DnsResolver dns, Clock clock, URI fallback) {
        DiscoveredServerSource source = new DiscoveredServerSource(properties, dns, new CatalogueHttp(properties),
                new ObjectMapper(), metrics, clock, new Random(42), fallback);
        sources.add(source);
        return source;
    }

    private DiscoveredServerSource source(DnsResolver dns) {
        return source(dns, Clock.systemUTC(), URI.create("http://localhost:" + wire.port()));
    }

    private static Deadline deadline() {
        return Deadline.after(Clock.systemUTC(), Duration.ofSeconds(10));
    }

    private static List<String> hosts(List<CatalogueServer> servers) {
        return servers.stream().map(CatalogueServer::tag).toList();
    }

    private static List<String> shuffled(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        Collections.shuffle(copy, new Random(42));
        return copy;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    // ---- DNS path -------------------------------------------------------------------------------

    @Test
    void doesNothingAtConstruction() {
        FakeDns dns = new FakeDns();
        source(dns);
        assertThat(dns.lookups).hasValue(0);
        assertThat(wire.getAllServeEvents()).isEmpty();
    }

    @Test
    void dnsNamesUnderTheCatalogueDomainBecomeShuffledHttpsServers() {
        FakeDns dns = new FakeDns();
        DiscoveredServerSource source = source(dns);
        List<CatalogueServer> servers = source.servers(deadline());
        assertThat(hosts(servers)).containsExactlyElementsOf(shuffled(EXPECTED_NAMES));
        assertThat(servers).allSatisfy(s -> assertThat(s.baseUri().getScheme()).isEqualTo("https"));
        assertThat(dns.hosts).containsExactly("all.api.radio-browser.info");
        assertThat(metrics.discoveries).containsExactly(List.of("dns", "ok"));
        assertThat(wire.getAllServeEvents()).isEmpty();
    }

    @Test
    void theListIsCachedSoTheSecondCallLooksNothingUp() {
        FakeDns dns = new FakeDns();
        DiscoveredServerSource source = source(dns);
        List<CatalogueServer> first = source.servers(deadline());
        assertThat(source.servers(deadline())).isEqualTo(first);
        assertThat(dns.lookups).hasValue(1);
    }

    @Test
    void aBlockedLookupFailsWithinTheDiscoveryTimeout() {
        CountDownLatch never = new CountDownLatch(1);
        DnsResolver blocked = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                await(never);
                return List.of();
            }
        };
        DiscoveredServerSource source = source(blocked, Clock.systemUTC(), URI.create("http://localhost:" + Wire.closedPort()));
        long start = System.nanoTime();
        assertThatThrownBy(() -> source.servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        assertThat(metrics.discoveries.get(0)).containsExactly("dns", "timeout");
        never.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- /json/servers fallback -----------------------------------------------------------------

    private DnsResolver emptyDns() {
        return new FakeDns() {
            @Override
            public Optional<String> reverse(InetAddress address) {
                return Optional.empty();
            }
        };
    }

    @Test
    void noUsableReverseNamesFallsBackToTheServerList() {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody(Fixtures.load("servers.json"))));
        DiscoveredServerSource source = source(emptyDns());
        List<CatalogueServer> servers = source.servers(deadline());
        assertThat(hosts(servers)).containsExactlyInAnyOrder("de1.api.radio-browser.info", "fi1.api.radio-browser.info");
        assertThat(metrics.discoveries).containsExactly(List.of("dns", "empty"), List.of("server_list", "ok"));
    }

    @Test
    void anAbsurdServerListBodyCountsAsNothingFound() {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody("{\"not\":\"an array\"}")));
        assertThatThrownBy(() -> source(emptyDns()).servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        assertThat(metrics.discoveries).contains(List.of("server_list", "empty"));
    }

    @Test
    void anUnreadableServerListBodyIsAnError() {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody("<html>")));
        assertThatThrownBy(() -> source(emptyDns()).servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        assertThat(metrics.discoveries).contains(List.of("server_list", "error"));
    }

    @Test
    void aFailingServerListIsAnErrorAndATimeoutIsATimeout() {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> source(emptyDns()).servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        assertThat(metrics.discoveries).contains(List.of("server_list", "error"));

        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withFixedDelay(1500).withBody("[]")));
        metrics.discoveries.clear();
        assertThatThrownBy(() -> source(emptyDns()).servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        assertThat(metrics.discoveries).contains(List.of("server_list", "timeout"));
    }

    // RADIO-002: an expired list is no longer rediscovered inside the request; it is returned at once
    // and replaced by a background refresh, so this checks the refresh is not queued behind the stuck lookup.
    @Test
    void aTimedOutDnsLookupFallsBackToTheServerListAndIsNotQueuedBehindTheNext() throws Exception {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody(Fixtures.load("servers.json"))));
        CountDownLatch never = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        DnsResolver dns = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                if (calls.incrementAndGet() == 1) {
                    await(never);
                    return List.of();
                }
                return super.lookup(host);
            }
        };
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        assertThat(hosts(source.servers(deadline()))).hasSize(2);

        // expire the list: the old one is returned at once, and the refresh's lookup completes
        // although the first is still stuck
        clock.advance(Duration.ofHours(2));
        assertThat(hosts(source.servers(deadline()))).hasSize(2);
        waitUntil(() -> hosts(source.servers(deadline())).size() == 3);
        assertThat(hosts(source.servers(deadline()))).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);
        never.countDown();
    }

    @Test
    void aLateDnsAnswerIsKeptForTheNextCallWithoutAnotherLookupOrRequest() throws Exception {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withStatus(500)));
        CountDownLatch release = new CountDownLatch(1);
        FakeDns dns = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                await(release);
                return super.lookup(host);
            }
        };
        DiscoveredServerSource source = source(dns);
        assertThatThrownBy(() -> source.servers(deadline())).isInstanceOf(CatalogueUnavailableException.class);
        int requestsBefore = wire.getAllServeEvents().size();

        release.countDown();
        waitUntil(() -> dns.lookups.get() == 1);
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        List<CatalogueServer> next = null;
        while (next == null && System.nanoTime() < end) {
            try {
                next = source.servers(Deadline.after(Clock.systemUTC(), Duration.ofMillis(1)));
            } catch (CatalogueUnavailableException e) {
                Thread.sleep(10);
            }
        }
        assertThat(next).isNotNull();
        assertThat(hosts(next)).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);
        assertThat(dns.lookups).hasValue(1);
        assertThat(wire.getAllServeEvents()).hasSize(requestsBefore);
    }

    @Test
    void aLateAnswerDoesNotReplaceAListDiscoveredAfterItStarted() throws Exception {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody(Fixtures.load("servers.json"))));
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FakeDns dns = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                if (calls.incrementAndGet() == 1) {
                    await(release);
                    return List.of(A3);
                }
                return List.of(A1);
            }
        };
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        source.servers(deadline());              // first lookup stuck: list comes from /json/servers
        clock.advance(Duration.ofHours(2));
        source.servers(deadline());              // RADIO-002: the old list at once, a refresh starts
        waitUntil(() -> hosts(source.servers(deadline())).equals(List.of("de1.api.radio-browser.info")));

        release.countDown();                      // the first lookup answers late with nl1
        Thread.sleep(300);
        assertThat(hosts(source.servers(deadline()))).containsExactly("de1.api.radio-browser.info");
    }

    // RADIO-002: discovery inside a request now gets half of what the caller has left (it was half of
    // resolve-timeout); resolve-timeout's half bounds the background refresh instead.
    @Test
    void discoveryInsideARequestStopsAtHalfOfWhatTheCallerHasLeft() {
        properties.setDiscoveryTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(3));
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withFixedDelay(2000).withBody("[]")));
        CountDownLatch never = new CountDownLatch(1);
        DnsResolver dns = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                await(never);
                return List.of();
            }
        };
        Deadline twoSeconds = Deadline.after(Clock.systemUTC(), Duration.ofSeconds(2));
        long start = System.nanoTime();
        assertThatThrownBy(() -> source(dns).servers(twoSeconds)).isInstanceOf(CatalogueUnavailableException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(900)).isLessThan(Duration.ofMillis(1400));
        never.countDown();
    }

    @Test
    void theShareIsHalfOfWhatTheCallerHasLeft() {
        assertThat(DiscoveredServerSource.shareOf(Duration.ofSeconds(5))).isEqualTo(Duration.ofMillis(2500));
        assertThat(DiscoveredServerSource.shareOf(Duration.ofSeconds(10))).isEqualTo(Duration.ofSeconds(5));
        assertThat(DiscoveredServerSource.shareOf(Duration.ofSeconds(4))).isEqualTo(Duration.ofSeconds(2));
        assertThat(DiscoveredServerSource.shareOf(Duration.ZERO)).isEqualTo(Duration.ZERO);
    }

    // ---- TTL, rotation, concurrency -------------------------------------------------------------

    @Test
    void theListIsRefreshedInTheBackgroundAfterTheTtl() throws Exception {
        FakeDns dns = new FakeDns();
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        source.servers(deadline());
        clock.advance(Duration.ofMinutes(59));
        source.servers(deadline());
        assertThat(dns.lookups).hasValue(1);
        clock.advance(Duration.ofMinutes(2));
        source.servers(deadline());
        waitUntil(() -> dns.lookups.get() == 2);
        waitUntil(() -> !source.refreshInProgress());
    }

    @Test
    void theListIsDiscoveredAgainAfterEveryServerFailed() {
        FakeDns dns = new FakeDns();
        DiscoveredServerSource source = source(dns);
        source.servers(deadline());
        source.allFailed();
        source.servers(deadline());
        assertThat(dns.lookups).hasValue(2);
    }

    @Test
    void answeredRotatesTheListToStartThere() {
        DiscoveredServerSource source = source(new FakeDns());
        List<CatalogueServer> list = source.servers(deadline());
        source.answered(list.get(1));
        assertThat(source.servers(deadline())).containsExactly(list.get(1), list.get(2), list.get(0));
        source.answered(new CatalogueServer(URI.create("https://other.example.test"), "other.example.test"));
        assertThat(source.servers(deadline())).containsExactly(list.get(1), list.get(2), list.get(0));
    }

    @Test
    void answeredAndAllFailedBeforeAnyDiscoveryAreHarmless() {
        DiscoveredServerSource source = source(new FakeDns());
        source.answered(new CatalogueServer(URI.create("https://x.example.test"), "x.example.test"));
        source.allFailed();
        assertThat(source.servers(deadline())).hasSize(3);
    }

    @Test
    void twoConcurrentFirstCallsDoNotWaitOnEachOthersLookup() throws Exception {
        CountDownLatch bothInside = new CountDownLatch(2);
        FakeDns dns = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                bothInside.countDown();
                await(bothInside);
                return super.lookup(host);
            }
        };
        properties.setDiscoveryTimeout(Duration.ofSeconds(3));
        DiscoveredServerSource source = source(dns);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<CatalogueServer>> one = pool.submit(() -> source.servers(deadline()));
            Future<List<CatalogueServer>> two = pool.submit(() -> source.servers(deadline()));
            assertThat(one.get(5, TimeUnit.SECONDS)).hasSize(3);
            assertThat(two.get(5, TimeUnit.SECONDS)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingLookupCountsAsAnErrorAndFallsBack() {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withBody(Fixtures.load("servers.json"))));
        DnsResolver broken = new FakeDns() {
            @Override
            public List<InetAddress> lookup(String host) {
                throw new IllegalStateException("resolver down");
            }
        };
        assertThat(source(broken).servers(deadline())).hasSize(2);
        assertThat(metrics.discoveries).containsExactly(List.of("dns", "error"), List.of("server_list", "ok"));
    }

    // ---- expired list: used at once, refreshed in the background (RADIO-002) --------------------

    private static final class ControlledDns extends FakeDns {
        volatile CountDownLatch gate = new CountDownLatch(0);
        volatile boolean empty;
        final AtomicInteger entered = new AtomicInteger();

        @Override
        public List<InetAddress> lookup(String host) {
            entered.incrementAndGet();
            await(gate);
            List<InetAddress> found = super.lookup(host);
            return empty ? List.of() : found;
        }
    }

    @Test
    void anExpiredListIsReturnedAtOnceAndOneBackgroundRefreshReplacesIt() throws Exception {
        ControlledDns dns = new ControlledDns();
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        List<String> first = hosts(source.servers(deadline()));
        clock.advance(Duration.ofHours(2));
        dns.gate = new CountDownLatch(1);

        long start = System.nanoTime();
        List<String> during = hosts(source.servers(deadline()));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
        assertThat(during).isEqualTo(first);

        dns.gate.countDown();
        waitUntil(() -> !source.refreshInProgress());
        assertThat(dns.lookups).hasValue(2);
        assertThat(hosts(source.servers(deadline()))).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);
    }

    @Test
    void twoRequestsDuringOneRefreshStartOnlyOne() throws Exception {
        ControlledDns dns = new ControlledDns();
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        source.servers(deadline());
        clock.advance(Duration.ofHours(2));
        dns.gate = new CountDownLatch(1);

        source.servers(deadline());
        source.servers(deadline());
        source.servers(deadline());
        waitUntil(() -> dns.entered.get() == 2);
        Thread.sleep(100);
        assertThat(dns.entered).hasValue(2);
        dns.gate.countDown();
        waitUntil(() -> !source.refreshInProgress());
        assertThat(dns.entered).hasValue(2);
    }

    @Test
    void aFailedRefreshKeepsTheOldListAndNoneStartsForSixtySecondsThenOneDoes() throws Exception {
        wire.stubFor(get(urlEqualTo("/json/servers")).willReturn(aResponse().withStatus(500)));
        ControlledDns dns = new ControlledDns();
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
        List<String> first = hosts(source.servers(deadline()));
        clock.advance(Duration.ofHours(2));
        dns.empty = true;

        source.servers(deadline());
        waitUntil(() -> dns.lookups.get() == 2);
        waitUntil(() -> !source.refreshInProgress());
        assertThat(hosts(source.servers(deadline()))).isEqualTo(first);     // the old list stays

        clock.advance(Duration.ofSeconds(59));
        source.servers(deadline());
        Thread.sleep(100);
        assertThat(dns.lookups).hasValue(2);                                // still paused

        clock.advance(Duration.ofSeconds(2));
        dns.empty = false;
        source.servers(deadline());
        waitUntil(() -> dns.lookups.get() == 3);
        waitUntil(() -> !source.refreshInProgress());
        assertThat(hosts(source.servers(deadline()))).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);
    }

    @Test
    void aRefreshIsGivenUpAfterHalfOfTheResolveTimeout() throws Exception {
        properties.setResolveTimeout(Duration.ofMillis(600));
        properties.setDiscoveryTimeout(Duration.ofSeconds(5));
        ControlledDns dns = new ControlledDns();
        MutableClock clock = new MutableClock();
        DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + Wire.closedPort()));
        source.servers(deadline());
        clock.advance(Duration.ofHours(2));
        dns.gate = new CountDownLatch(1);

        long start = System.nanoTime();
        source.servers(deadline());
        waitUntil(() -> source.refreshInProgress());
        waitUntil(() -> !source.refreshInProgress());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        dns.gate.countDown();
    }

    @Test
    void withNoListDiscoveryRunsInsideTheRequestAndAFullyFailedListToo() {
        FakeDns dns = new FakeDns();
        DiscoveredServerSource source = source(dns);
        assertThat(source.servers(deadline())).hasSize(3);          // no list: inside the request
        source.allFailed();
        assertThat(source.servers(deadline())).hasSize(3);          // all failed: inside the request again
        assertThat(dns.lookups).hasValue(2);
    }

    @Test
    void aBackgroundRefreshCountsAsADiscoveryAndLogsAtInfoWithSourceAndCount() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DiscoveredServerSource.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            FakeDns dns = new FakeDns();
            MutableClock clock = new MutableClock();
            DiscoveredServerSource source = source(dns, clock, URI.create("http://localhost:" + wire.port()));
            source.servers(deadline());
            appender.list.clear();
            metrics.discoveries.clear();
            clock.advance(Duration.ofHours(2));
            source.servers(deadline());
            waitUntil(() -> !source.refreshInProgress() && dns.lookups.get() == 2);
            assertThat(metrics.discoveries).containsExactly(List.of("dns", "ok"));
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
                assertThat(event.getFormattedMessage()).contains("3").contains("dns");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
