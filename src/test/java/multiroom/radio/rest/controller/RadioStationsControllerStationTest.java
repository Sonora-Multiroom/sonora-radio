package multiroom.radio.rest.controller;

import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.StationNotFoundException;
import multiroom.radio.rest.advice.RadioExceptionHandler;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.query.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({RadioStationsController.class, RadioExceptionHandler.class})
class RadioStationsControllerStationTest {

    private static final String ID = "9617a958-0601-11e8-ae97-52543be04c81";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RadioCatalogueService service;

    private static RadioStation station() {
        return new RadioStation(ID, "radiobrowser://" + ID, "Test FM", null, null, "UA", List.of(), List.of("jazz"),
                "MP3", 128, false, true, 1, 2);
    }

    @Test
    void aKnownStationAnswers200WithTheSameShapeAsSearch() throws Exception {
        when(service.station(ID)).thenReturn(station());
        mvc.perform(get("/api/radio/stations/" + ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.uuid").value(ID))
                .andExpect(jsonPath("$.uri").value("radiobrowser://" + ID))
                .andExpect(jsonPath("$.favicon").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.bitrate").value(128))
                .andExpect(jsonPath("$.lastCheckOk").value(true));
    }

    @Test
    void anUpperCaseUuidAnswersTheLowerCaseUuidAndUri() throws Exception {
        when(service.station(ID.toUpperCase())).thenReturn(station());
        mvc.perform(get("/api/radio/stations/" + ID.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(ID))
                .andExpect(jsonPath("$.uri").value("radiobrowser://" + ID));
    }

    @Test
    void aMalformedUuidAnswers400NamingUuid() throws Exception {
        when(service.station(anyString())).thenThrow(new InvalidParameterException("uuid", "must be a UUID"));
        mvc.perform(get("/api/radio/stations/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.parameter").value("uuid"));
        mvc.perform(get("/api/radio/stations/" + ID + "%20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("uuid"));
    }

    @Test
    void anUnknownUuidAnswers404NamingIt() throws Exception {
        when(service.station(anyString())).thenThrow(new StationNotFoundException(UUID.fromString(ID)));
        mvc.perform(get("/api/radio/stations/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:multiroom:radio:error:station-not-found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(ID)));
    }

    @Test
    void anyQueryParameterAnswers400AndTheServiceIsNeverCalled() throws Exception {
        mvc.perform(get("/api/radio/stations/" + ID + "?x=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("x"));
        verify(service, never()).station(any());
    }
}
