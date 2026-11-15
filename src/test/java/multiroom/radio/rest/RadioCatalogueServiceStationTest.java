package multiroom.radio.rest;

import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.query.InvalidParameterException;
import multiroom.radio.support.MutableClock;
import multiroom.radio.support.StationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RadioCatalogueServiceStationTest {

    private static final UUID ID = UUID.fromString("9617a958-0601-11e8-ae97-52543be04c81");

    private RadioBrowserClient client;
    private RadioCatalogueService service;

    @BeforeEach
    void setUp() {
        client = mock(RadioBrowserClient.class);
        service = new RadioCatalogueService(client, new RadioStationMapper(), new RadioProperties(), new MutableClock());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-uuid", "9617a958-0601-11e8-ae97-52543be04c8", " 9617a958-0601-11e8-ae97-52543be04c81",
            "9617a958-0601-11e8-ae97-52543be04c81 ", "{9617a958-0601-11e8-ae97-52543be04c81}"})
    void aMalformedUuidIsRefusedBeforeTheClientIsTouched(String text) {
        assertThatThrownBy(() -> service.station(text))
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> assertThat(e.parameter()).isEqualTo("uuid"));
        verifyNoInteractions(client);
    }

    @Test
    void upperAndMixedCaseAreAcceptedAndTheLowerCaseUuidIsAskedFor() {
        when(client.station(any(), any())).thenReturn(
                CatalogueAnswer.from(Optional.of(new StationBuilder().uuid(ID).build()), "srv"));
        RadioStation station = service.station("9617A958-0601-11E8-AE97-52543BE04C81");
        assertThat(station.uuid()).isEqualTo(ID.toString());
        assertThat(station.uri()).isEqualTo("radiobrowser://" + ID);
        ArgumentCaptor<UUID> asked = ArgumentCaptor.forClass(UUID.class);
        verify(client).station(asked.capture(), any(Deadline.class));
        assertThat(asked.getValue()).isEqualTo(ID);
    }

    @Test
    void aStationTheCatalogueMarksBrokenIsStillReturned() {
        when(client.station(any(), any())).thenReturn(
                CatalogueAnswer.from(Optional.of(new StationBuilder().uuid(ID).lastCheckOk(false).build()), "srv"));
        assertThat(service.station(ID.toString()).lastCheckOk()).isFalse();
    }

    @Test
    void anEmptyAnswerRaisesStationNotFoundNamingTheUuid() {
        when(client.station(any(), any())).thenReturn(CatalogueAnswer.from(Optional.empty(), "srv"));
        assertThatThrownBy(() -> service.station(ID.toString().toUpperCase()))
                .isInstanceOfSatisfying(StationNotFoundException.class, e -> assertThat(e.uuid()).isEqualTo(ID));
    }

    @Test
    void itUsesTheCachedLookupAndNeverRefreshesOrReportsAPlay() {
        when(client.station(any(), any())).thenReturn(
                CatalogueAnswer.cached(Optional.of(new StationBuilder().uuid(ID).build())));
        service.station(ID.toString());
        verify(client, never()).refreshStation(any(), any());
        verify(client, never()).reportPlay(any(), any());
    }

    @Test
    void theLookupGetsTheRequestBudgetWithAnAttemptLimitOfHalf() {
        when(client.station(any(), any())).thenReturn(CatalogueAnswer.from(Optional.empty(), "srv"));
        assertThatThrownBy(() -> service.station(ID.toString())).isInstanceOf(StationNotFoundException.class);
        ArgumentCaptor<Deadline> deadline = ArgumentCaptor.forClass(Deadline.class);
        verify(client).station(any(), deadline.capture());
        assertThat(deadline.getValue().attemptLimit()).isEqualTo(new RadioProperties().getRequestTimeout().dividedBy(2));
    }
}
