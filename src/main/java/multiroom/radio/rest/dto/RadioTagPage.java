package multiroom.radio.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of tags.
 *
 * @param tags       the tags of this page
 * @param nextOffset the {@code offset} of the next page; {@code null} on the last page
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RadioTagPage", description = "One page of tags")
public record RadioTagPage(
        @Schema(description = "The tags of this page", requiredMode = Schema.RequiredMode.REQUIRED) List<RadioTag> tags,
        @Schema(description = "The offset of the next page; null on the last page", nullable = true) Integer nextOffset) {
}
