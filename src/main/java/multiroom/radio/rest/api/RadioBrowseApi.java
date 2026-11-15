package multiroom.radio.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.dto.RadioTagPage;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Browse the catalogue by country, language and tag. */
@Tag(name = "Radio", description = "Search and browse radio stations")
@RequestMapping("/api/radio")
public interface RadioBrowseApi {

    /** Every country with stations. */
    @Operation(operationId = "listRadioCountries", summary = "Countries with their station counts",
            description = "The whole list, largest first. Remembered by the hub for 6 hours. Counts exclude broken "
                    + "stations and are close to, not equal to, what a search returns. Accepts no parameters.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Every country that has stations",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = RadioCountry.class)))),
                    @ApiResponse(responseCode = "400", description = "A parameter was sent (invalid-parameter)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "502", description = "The catalogue answered with something unreadable "
                            + "(catalogue-bad-answer)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "503", description = "No catalogue server answered in time "
                            + "(catalogue-unavailable)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "default", description = "405, 404 for another path, or 500",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class)))})
    @GetMapping("/countries")
    List<RadioCountry> listRadioCountries(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParameters);

    /** Every language with stations. */
    @Operation(operationId = "listRadioLanguages", summary = "Languages with their station counts",
            description = "The whole list, largest first. Remembered by the hub for 6 hours. Accepts no parameters.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Every language that has stations",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = RadioLanguage.class)))),
                    @ApiResponse(responseCode = "400", description = "A parameter was sent (invalid-parameter)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "502", description = "The catalogue answered with something unreadable "
                            + "(catalogue-bad-answer)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "503", description = "No catalogue server answered in time "
                            + "(catalogue-unavailable)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "default", description = "405, 404 for another path, or 500",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class)))})
    @GetMapping("/languages")
    List<RadioLanguage> listRadioLanguages(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParameters);

    /** One page of tags, optionally filtered by name. */
    @Operation(operationId = "listRadioTags", summary = "Tags with their station counts",
            description = "Largest first. The full list has more than ten thousand entries, so it is paged; filter it "
                    + "with `name`. Each page is remembered by the hub for 6 hours.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of tags",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RadioTagPage.class))),
                    @ApiResponse(responseCode = "400", description = "A malformed or out-of-range value, an unknown or "
                            + "repeated parameter (invalid-parameter)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "502", description = "The catalogue answered with something unreadable "
                            + "(catalogue-bad-answer)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "503", description = "No catalogue server answered in time "
                            + "(catalogue-unavailable)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "default", description = "405, 404 for another path, or 500",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class)))})
    @GetMapping("/tags")
    RadioTagPage listRadioTags(
            @Parameter(description = "Part of the tag name, any letter case. Blank counts as not given. `%` and `/` are refused.",
                    schema = @Schema(type = "string", maxLength = 100))
            @RequestParam(name = "name", required = false) String name,
            @Parameter(description = "Page size. A value outside the range is refused, never reduced.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(description = "Where the page starts. Send the previous answer's `nextOffset`.",
                    schema = @Schema(type = "integer", minimum = "0", maximum = "10000", defaultValue = "0"))
            @RequestParam(name = "offset", required = false) Integer offset,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParameters);
}
