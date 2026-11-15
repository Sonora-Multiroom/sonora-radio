package multiroom.radio.rest;

import multiroom.radio.catalogue.CatalogueAnswer;
import multiroom.radio.catalogue.Country;
import multiroom.radio.catalogue.Deadline;
import multiroom.radio.catalogue.Language;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.TagCount;
import multiroom.radio.catalogue.TagPage;
import multiroom.radio.catalogue.TagQuery;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.rest.dto.RadioTagPage;
import multiroom.radio.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadioCatalogueServiceBrowseTest {

    private RadioBrowserClient client;
    private RadioCatalogueService service;

    @BeforeEach
    void setUp() {
        client = mock(RadioBrowserClient.class);
        service = new RadioCatalogueService(client, new RadioStationMapper(), new RadioProperties(), new MutableClock());
    }

    @Test
    void countriesAndLanguagesAreMappedAndKeepTheCataloguesOrder() {
        when(client.countries(any())).thenReturn(CatalogueAnswer.from(
                List.of(new Country("us", "USA", 9), new Country("UA", "Ukraine", 3), new Country("zz9", "Bad", 1)), "srv"));
        when(client.languages(any())).thenReturn(CatalogueAnswer.from(
                List.of(new Language("english", "en", 9), new Language("ukrainian", "uk", 3)), "srv"));
        assertThat(service.countries()).extracting(c -> c.code()).containsExactly("US", "UA");
        assertThat(service.languages()).extracting(l -> l.name()).containsExactly("english", "ukrainian");
    }

    @Test
    void listCallsGetTheRequestBudgetWithHalfAsTheAttemptLimit() {
        when(client.countries(any())).thenReturn(CatalogueAnswer.from(List.of(), "srv"));
        service.countries();
        ArgumentCaptor<Deadline> deadline = ArgumentCaptor.forClass(Deadline.class);
        verify(client).countries(deadline.capture());
        assertThat(deadline.getValue().attemptLimit()).isEqualTo(new RadioProperties().getRequestTimeout().dividedBy(2));
    }

    private void tagPage(int kept, int received) {
        List<TagCount> tags = IntStream.range(0, kept).mapToObj(i -> new TagCount("t" + i, 1)).collect(Collectors.toList());
        when(client.tags(any(), any())).thenReturn(CatalogueAnswer.from(new TagPage(tags, received), "srv"));
    }

    @Test
    void theTagPageNextOffsetFollowsWhatWasReceived() {
        tagPage(20, 20);
        RadioTagPage full = service.tags(new TagQuery(null, 20, 40));
        assertThat(full.nextOffset()).isEqualTo(60);
        tagPage(19, 20);
        assertThat(service.tags(new TagQuery(null, 20, 0)).nextOffset()).isEqualTo(20);
        tagPage(5, 5);
        assertThat(service.tags(new TagQuery(null, 20, 0)).nextOffset()).isNull();
        tagPage(20, 20);
        assertThat(service.tags(new TagQuery(null, 20, 9990)).nextOffset()).isNull();
    }
}
