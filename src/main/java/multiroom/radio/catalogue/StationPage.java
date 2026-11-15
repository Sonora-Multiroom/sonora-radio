package multiroom.radio.catalogue;

import java.util.List;

/**
 * The stations of one search page.
 *
 * @param stations the records kept (those with a valid UUID)
 * @param received how many records the catalogue's answer held before any was dropped; what paging
 *                 is worked out from, because the catalogue counts what it sent, not what survived
 */
public record StationPage(List<Station> stations, int received) {
}
