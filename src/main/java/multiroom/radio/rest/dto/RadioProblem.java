package multiroom.radio.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An RFC 7807 problem detail, with the fields of the hub's own error body so that clients read both
 * the same way. {@code parameter} is left out when no single parameter is to blame. The timestamp is
 * text (ISO-8601) so the shape does not depend on how the host's JSON mapper is configured.
 *
 * @param type      a stable {@code urn:multiroom:radio:error:*} identifier
 * @param title     a short fixed title for the type
 * @param detail    what happened, never containing catalogue text or a long client value
 * @param status    the HTTP status
 * @param instance  the request path
 * @param timestamp when it happened
 * @param parameter the offending parameter, only for an invalid-parameter problem
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RadioProblem", description = "RFC 7807 problem details")
public record RadioProblem(
        @Schema(description = "One of urn:multiroom:radio:error:invalid-parameter, :station-not-found, "
                + ":catalogue-bad-answer, :catalogue-unavailable, :method-not-allowed, :not-found, :internal-error. "
                + "More may be added.", requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(description = "A short fixed title for the type", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(description = "What happened", requiredMode = Schema.RequiredMode.REQUIRED) String detail,
        @Schema(description = "The HTTP status", requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(description = "The request path") String instance,
        @Schema(description = "When it happened", format = "date-time") String timestamp,
        @Schema(description = "The offending parameter; only on invalid-parameter") String parameter) {
}
