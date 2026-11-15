package multiroom.radio.rest.controller;

import io.swagger.v3.oas.annotations.Hidden;
import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.advice.RadioExceptionHandler;
import multiroom.radio.rest.dto.RadioStationPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({RadioStationsController.class, RadioFallbackController.class, RadioExceptionHandler.class})
class RadioFallbackControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RadioCatalogueService service;

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void aMethodOtherThanGetOnAKnownPathIs405WithAllowGet(String method) throws Exception {
        mvc.perform(MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), "/api/radio/stations"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:multiroom:radio:error:method-not-allowed"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.instance").value("/api/radio/stations"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/radio/countries", "/api/radio/languages", "/api/radio/tags",
            "/api/radio/stations/9617a958-0601-11e8-ae97-52543be04c81"})
    void everyKnownPathAnswers405ForPost(String path) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(path))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/radio/nothing", "/api/radio/stations/x/y", "/api/radio/", "/api/radio"})
    void anUnknownPathIs404NotFound(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:multiroom:radio:error:not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void anUnknownPathWithAnyMethodIs404() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/radio/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:multiroom:radio:error:not-found"));
    }

    @Test
    void theFallbackDoesNotShadowTheRealMapping() throws Exception {
        when(service.search(any())).thenReturn(new RadioStationPage(List.of(), null));
        mvc.perform(get("/api/radio/stations")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stations").isArray());
    }

    @Test
    void theFallbackIsAbsentFromTheGeneratedApiDescription() {
        assertThat(RadioFallbackController.class.isAnnotationPresent(Hidden.class)).isTrue();
    }
}
