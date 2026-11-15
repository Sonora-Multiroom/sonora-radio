package multiroom.radio.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioStationPage;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Search for radio stations. Read-only requests served only while the radio extension is installed and
 * enabled; otherwise every path answers 404, which clients read as "radio is not available". To play a
 * station, pass its {@code uri} to {@code POST /api/v2/play}.
 */
@Tag(name = "Radio", description = "Search and browse radio stations")
@RequestMapping("/api/radio")
public interface RadioStationsApi {

    /** Searches stations; every parameter is optional. */
    @Operation(operationId = "searchRadioStations", summary = "Search stations",
            description = "Returns stations matching every filter given, never ones the catalogue marks as broken. "
                    + "With no parameters: the 20 most-voted stations. The order among stations with equal values is "
                    + "not stable, so a client that pages drops stations whose `uuid` it has already received. "
                    + "A parameter this request does not define, or one sent twice, answers 400.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of stations",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RadioStationPage.class))),
                    @ApiResponse(responseCode = "400", description = "A malformed or out-of-range value, an unknown "
                            + "or repeated parameter (invalid-parameter); `parameter` names it. The catalogue was "
                            + "not contacted.",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "502", description = "The catalogue answered with something "
                            + "unreadable or refused the request (catalogue-bad-answer)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "503", description = "No catalogue server answered within the time "
                            + "budget (catalogue-unavailable); `Retry-After` says when to try again",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "default", description = "405 for a method other than GET "
                            + "(method-not-allowed), 404 for an unknown path under /api/radio/ (not-found), 500 for an "
                            + "unexpected failure (internal-error)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class)))})
    @GetMapping("/stations")
    RadioStationPage searchRadioStations(
            @Parameter(description = "Part of the station name, any letter case. Blank counts as not given. `%` is refused.",
                    schema = @Schema(type = "string", maxLength = 100))
            @RequestParam(name = "name", required = false) String name,
            @Parameter(description = "One whole tag, any letter case (\"jazz\" does not match \"acid jazz\"). "
                    + "Find tags through `/api/radio/tags`.", schema = @Schema(type = "string", maxLength = 100))
            @RequestParam(name = "tag", required = false) String tag,
            @Parameter(description = "ISO 3166-1 alpha-2, either case.",
                    schema = @Schema(type = "string", pattern = "^[A-Za-z]{2}$"))
            @RequestParam(name = "countryCode", required = false) String countryCode,
            @Parameter(description = "One whole language name as `/api/radio/languages` gives it, any letter case.",
                    schema = @Schema(type = "string", maxLength = 100))
            @RequestParam(name = "language", required = false) String language,
            @Parameter(description = "The catalogue's codec label, whole, any letter case (for example MP3, AAC+).",
                    schema = @Schema(type = "string", maxLength = 32))
            @RequestParam(name = "codec", required = false) String codec,
            @Parameter(description = "Lowest bitrate in kbit/s. Stations of unknown bitrate are excluded when it is above 0.",
                    schema = @Schema(type = "integer", minimum = "0", maximum = "1000000"))
            @RequestParam(name = "bitrateMin", required = false) Integer bitrateMin,
            @Parameter(description = "What to sort by.",
                    schema = @Schema(type = "string", allowableValues = {"name", "votes", "clickCount", "bitrate"},
                            defaultValue = "votes"))
            @RequestParam(name = "order", required = false) String order,
            @Parameter(description = "Defaults to `desc` for `votes` and `clickCount`, `asc` for `name` and `bitrate`.",
                    schema = @Schema(type = "string", allowableValues = {"asc", "desc"}))
            @RequestParam(name = "direction", required = false) String direction,
            @Parameter(description = "Page size. A value outside the range is refused, never reduced.",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(description = "Where the page starts. Send the previous answer's `nextOffset`.",
                    schema = @Schema(type = "integer", minimum = "0", maximum = "10000", defaultValue = "0"))
            @RequestParam(name = "offset", required = false) Integer offset,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParameters);

    /** One station by its UUID. */
    @Operation(operationId = "getRadioStation", summary = "One station by its UUID",
            description = "Returned whether or not the catalogue marks it as broken (see `lastCheckOk`). "
                    + "May be up to 5 minutes old.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The station",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RadioStation.class))),
                    @ApiResponse(responseCode = "400", description = "The UUID is malformed, or a query parameter was "
                            + "sent (invalid-parameter)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "404", description = "A well-formed UUID the catalogue does not know "
                            + "(station-not-found); the detail names it",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "502", description = "The catalogue answered with something "
                            + "unreadable (catalogue-bad-answer)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "503", description = "No catalogue server answered in time "
                            + "(catalogue-unavailable)",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class))),
                    @ApiResponse(responseCode = "default", description = "405, 404 for another path, or 500",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = RadioProblem.class)))})
    @GetMapping("/stations/{uuid}")
    RadioStation getRadioStation(
            @Parameter(description = "The station UUID, any letter case.",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable("uuid") String uuid,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParameters);
}
