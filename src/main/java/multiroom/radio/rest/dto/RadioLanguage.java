package multiroom.radio.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A language with stations.
 *
 * @param name         the value to send as {@code language} in a search
 * @param code         ISO 639 where the catalogue has one, otherwise {@code null} (most entries have none)
 * @param stationCount how many stations speak it
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RadioLanguage", description = "A language with stations")
public record RadioLanguage(
        @Schema(description = "The value to send as `language` in a search", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "ISO 639 code where the catalogue has one", nullable = true) String code,
        @Schema(description = "How many stations speak it", requiredMode = Schema.RequiredMode.REQUIRED) long stationCount) {
}
