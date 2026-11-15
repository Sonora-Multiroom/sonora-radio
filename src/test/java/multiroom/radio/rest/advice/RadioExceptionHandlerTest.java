package multiroom.radio.rest.advice;

import multiroom.radio.catalogue.CatalogueProtocolException;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.controller.RadioStationsController;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.query.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({RadioStationsController.class, RadioExceptionHandler.class, RadioExceptionHandlerTest.OtherExtensionController.class})
class RadioExceptionHandlerTest {

    private static final String PREFIX = "urn:multiroom:radio:error:";

    /** Another extension's controller: this advice must leave its errors alone. */
    @RestController
    static class OtherExtensionController {
        @GetMapping("/api/other/boom")
        String boom() {
            throw new InvalidParameterException("x", "belongs to someone else");
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RadioCatalogueService service;

    @Test
    void anInvalidParameterIs400ProblemJsonNamingIt() throws Exception {
        mvc.perform(get("/api/radio/stations?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PREFIX + "invalid-parameter"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("limit")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/radio/stations"))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z")))
                .andExpect(jsonPath("$.parameter").value("limit"));
    }

    @Test
    void aValueOfTheWrongTypeIs400NamingTheParameter() throws Exception {
        mvc.perform(get("/api/radio/stations?limit=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PREFIX + "invalid-parameter"))
                .andExpect(jsonPath("$.parameter").value("limit"));
    }

    @Test
    void aMissingRequiredParameterIs400NamingIt() {
        MissingServletRequestParameterException e = new MissingServletRequestParameterException("q", "String");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/radio/stations");
        ResponseEntity<RadioProblem> answer = new RadioExceptionHandler().missingParameter(e, request);
        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getBody().type()).isEqualTo(PREFIX + "invalid-parameter");
        assertThat(answer.getBody().parameter()).isEqualTo("q");
        assertThat(answer.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
    }

    @Test
    void aCatalogueProtocolErrorIs502() throws Exception {
        when(service.search(any())).thenThrow(new CatalogueProtocolException("unreadable"));
        mvc.perform(get("/api/radio/stations"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PREFIX + "catalogue-bad-answer"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.parameter").doesNotExist());
    }

    @Test
    void anUnavailableCatalogueIs503WithRetryAfterThirty() throws Exception {
        when(service.search(any())).thenThrow(new CatalogueUnavailableException("down", java.util.List.of("srv")));
        mvc.perform(get("/api/radio/stations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PREFIX + "catalogue-unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void anyOtherExceptionIs500WithAFixedDetailAndNoExceptionText() throws Exception {
        when(service.search(any())).thenThrow(new IllegalStateException("secret internal detail"));
        MvcResult result = mvc.perform(get("/api/radio/stations"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PREFIX + "internal-error"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret internal detail");
    }

    @Test
    void aLongClientValueIsNeverEchoedAndALongUnknownNameIsCutToThirtyTwo() throws Exception {
        String longName = "p".repeat(100);
        MvcResult result = mvc.perform(get("/api/radio/stations?" + longName + "=1"))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("p".repeat(32)).doesNotContain("p".repeat(33));

        MvcResult longValue = mvc.perform(get("/api/radio/stations?name=" + "v".repeat(500)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(longValue.getResponse().getContentAsString()).doesNotContain("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
    }

    @Test
    void aHandlerOutsideTheRadioApiIsNotTouchedByTheAdvice() {
        assertThatThrownBy(() -> mvc.perform(get("/api/other/boom")))
                .hasRootCauseInstanceOf(InvalidParameterException.class);
    }
}
