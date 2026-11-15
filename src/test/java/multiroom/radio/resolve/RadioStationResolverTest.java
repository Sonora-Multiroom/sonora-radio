package multiroom.radio.resolve;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import multiroom.api.io.resolve.EndpointRefreshMode;
import multiroom.api.io.resolve.InputResolutionException;
import multiroom.api.io.resolve.InputResolutionFailure;
import multiroom.api.io.resolve.ResolvedInputEndpoint;
import multiroom.api.io.resolve.SupportedCodecRegistry;
import multiroom.api.model.AudioInputDefinition;
import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.CatalogueProtocolException;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.PlayReport;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.Station;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.support.Fixtures;
import multiroom.radio.support.RecordingMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RadioStationResolverTest {

    private static final UUID ID = Fixtures.STATION;
    private static final String URI_TEXT = "radiobrowser://" + ID + "#label-not-to-be-logged";

    private RadioBrowserClient client;
    private RadioProperties properties;
    private RecordingMetrics metrics;
    private SupportedCodecRegistry registry;
    private RadioStationResolver resolver;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    private static Station station(String codec, boolean hls, String streamUrl) {
        return multiroom.radio.support.Fixtures.station(ID, "Test FM", streamUrl, codec, hls);
    }

    private static AudioInputDefinition input(String uri) {
        return AudioInputDefinition.builder().uri(uri).build();
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        client = mock(RadioBrowserClient.class);
        properties = new RadioProperties();
        metrics = new RecordingMetrics();
        registry = new SupportedCodecRegistry() {
            @Override
            public Set<String> getSupportedCodecNames() {
                return Set.of("mp3", "aac", "opus", "flac");
            }

            @Override
            public boolean isSupported(String codecName) {
                return getSupportedCodecNames().contains(codecName);
            }
        };
        ObjectProvider<SupportedCodecRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(invocation -> registry);
        resolver = new RadioStationResolver(client, new CodecPolicy(provider), properties, metrics, Clock.systemUTC());

        logger = (Logger) LoggerFactory.getLogger(RadioStationResolver.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private void lookupAnswers(Station station, String server) {
        when(client.station(eq(ID), any())).thenReturn(server == null
                ? CatalogueAnswer.cached(Optional.of(station)) : CatalogueAnswer.from(Optional.of(station), server));
    }

    private void playAnswers(PlayReport report, String server) {
        when(client.reportPlay(eq(ID), any())).thenReturn(CatalogueAnswer.from(report, server));
    }

    private void healthy() {
        lookupAnswers(station("MP3", false, "https://stream.example.test/live.mp3"), "srv1");
        playAnswers(new PlayReport(true, "https://stream.example.test/live-current.mp3"), "srv1");
    }

    private InputResolutionException failure(String uri) {
        return org.junit.jupiter.api.Assertions.assertThrows(InputResolutionException.class,
                () -> resolver.resolve(input(uri)));
    }

    // ---- supports -------------------------------------------------------------------------------

    @Test
    void supportsOnlyTheRadiobrowserScheme() {
        assertThat(resolver.supports(input("radiobrowser://" + ID))).isTrue();
        assertThat(resolver.supports(input("RADIOBROWSER://" + ID))).isTrue();
        assertThat(resolver.supports(input("radiobrowser://kissfm"))).isTrue();
        assertThat(resolver.supports(input("http://stream.example.test/a"))).isFalse();
        assertThat(resolver.supports(input("tts://" + ID))).isFalse();
        assertThat(resolver.supports(input("soundcloud://x"))).isFalse();
        assertThat(resolver.supports(null)).isFalse();
    }

    @Test
    void supportsNeverThrowsAndNeverCallsTheClient() {
        assertThat(resolver.supports(input(null))).isFalse();
        assertThat(resolver.supports(input(""))).isFalse();
        assertThat(resolver.supports(input("   "))).isFalse();
        assertThat(resolver.supports(input("radiobrowser://a b"))).isFalse();
        assertThat(resolver.supports(input("::::"))).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void orderIs120() {
        assertThat(resolver.getOrder()).isEqualTo(120);
    }

    // ---- the happy path -------------------------------------------------------------------------

    @Test
    void resolvesToThePlayReportAddress() {
        healthy();
        AudioInputDefinition given = input(URI_TEXT);
        ResolvedInputEndpoint endpoint = resolver.resolve(given);

        assertThat(endpoint.effectiveUri()).isEqualTo(URI.create("https://stream.example.test/live-current.mp3"));
        assertThat(endpoint.headers()).isEmpty();
        assertThat(endpoint.expiresAt()).isNull();
        assertThat(endpoint.refreshMode()).isEqualTo(EndpointRefreshMode.RE_RESOLVE_ORIGINAL_INPUT);
        assertThat(endpoint.resolverId()).isEqualTo("radiobrowser");
        assertThat(endpoint.title()).isEqualTo("Test FM");
        assertThat(endpoint.creator()).isNull();
        assertThat(endpoint.originalInput()).isSameAs(given);
    }

    @Test
    void looksUpThenReportsInThatOrderAndTheSameDeadlineIsUsed() {
        healthy();
        resolver.resolve(input(URI_TEXT));
        var order = inOrder(client);
        ArgumentCaptor<Deadline> first = ArgumentCaptor.forClass(Deadline.class);
        ArgumentCaptor<Deadline> second = ArgumentCaptor.forClass(Deadline.class);
        order.verify(client).station(eq(ID), first.capture());
        order.verify(client).reportPlay(eq(ID), second.capture());
        assertThat(first.getValue()).isSameAs(second.getValue());
    }

    @Test
    void resolvingTwiceReportsTheStationTwice() {
        healthy();
        resolver.resolve(input(URI_TEXT));
        resolver.resolve(input(URI_TEXT));
        verify(client, times(2)).reportPlay(eq(ID), any());
    }

    @Test
    void aNameWithControlCharactersStillBecomesATitle() {
        lookupAnswers(multiroom.radio.support.Fixtures.station(ID, "Test\u0000\n FM", "https://s.example.test/a", "MP3", false), "srv1");
        playAnswers(new PlayReport(true, "https://s.example.test/a"), "srv1");
        assertThat(resolver.resolve(input(URI_TEXT)).title()).isEqualTo("Test FM");
    }

    // ---- failures -------------------------------------------------------------------------------

    @Test
    void malformedInputFailsInvalidWithoutAnyRequest() {
        for (String uri : new String[]{"radiobrowser://kissfm", "radiobrowser://", null,
                "radiobrowser://" + ID + "/x", "radiobrowser://" + ID + "?a=b"}) {
            InputResolutionException e = failure(uri);
            assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.INVALID_INPUT);
        }
        verifyNoInteractions(client);
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsOnly("invalid_uuid");
    }

    @Test
    void theMalformedValueInTheMessageIsTruncatedTo64Characters() {
        InputResolutionException e = failure("radiobrowser://" + "k".repeat(500));
        assertThat(e.getMessage()).contains("k".repeat(40)).doesNotContain("k".repeat(65));
    }

    @Test
    void anUnknownStationFailsInvalid() {
        when(client.station(eq(ID), any())).thenReturn(CatalogueAnswer.from(Optional.empty(), "srv1"));
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.INVALID_INPUT);
        assertThat(e.getMessage()).contains(ID.toString()).contains("not found in radio-browser");
        verify(client, never()).reportPlay(any(), any());
    }

    @Test
    void aRefusedCodecFailsUnsupportedAndNeverReportsAPlay() {
        registry = new SupportedCodecRegistry() {
            @Override
            public Set<String> getSupportedCodecNames() {
                return Set.of("mp3");
            }

            @Override
            public boolean isSupported(String codecName) {
                return codecName.equals("mp3");
            }
        };
        lookupAnswers(station("AAC+", false, "https://s.example.test/a"), "srv1");
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
        assertThat(e.getMessage()).contains(ID.toString()).contains("AAC+");
        verify(client, never()).reportPlay(any(), any());
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsExactly("unsupported_codec");
    }

    @Test
    void hlsWithHlsRefusedFailsUnsupportedAndNeverReportsAPlay() {
        properties.setAllowHls(false);
        lookupAnswers(station("AAC", true, "https://s.example.test/a.m3u8"), "srv1");
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
        assertThat(e.getMessage()).contains(ID.toString()).contains("HLS");
        verify(client, never()).reportPlay(any(), any());
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsExactly("hls_refused");
    }

    @Test
    void hlsWithHlsAllowedPlays() {
        lookupAnswers(station("AAC", true, "https://s.example.test/a.m3u8"), "srv1");
        playAnswers(new PlayReport(true, "https://s.example.test/a.m3u8"), "srv1");
        assertThat(resolver.resolve(input(URI_TEXT)).effectiveUri().getPath()).endsWith(".m3u8");
    }

    @Test
    void aNonHttpPlayReportAddressIsRefusedNotAFallback() {
        lookupAnswers(station("MP3", false, "https://stream.example.test/ok"), "srv1");
        playAnswers(new PlayReport(true, "file:///etc/passwd"), "srv1");
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
        assertThat(e.getMessage()).contains(ID.toString()).contains("file").doesNotContain("passwd");
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsExactly("unsupported_address");
    }

    @Test
    void anUnavailableCatalogueFailsUpstreamUnavailable() {
        when(client.station(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of("a")));
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UPSTREAM_UNAVAILABLE);
        assertThat(e.getMessage()).contains(ID.toString()).contains("radio-browser unavailable");
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::server).containsExactly("none");
    }

    @Test
    void anUnreadableAnswerFailsUpstreamProtocolError() {
        when(client.station(eq(ID), any())).thenThrow(new CatalogueProtocolException("bad"));
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UPSTREAM_PROTOCOL_ERROR);
        assertThat(e.getMessage()).contains(ID.toString());
    }

    @Test
    void anUnexpectedExceptionIsWrappedNeverRaw() {
        when(client.station(eq(ID), any())).thenThrow(new IllegalStateException("boom"));
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UPSTREAM_PROTOCOL_ERROR);
        assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsExactly("upstream_protocol_error");
    }

    // ---- play report fallback -------------------------------------------------------------------

    @Test
    void aFailedPlayReportFallsBackToTheLookupAddress() {
        List<java.util.function.Consumer<RadioBrowserClient>> failures = List.of(
                c -> when(c.reportPlay(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of())),
                c -> when(c.reportPlay(eq(ID), any())).thenThrow(new CatalogueProtocolException("bad")),
                c -> when(c.reportPlay(eq(ID), any())).thenReturn(CatalogueAnswer.from(new PlayReport(false, "https://x.test/"), "s")),
                c -> when(c.reportPlay(eq(ID), any())).thenReturn(CatalogueAnswer.from(new PlayReport(true, null), "s")),
                c -> when(c.reportPlay(eq(ID), any())).thenReturn(CatalogueAnswer.from(new PlayReport(true, "  "), "s")));
        for (var failing : failures) {
            RadioBrowserClient fresh = mock(RadioBrowserClient.class);
            when(fresh.station(eq(ID), any())).thenReturn(
                    CatalogueAnswer.from(Optional.of(station("MP3", false, "https://stream.example.test/live.mp3")), "srv1"));
            failing.accept(fresh);
            RadioStationResolver r = new RadioStationResolver(fresh, new CodecPolicy(emptyProvider()), properties,
                    metrics, Clock.systemUTC());
            ResolvedInputEndpoint endpoint = r.resolve(input(URI_TEXT));
            assertThat(endpoint.effectiveUri()).hasToString("https://stream.example.test/live.mp3");
            verify(fresh, never()).refreshStation(any(), any());
        }
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsOnly("fallback");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SupportedCodecRegistry> emptyProvider() {
        return mock(ObjectProvider.class);
    }

    @Test
    void aCachedStationIsLookedUpAgainBeforeTheFallbackAddressIsUsed() {
        lookupAnswers(station("MP3", false, "https://stale.example.test/old"), null);
        when(client.reportPlay(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of()));
        when(client.refreshStation(eq(ID), any())).thenReturn(CatalogueAnswer.from(
                Optional.of(station("MP3", false, "https://fresh.example.test/new")), "srv2"));
        ResolvedInputEndpoint endpoint = resolver.resolve(input(URI_TEXT));
        assertThat(endpoint.effectiveUri()).hasToString("https://fresh.example.test/new");
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::server).containsExactly("srv2");
    }

    @Test
    void aCachedStationThatIsGoneFailsInvalidOnTheFallback() {
        lookupAnswers(station("MP3", false, "https://stale.example.test/old"), null);
        playAnswers(new PlayReport(false, null), "srv1");
        when(client.refreshStation(eq(ID), any())).thenReturn(CatalogueAnswer.from(Optional.empty(), "srv2"));
        assertThat(failure(URI_TEXT).getFailure()).isEqualTo(InputResolutionFailure.INVALID_INPUT);
    }

    @Test
    void aCachedStationWhoseFreshLookupIsUnavailableFailsUnavailable() {
        lookupAnswers(station("MP3", false, "https://stale.example.test/old"), null);
        playAnswers(new PlayReport(false, null), "srv1");
        when(client.refreshStation(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of()));
        assertThat(failure(URI_TEXT).getFailure()).isEqualTo(InputResolutionFailure.UPSTREAM_UNAVAILABLE);
        // the attribution is the last server that answered: the play report's
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::server).containsExactly("srv1");
    }

    @Test
    void aFreshLookupThatIsUnreadableFailsProtocolError() {
        lookupAnswers(station("MP3", false, "https://stale.example.test/old"), null);
        playAnswers(new PlayReport(false, null), "srv1");
        when(client.refreshStation(eq(ID), any())).thenThrow(new CatalogueProtocolException("bad"));
        assertThat(failure(URI_TEXT).getFailure()).isEqualTo(InputResolutionFailure.UPSTREAM_PROTOCOL_ERROR);
    }

    @Test
    void thePolicyIsAppliedAgainToTheFreshLookupAndNoSecondPlayIsReported() {
        properties.setAllowHls(false);
        lookupAnswers(station("MP3", false, "https://stale.example.test/old"), null);
        playAnswers(new PlayReport(false, null), "srv1");
        when(client.refreshStation(eq(ID), any())).thenReturn(CatalogueAnswer.from(
                Optional.of(station("MP3", true, "https://fresh.example.test/new.m3u8")), "srv2"));
        InputResolutionException e = failure(URI_TEXT);
        assertThat(e.getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::outcome).containsExactly("hls_refused");
        verify(client, times(1)).reportPlay(any(), any());
    }

    @Test
    void aFallbackAddressThatIsNotHttpIsRefused() {
        lookupAnswers(station("MP3", false, "file:///etc/passwd"), "srv1");
        when(client.reportPlay(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of()));
        assertThat(failure(URI_TEXT).getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
        lookupAnswers(station("MP3", false, null), "srv1");
        assertThat(failure(URI_TEXT).getFailure()).isEqualTo(InputResolutionFailure.UNSUPPORTED_RESOURCE);
    }

    // ---- metrics and logs -----------------------------------------------------------------------

    @Test
    void oneResolutionMetricPerResolveAttributedToTheLastServerThatAnswered() {
        lookupAnswers(station("MP3", false, "https://s.example.test/a"), "srvA");
        playAnswers(new PlayReport(true, "https://s.example.test/b"), "srvB");
        resolver.resolve(input(URI_TEXT));
        assertThat(metrics.resolutions).hasSize(1);
        assertThat(metrics.resolutions.get(0).outcome()).isEqualTo("ok");
        assertThat(metrics.resolutions.get(0).server()).isEqualTo("srvB");
        assertThat(metrics.resolutions.get(0).elapsed().isNegative()).isFalse();
    }

    @Test
    void aCachedStationWithNoPlayAnswerIsAttributedToNone() {
        lookupAnswers(station("MP3", false, "https://s.example.test/a"), null);
        when(client.reportPlay(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of()));
        when(client.refreshStation(eq(ID), any())).thenThrow(new CatalogueUnavailableException("down", List.of()));
        failure(URI_TEXT);
        assertThat(metrics.resolutions).extracting(RecordingMetrics.Resolution::server).containsExactly("none");
    }

    @Test
    void theInfoLineHasTheUuidOutcomeAndServerButNeverTheNameOrLabel() {
        healthy();
        resolver.resolve(input(URI_TEXT));
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.INFO).hasSize(1).allSatisfy(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).contains(ID.toString()).contains("ok").contains("srv1");
            assertThat(message).doesNotContain("Test FM").doesNotContain("label-not-to-be-logged");
        });
    }
}
