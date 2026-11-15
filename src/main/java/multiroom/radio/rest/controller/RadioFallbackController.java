package multiroom.radio.rest.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.advice.RadioExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catches what the real mappings do not: a wrong method on a known path (405 with {@code Allow: GET})
 * and an unknown path under {@code /api/radio/} (404), both as problem details.
 *
 * <p>Needed because a scoped {@code @RestControllerAdvice} is only consulted when a handler was
 * chosen; with none, Spring would answer with its own default body. Spring prefers the more specific
 * pattern, so this never shadows a real mapping. Hidden from the API description.
 */
@Hidden
@RestController
public class RadioFallbackController {

    private static final Set<String> KNOWN = Set.of("/api/radio/stations", "/api/radio/countries",
            "/api/radio/languages", "/api/radio/tags");
    private static final Pattern ONE_STATION = Pattern.compile("/api/radio/stations/[^/]+");

    /** Answers every request that matched no real mapping. */
    @RequestMapping("/api/radio/**")
    public ResponseEntity<RadioProblem> unmatched(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean known = KNOWN.contains(path) || ONE_STATION.matcher(path).matches();
        if (known && !"GET".equals(request.getMethod())) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ALLOW, "GET");
            return answer(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
                    "This path answers GET only", request, headers);
        }
        return answer(HttpStatus.NOT_FOUND, "not-found", "Not found",
                "There is nothing at this path under /api/radio/", request, new HttpHeaders());
    }

    private static ResponseEntity<RadioProblem> answer(HttpStatus status, String type, String title, String detail,
                                                      HttpServletRequest request, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(RadioExceptionHandler.problem(status, type, title, detail, request, null),
                headers, status);
    }
}
