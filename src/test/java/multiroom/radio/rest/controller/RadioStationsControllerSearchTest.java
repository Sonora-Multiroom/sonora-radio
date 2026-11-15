package multiroom.radio.rest.controller;

import multiroom.radio.catalogue.SortDirection;
import multiroom.radio.catalogue.StationOrder;
import multiroom.radio.catalogue.StationQuery;
import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioStationPage;
import multiroom.radio.rest.query.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({RadioStationsController.class, RadioStationsControllerSearchTest.StubAdvice.class})
class RadioStationsControllerSearchTest {

    /** Stands in for the real advice, which has its own test: it only has to name the parameter. */
    @RestControllerAdvice
    static class StubAdvice {
        @ExceptionHandler(InvalidParameterException.class)
        ResponseEntity<String> invalid(InvalidParameterException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("parameter=" + e.parameter());
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        ResponseEntity<String> mismatch(MethodArgumentTypeMismatchException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("parameter=" + e.getName());
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RadioCatalogueService service;

    private static RadioStation station(String uuid) {
        return new RadioStation(uuid, "radiobrowser://" + uuid, "Test FM", null, null, "UA", List.of(), List.of("jazz"),
                "MP3", null, false, true, 1, 2);
    }

    @Test
    void noParametersAnswer200JsonWithStationsAndNextOffsetNullWrittenAsNull() throws Exception {
        when(service.search(any())).thenReturn(
                new RadioStationPage(List.of(station("9617a958-0601-11e8-ae97-52543be04c81")), null));
        mvc.perform(get("/api/radio/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.stations.length()").value(1))
                .andExpect(jsonPath("$.nextOffset").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stations[0].favicon").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stations[0].uri").value("radiobrowser://9617a958-0601-11e8-ae97-52543be04c81"));
        // the key is present, not omitted
        String body = mvc.perform(get("/api/radio/stations")).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"nextOffset\":null").contains("\"favicon\":null").contains("\"bitrate\":null")
                .contains("\"homepage\":null");
    }

    @Test
    void theParsedQueryReachesTheService() throws Exception {
        when(service.search(any())).thenReturn(new RadioStationPage(List.of(), null));
        mvc.perform(get("/api/radio/stations")
                        .param("name", " kiss ").param("tag", "Jazz").param("countryCode", "ua")
                        .param("order", "clickCount").param("direction", "asc")
                        .param("limit", "10").param("offset", "30").param("bitrateMin", "64"))
                .andExpect(status().isOk());
        ArgumentCaptor<StationQuery> captured = ArgumentCaptor.forClass(StationQuery.class);
        verify(service).search(captured.capture());
        StationQuery q = captured.getValue();
        assertThat(q.name()).isEqualTo("kiss");
        assertThat(q.tag()).isEqualTo("jazz");
        assertThat(q.countryCode()).isEqualTo("UA");
        assertThat(q.order()).isEqualTo(StationOrder.CLICK_COUNT);
        assertThat(q.direction()).isEqualTo(SortDirection.ASC);
        assertThat(q.limit()).isEqualTo(10);
        assertThat(q.offset()).isEqualTo(30);
        assertThat(q.bitrateMin()).isEqualTo(64);
    }

    @Test
    void withNoParametersTheDefaultsApply() throws Exception {
        when(service.search(any())).thenReturn(new RadioStationPage(List.of(), null));
        mvc.perform(get("/api/radio/stations")).andExpect(status().isOk());
        ArgumentCaptor<StationQuery> captured = ArgumentCaptor.forClass(StationQuery.class);
        verify(service).search(captured.capture());
        assertThat(captured.getValue().limit()).isEqualTo(20);
        assertThat(captured.getValue().order()).isEqualTo(StationOrder.VOTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=101", "offset=-1", "offset=10001", "bitrateMin=-1", "order=Votes",
            "direction=up", "countryCode=UAH", "name=50%25", "tag=a%00b"})
    void anInvalidValueAnswers400NamingTheParameterAndNeverCallsTheService(String query) throws Exception {
        String parameter = query.substring(0, query.indexOf('='));
        mvc.perform(get("/api/radio/stations?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("parameter=" + parameter));
        verifyNoInteractions(service);
    }

    @Test
    void anUnknownParameterAnswers400NamingItAndNeverCallsTheService() throws Exception {
        mvc.perform(get("/api/radio/stations?countrycode=UA"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("parameter=countrycode"));
        mvc.perform(get("/api/radio/stations?q=jazz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("parameter=q"));
        verify(service, never()).search(any());
    }

    @Test
    void aRepeatedParameterAnswers400NamingItAndNeverCallsTheService() throws Exception {
        mvc.perform(get("/api/radio/stations?limit=5&limit=6"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("parameter=limit"));
        verify(service, never()).search(any());
    }

    @Test
    void aValueThatIsNotANumberAnswers400NamingTheParameter() throws Exception {
        mvc.perform(get("/api/radio/stations?limit=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("parameter=limit"));
        verifyNoInteractions(service);
    }
}
