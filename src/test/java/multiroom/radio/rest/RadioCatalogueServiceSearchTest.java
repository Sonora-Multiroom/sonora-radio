package multiroom.radio.rest;

import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.SortDirection;
import multiroom.radio.catalogue.Station;
import multiroom.radio.catalogue.StationOrder;
import multiroom.radio.catalogue.StationPage;
import multiroom.radio.catalogue.StationQuery;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.rest.dto.RadioStationPage;
import multiroom.radio.support.MutableClock;
import multiroom.radio.support.StationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadioCatalogueServiceSearchTest {

    private RadioBrowserClient client;
    private MutableClock clock;
    private RadioProperties properties;
    private RadioCatalogueService service;

    @BeforeEach
    void setUp() {
        client = mock(RadioBrowserClient.class);
        clock = new MutableClock();
        properties = new RadioProperties();
        service = new RadioCatalogueService(client, new RadioStationMapper(), properties, clock);
    }

    private static StationQuery query(int limit, int offset) {
        return new StationQuery(null, null, null, null, null, null, StationOrder.VOTES, SortDirection.DESC, limit, offset);
    }

    private static List<Station> stations(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new StationBuilder().uuid(UUID.randomUUID()).name("S" + i).build())
                .collect(Collectors.toList());
    }

    private void catalogueAnswers(int kept, int received) {
        when(client.search(any(), any())).thenReturn(CatalogueAnswer.from(new StationPage(stations(kept), received), "srv"));
    }

    @Test
    void aFullPageHasTheNextOffset() {
        catalogueAnswers(20, 20);
        RadioStationPage page = service.search(query(20, 40));
        assertThat(page.stations()).hasSize(20);
        assertThat(page.nextOffset()).isEqualTo(60);
    }

    @Test
    void aShortPageHasNoNextOffset() {
        catalogueAnswers(7, 7);
        assertThat(service.search(query(20, 0)).nextOffset()).isNull();
    }

    @Test
    void anEmptyPageHasNoNextOffset() {
        catalogueAnswers(0, 0);
        RadioStationPage page = service.search(query(20, 0));
        assertThat(page.stations()).isEmpty();
        assertThat(page.nextOffset()).isNull();
    }

    @Test
    void aFullPageWhoseNextOffsetWouldPassTenThousandHasNone() {
        catalogueAnswers(20, 20);
        assertThat(service.search(query(20, 9990)).nextOffset()).isNull();
        catalogueAnswers(20, 20);
        assertThat(service.search(query(20, 9980)).nextOffset()).isEqualTo(10000);
    }

    @Test
    void aDroppedRecordDoesNotShortenThePageSoPagingGoesOn() {
        catalogueAnswers(19, 20);
        RadioStationPage page = service.search(query(20, 0));
        assertThat(page.stations()).hasSize(19);
        assertThat(page.nextOffset()).isEqualTo(20);
    }

    @Test
    void theDeadlineHandedToTheClientIsTheRequestTimeoutFromTheInjectedClock() {
        properties.setRequestTimeout(Duration.ofSeconds(3));
        catalogueAnswers(0, 0);
        service.search(query(20, 0));
        ArgumentCaptor<Deadline> captured = ArgumentCaptor.forClass(Deadline.class);
        verify(client).search(any(), captured.capture());
        assertThat(captured.getValue().end()).isEqualTo(clock.instant().plus(Duration.ofSeconds(3)));
        assertThat(captured.getValue().clock()).isSameAs(clock);
    }

    @Test
    void stationsAreMappedToTheApiShape() {
        catalogueAnswers(1, 1);
        assertThat(service.search(query(20, 0)).stations().get(0).uri()).startsWith("radiobrowser://");
    }
}
