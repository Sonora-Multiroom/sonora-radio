package multiroom.radio.catalogue;

import java.util.UUID;

/**
 * A station as the catalogue describes it. Every text field is untrusted (the catalogue is
 * community-edited) and stays raw here; it is cleaned where it leaves the extension, by
 * {@code StationTitle} for plays and by the REST mapper for the API.
 *
 * @param uuid        the station's UUID
 * @param name        its name, possibly {@code null}
 * @param streamUrl   {@code url_resolved}, else {@code url}; possibly {@code null}; used by plays only
 * @param codecLabel  the catalogue's free-form codec label, never {@code null}
 * @param hls         whether the catalogue marks it HLS
 * @param favicon     the station's icon address, possibly {@code null}
 * @param homepage    the station's web page, possibly {@code null}
 * @param countryCode {@code countrycode}, possibly {@code null}
 * @param languages   {@code language}, comma-separated, possibly {@code null}
 * @param tags        {@code tags}, comma-separated, possibly {@code null}
 * @param bitrate     kbit/s; 0 when the catalogue does not say
 * @param lastCheckOk whether the catalogue's last check of the stream succeeded
 * @param votes       the catalogue's vote count; 0 when missing
 * @param clickCount  plays reported in the last 24 hours; 0 when missing
 */
public record Station(UUID uuid, String name, String streamUrl, String codecLabel, boolean hls,
                      String favicon, String homepage, String countryCode, String languages, String tags,
                      int bitrate, boolean lastCheckOk, long votes, long clickCount) {
}
