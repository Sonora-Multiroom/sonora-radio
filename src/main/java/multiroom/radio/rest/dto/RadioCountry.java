package multiroom.radio.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A country with stations.
 *
 * @param code         ISO 3166-1 alpha-2, upper case
 * @param name         the catalogue's English name; clients localise from {@code code}
 * @param stationCount how many stations the catalogue holds for it
 */
@Schema(name = "RadioCountry", description = "A country with stations")
public record RadioCountry(
        @Schema(description = "ISO 3166-1 alpha-2, upper case", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "English name; localise from `code`", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "How many stations the catalogue holds for it", requiredMode = Schema.RequiredMode.REQUIRED)
        long stationCount) {
}
