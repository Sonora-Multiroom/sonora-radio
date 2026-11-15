package multiroom.radio.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A station as clients see it: cleaned, bounded, and with a {@code uri} ready for {@code POST
 * /api/v2/play}. Null fields are written as {@code null}, never left out, so every station has the
 * same keys.
 *
 * @param uuid        the station's stable identifier, lower case
 * @param uri         pass unchanged to {@code POST /api/v2/play}
 * @param name        community-edited text, at most 256 characters
 * @param favicon     an http or https address the hub has not fetched or checked
 * @param homepage    an http or https address the hub has not fetched or checked
 * @param countryCode ISO 3166-1 alpha-2, upper case
 * @param languages   at most 10 language names, in the catalogue's order
 * @param tags        at most 30 tags, in the catalogue's order
 * @param codec       the catalogue's label, for example MP3, AAC+, OGG, UNKNOWN
 * @param bitrate     kbit/s; {@code null} when unknown
 * @param hls         an HLS stream; the hub may be configured to refuse these when played
 * @param lastCheckOk whether the catalogue's last check of the stream succeeded
 * @param votes       the catalogue's vote count
 * @param clickCount  plays reported in the last 24 hours
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RadioStation", description = "A radio station")
public record RadioStation(
        @Schema(description = "The station's stable identifier, lower case", format = "uuid",
                requiredMode = Schema.RequiredMode.REQUIRED) String uuid,
        @Schema(description = "Pass unchanged to POST /api/v2/play",
                example = "radiobrowser://9617a958-0601-11e8-ae97-52543be04c81",
                requiredMode = Schema.RequiredMode.REQUIRED) String uri,
        @Schema(description = "Community-edited text", maxLength = 256, nullable = true) String name,
        @Schema(description = "An http or https address the hub has not fetched or checked", maxLength = 2048,
                nullable = true) String favicon,
        @Schema(description = "An http or https address the hub has not fetched or checked", maxLength = 2048,
                nullable = true) String homepage,
        @Schema(description = "ISO 3166-1 alpha-2, upper case", nullable = true) String countryCode,
        @Schema(description = "At most 10 language names, in the catalogue's order",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(maxItems = 10, schema = @Schema(maxLength = 64)) List<String> languages,
        @Schema(description = "At most 30 tags, in the catalogue's order",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(maxItems = 30, schema = @Schema(maxLength = 64)) List<String> tags,
        @Schema(description = "The catalogue's label, for example MP3, AAC+, OGG, UNKNOWN", maxLength = 32,
                nullable = true) String codec,
        @Schema(description = "kbit/s; null when unknown", nullable = true) Integer bitrate,
        @Schema(description = "An HLS stream; the hub may be configured to refuse these when played",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean hls,
        @Schema(description = "Whether the catalogue's last check of the stream succeeded",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean lastCheckOk,
        @Schema(description = "The catalogue's vote count", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) long votes,
        @Schema(description = "Plays reported in the last 24 hours", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) long clickCount) {
}
