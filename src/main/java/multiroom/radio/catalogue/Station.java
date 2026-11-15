package multiroom.radio.catalogue;

import java.util.UUID;

/**
 * A station as the catalogue describes it (untrusted text).
 *
 * @param uuid       the station's UUID
 * @param name       its name, possibly {@code null}
 * @param streamUrl  {@code url_resolved}, else {@code url}; possibly {@code null}
 * @param codecLabel the catalogue's free-form codec label, never {@code null}
 * @param hls        whether the catalogue marks it HLS
 */
public record Station(UUID uuid, String name, String streamUrl, String codecLabel, boolean hls) {
}
