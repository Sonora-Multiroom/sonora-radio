package multiroom.radio.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of search results.
 *
 * @param stations   the stations of this page
 * @param nextOffset the {@code offset} of the next page; {@code null} on the last page
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RadioStationPage", description = "One page of stations")
public record RadioStationPage(
        @Schema(description = "The stations of this page", requiredMode = Schema.RequiredMode.REQUIRED)
        List<RadioStation> stations,
        @Schema(description = "The offset of the next page; null on the last page", nullable = true)
        Integer nextOffset) {
}
