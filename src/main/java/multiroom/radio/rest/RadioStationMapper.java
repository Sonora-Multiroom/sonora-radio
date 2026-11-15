package multiroom.radio.rest;

import multiroom.radio.catalogue.Country;
import multiroom.radio.catalogue.Language;
import multiroom.radio.catalogue.Station;
import multiroom.radio.catalogue.TagCount;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioTag;
import multiroom.radio.text.StationTitle;
import multiroom.radio.text.Text;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns what the catalogue sent into what clients see. The catalogue is community-edited, so every
 * text is untrusted: this class holds all the cleaning rules, in one place, with fixed bounds.
 *
 * <p>A tag or language that is too long is <em>dropped</em>, not cut: a cut tag would match nothing
 * when a client sends it back as a filter, and dropping loses nothing a client could use.
 */
public class RadioStationMapper {

    static final int MAX_TAGS = 30;
    static final int MAX_LANGUAGES = 10;
    static final int MAX_LIST_ENTRY = 64;
    static final int MAX_ADDRESS = 2048;
    static final int MAX_CODEC = 32;
    static final int MAX_COUNTRY_NAME = 100;

    private static final String URI_PREFIX = "radiobrowser://";

    /** Maps one catalogue station. */
    public RadioStation station(Station station) {
        String uuid = station.uuid().toString();
        return new RadioStation(uuid, URI_PREFIX + uuid,
                StationTitle.of(station.name()).orElse(null),
                address(station.favicon()), address(station.homepage()),
                countryCode(station.countryCode()),
                list(station.languages(), MAX_LANGUAGES), list(station.tags(), MAX_TAGS),
                codec(station.codecLabel()),
                station.bitrate() > 0 ? station.bitrate() : null,
                station.hls(), station.lastCheckOk(), station.votes(), station.clickCount());
    }

    private static List<String> list(String raw, int maxItems) {
        if (raw == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String entry = Text.withoutControl(part).strip();
            if (entry.isEmpty() || entry.codePointCount(0, entry.length()) > MAX_LIST_ENTRY) {
                continue;
            }
            out.add(entry);
            if (out.size() == maxItems) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    /** An address clients may show: http or https with a host, bounded, never fetched by the hub. */
    private static String address(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.isEmpty() || text.length() > MAX_ADDRESS) {
            return null;
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            boolean web = scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            return web && uri.getHost() != null && !uri.getHost().isBlank() ? text : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String countryCode(String raw) {
        if (raw == null || raw.length() != 2) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            char c = raw.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return null;
            }
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private static String codec(String label) {
        if (label == null) {
            return null;
        }
        String cleaned = Text.truncate(Text.withoutControl(label), MAX_CODEC).strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Maps the catalogue's countries; an entry without a valid two-letter code is dropped. */
    public List<RadioCountry> countries(List<Country> countries) {
        List<RadioCountry> out = new ArrayList<>();
        for (Country country : countries) {
            String code = countryCode(country.code());
            if (code == null) {
                continue;
            }
            String name = country.name() == null ? "" : Text.truncate(Text.withoutControl(country.name()).strip(), MAX_COUNTRY_NAME).strip();
            out.add(new RadioCountry(code, name.isEmpty() ? code : name, country.stationCount()));
        }
        return out;
    }

    /** Maps the catalogue's languages; a blank or over-long name drops the entry. */
    public List<RadioLanguage> languages(List<Language> languages) {
        List<RadioLanguage> out = new ArrayList<>();
        for (Language language : languages) {
            String name = listName(language.name());
            if (name != null) {
                out.add(new RadioLanguage(name, languageCode(language.code()), language.stationCount()));
            }
        }
        return out;
    }

    /** Maps the catalogue's tags; a blank or over-long name drops the entry. */
    public List<RadioTag> tags(List<TagCount> tags) {
        List<RadioTag> out = new ArrayList<>();
        for (TagCount tag : tags) {
            String name = listName(tag.name());
            if (name != null) {
                out.add(new RadioTag(name, tag.stationCount()));
            }
        }
        return out;
    }

    /** A language or tag name, cleaned; {@code null} when blank or longer than 64 code points (dropped, not cut). */
    private static String listName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = Text.withoutControl(raw).strip();
        return name.isEmpty() || name.codePointCount(0, name.length()) > MAX_LIST_ENTRY ? null : name;
    }

    private static String languageCode(String raw) {
        if (raw == null || raw.length() < 2 || raw.length() > 3) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return null;
            }
        }
        return raw.toLowerCase(Locale.ROOT);
    }
}
