package multiroom.radio.rest.controller;

import multiroom.radio.catalogue.TagQuery;
import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.advice.RadioExceptionHandler;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioTag;
import multiroom.radio.rest.dto.RadioTagPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({RadioBrowseController.class, RadioExceptionHandler.class})
class RadioBrowseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RadioCatalogueService service;

    @Test
    void countriesIsAPlainJsonArray() throws Exception {
        when(service.countries()).thenReturn(List.of(new RadioCountry("UA", "Ukraine", 312)));
        mvc.perform(get("/api/radio/countries"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].code").value("UA"))
                .andExpect(jsonPath("$[0].name").value("Ukraine"))
                .andExpect(jsonPath("$[0].stationCount").value(312));
    }

    @Test
    void languagesIsAPlainJsonArrayWithNullWhereTheCodeIsUnknown() throws Exception {
        when(service.languages()).thenReturn(List.of(new RadioLanguage("english", "en", 5),
                new RadioLanguage("american english", null, 1)));
        mvc.perform(get("/api/radio/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("en"))
                .andExpect(jsonPath("$[1].code").value(org.hamcrest.Matchers.nullValue()));
        String body = mvc.perform(get("/api/radio/languages")).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"code\":null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/radio/countries?limit=5", "/api/radio/languages?x=1"})
    void theTwoListsAcceptNoParameters(String url) throws Exception {
        String parameter = url.substring(url.indexOf('?') + 1, url.indexOf('='));
        mvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.parameter").value(parameter));
        verifyNoInteractions(service);
    }

    @Test
    void tagsReturnsAPageWithNextOffset() throws Exception {
        when(service.tags(any())).thenReturn(new RadioTagPage(List.of(new RadioTag("jazz", 1062)), 20));
        mvc.perform(get("/api/radio/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("jazz"))
                .andExpect(jsonPath("$.tags[0].stationCount").value(1062))
                .andExpect(jsonPath("$.nextOffset").value(20));
    }

    @Test
    void tagsPassesNameLimitAndOffsetAsInSearch() throws Exception {
        when(service.tags(any())).thenReturn(new RadioTagPage(List.of(), null));
        mvc.perform(get("/api/radio/tags?name=Jazz&limit=50&offset=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextOffset").value(org.hamcrest.Matchers.nullValue()));
        ArgumentCaptor<TagQuery> captured = ArgumentCaptor.forClass(TagQuery.class);
        verify(service).tags(captured.capture());
        assertThat(captured.getValue()).isEqualTo(new TagQuery("jazz", 50, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=101", "offset=-1", "offset=10001", "name=a/b", "name=50%25", "q=1",
            "limit=abc"})
    void invalidTagParametersAnswer400WithNoServiceCall(String query) throws Exception {
        String parameter = query.substring(0, query.indexOf('='));
        mvc.perform(get("/api/radio/tags?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value(parameter));
        verifyNoInteractions(service);
    }
}
