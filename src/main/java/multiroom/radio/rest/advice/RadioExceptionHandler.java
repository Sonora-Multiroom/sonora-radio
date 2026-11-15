package multiroom.radio.rest.advice;

import jakarta.servlet.http.HttpServletRequest;
import multiroom.radio.catalogue.CatalogueProtocolException;
import multiroom.radio.catalogue.CatalogueUnavailableException;
import multiroom.radio.rest.StationNotFoundException;
import multiroom.radio.rest.api.RadioBrowseApi;
import multiroom.radio.rest.api.RadioStationsApi;
import multiroom.radio.rest.controller.RadioFallbackController;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.query.InvalidParameterException;
import multiroom.radio.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/**
 * Shapes every error of this extension's API as {@code application/problem+json}. Scoped to this
 * module's API interfaces and its fallback with {@code assignableTypes}: without that it would take
 * over other extensions' error responses.
 *
 * <p>A detail never contains catalogue text or a client value longer than 32 characters; a 500's
 * detail is a fixed sentence and the exception goes to the log.
 */
@RestControllerAdvice(assignableTypes = {RadioStationsApi.class, RadioBrowseApi.class, RadioFallbackController.class})
public class RadioExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RadioExceptionHandler.class);
    private static final String PREFIX = "urn:multiroom:radio:error:";
    private static final int PATH_LIMIT = 200;

    /** A refused parameter: 400, naming it. */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<RadioProblem> invalid(InvalidParameterException e, HttpServletRequest request) {
        return invalidParameter(e.parameter(), e.parameter() + " " + e.reason(), request);
    }

    /** A value Spring could not convert (for example {@code limit=abc}): 400, naming the parameter. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RadioProblem> mismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return invalidParameter(e.getName(), e.getName() + " must be a whole number", request);
    }

    /** A required parameter that is missing: 400, naming it. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RadioProblem> missingParameter(MissingServletRequestParameterException e,
                                                         HttpServletRequest request) {
        return invalidParameter(e.getParameterName(), e.getParameterName() + " is required", request);
    }

    /** A well-formed UUID the catalogue does not know: 404, naming it. */
    @ExceptionHandler(StationNotFoundException.class)
    public ResponseEntity<RadioProblem> notFound(StationNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "station-not-found", "Station not found",
                "The catalogue has no station with UUID " + e.uuid(), request, null, new HttpHeaders());
    }

    /** The catalogue answered with something unreadable or refused the request: 502. */
    @ExceptionHandler(CatalogueProtocolException.class)
    public ResponseEntity<RadioProblem> badAnswer(CatalogueProtocolException e, HttpServletRequest request) {
        LOG.warn("The radio catalogue gave an unusable answer to {}", path(request));
        return respond(HttpStatus.BAD_GATEWAY, "catalogue-bad-answer", "Catalogue gave a bad answer",
                "The radio catalogue answered with something this hub cannot use", request, null, new HttpHeaders());
    }

    /** No catalogue server answered in time: 503 with {@code Retry-After}. */
    @ExceptionHandler(CatalogueUnavailableException.class)
    public ResponseEntity<RadioProblem> unavailable(CatalogueUnavailableException e, HttpServletRequest request) {
        LOG.warn("The radio catalogue is unavailable for {} (servers tried: {})", path(request), e.serversTried());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "catalogue-unavailable", "Catalogue unavailable",
                "No radio catalogue server answered in time; try again shortly", request, null, headers);
    }

    /** Anything unexpected: 500 with a fixed detail. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RadioProblem> internal(Exception e, HttpServletRequest request) {
        LOG.error("Unexpected failure answering {}", path(request), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "The request could not be completed", request, null, new HttpHeaders());
    }

    private ResponseEntity<RadioProblem> invalidParameter(String parameter, String detail, HttpServletRequest request) {
        String safeName = Text.bounded(parameter, 32);
        return respond(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid parameter",
                Text.bounded(detail, 200), request, safeName, new HttpHeaders());
    }

    private static ResponseEntity<RadioProblem> respond(HttpStatus status, String type, String title, String detail,
                                                       HttpServletRequest request, String parameter,
                                                       HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem(status, type, title, detail, request, parameter), headers, status);
    }

    /** Builds a problem for this extension's error {@code type}; shared with the fallback controller. */
    public static RadioProblem problem(HttpStatus status, String type, String title, String detail,
                                       HttpServletRequest request, String parameter) {
        return new RadioProblem(PREFIX + type, title, detail, status.value(), path(request),
                Instant.now().toString(), parameter);
    }

    private static String path(HttpServletRequest request) {
        return Text.bounded(request.getRequestURI(), PATH_LIMIT);
    }
}
