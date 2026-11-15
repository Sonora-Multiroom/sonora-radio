package multiroom.radio.catalogue;

import java.util.List;

/**
 * One page of tags.
 *
 * @param tags     the entries kept
 * @param received how many entries the catalogue's answer held before any was dropped
 */
public record TagPage(List<TagCount> tags, int received) {
}
