package multiroom.radio.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A tag with stations.
 *
 * @param name         the value to send as {@code tag} in a search
 * @param stationCount how many stations carry it
 */
@Schema(name = "RadioTag", description = "A tag with stations")
public record RadioTag(
        @Schema(description = "The value to send as `tag` in a search", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "How many stations carry it", requiredMode = Schema.RequiredMode.REQUIRED) long stationCount) {
}
