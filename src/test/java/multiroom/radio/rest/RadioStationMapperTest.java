package multiroom.radio.rest;

import multiroom.radio.catalogue.Country;
import multiroom.radio.catalogue.Language;
import multiroom.radio.catalogue.Station;
import multiroom.radio.catalogue.TagCount;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioTag;
import multiroom.radio.support.StationBuilder;
import multiroom.radio.rest.dto.RadioStation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RadioStationMapperTest {

    private static final UUID ID = UUID.fromString("9617a958-0601-11e8-ae97-52543be04c81");

    private final RadioStationMapper mapper = new RadioStationMapper();

    /** A station whose every field is clean; tests change the one they are about. */
    private static StationBuilder base() {
        return new StationBuilder();
    }

    private RadioStation map(Station station) {
        return mapper.station(station);
    }

    // ---- name, uuid, uri ------------------------------------------------------------------------

    @Test
    void uuidIsLowerCaseAndUriIsTheRadiobrowserSchemeWithIt() {
        RadioStation s = map(base().build());
        assertThat(s.uuid()).isEqualTo("9617a958-0601-11e8-ae97-52543be04c81");
        assertThat(s.uri()).isEqualTo("radiobrowser://9617a958-0601-11e8-ae97-52543be04c81");
    }

    @Test
    void theNameFollowsTheStationTitleRules() {
        assertThat(map(base().name("  Kiss \t\u0000 FM ").build()).name()).isEqualTo("Kiss FM");
        assertThat(map(base().name("x".repeat(300)).build()).name()).hasSize(256);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\u0000\u0007"})
    void aNameWithNothingLeftIsNullAndTheStationIsStillReturned(String name) {
        RadioStation s = map(base().name(name).build());
        assertThat(s.name()).isNull();
        assertThat(s.uuid()).isNotNull();
    }

    // ---- tags and languages ---------------------------------------------------------------------

    @Test
    void tagsAreSplitTrimmedCleanedDeduplicatedAndKeepTheCataloguesOrder() {
        RadioStation s = map(base().tags(" jazz , smooth\u0000 jazz,,  ,jazz,rock ").build());
        assertThat(s.tags()).containsExactly("jazz", "smooth jazz", "rock");
    }

    @Test
    void anEntryOverSixtyFourCodePointsIsDroppedNotCut() {
        String tooLong = "t".repeat(65);
        RadioStation s = map(base().tags("a," + tooLong + "," + "b".repeat(64)).build());
        assertThat(s.tags()).containsExactly("a", "b".repeat(64));
        assertThat(map(base().languages("english," + tooLong).build()).languages()).containsExactly("english");
    }

    @Test
    void atMostThirtyTagsAndTenLanguagesInTheCataloguesOrder() {
        String fiftyEight = IntStream.range(0, 58).mapToObj(i -> "tag" + i).collect(Collectors.joining(","));
        RadioStation s = map(base().tags(fiftyEight).build());
        assertThat(s.tags()).hasSize(30).startsWith("tag0", "tag1").endsWith("tag29");
        String fifteen = IntStream.range(0, 15).mapToObj(i -> "lang" + i).collect(Collectors.joining(","));
        assertThat(map(base().languages(fifteen).build()).languages()).hasSize(10).endsWith("lang9");
    }

    @Test
    void listsAreEmptyNeverNull() {
        RadioStation s = map(base().tags(null).languages("").build());
        assertThat(s.tags()).isEmpty();
        assertThat(s.languages()).isEmpty();
    }

    // ---- favicon and homepage -------------------------------------------------------------------

    @Test
    void anHttpAndAnHttpsAddressWithAHostAreKept() {
        assertThat(map(base().favicon("https://x.test/i.png").homepage("http://x.test/").build()))
                .matches(s -> "https://x.test/i.png".equals(s.favicon()) && "http://x.test/".equals(s.homepage()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://x.test/a", "/relative/path", "x.test/no-scheme", "http://",
            "https:///nohost", "http://exa mple.test/", "data:text/html,hi", "file:///etc/passwd", "   "})
    void anyOtherAddressIsNull(String address) {
        RadioStation s = map(base().favicon(address).homepage(address).build());
        assertThat(s.favicon()).isNull();
        assertThat(s.homepage()).isNull();
    }

    @Test
    void anAddressIsKeptUpTo2048CharactersAndNullBeyond() {
        String prefix = "https://x.test/";
        String ok = prefix + "a".repeat(2048 - prefix.length());
        String tooLong = ok + "a";
        assertThat(ok).hasSize(2048);
        assertThat(map(base().favicon(ok).build()).favicon()).isEqualTo(ok);
        assertThat(map(base().favicon(tooLong).build()).favicon()).isNull();
    }

    @Test
    void anAbsentAddressIsNull() {
        RadioStation s = map(base().favicon(null).homepage(null).build());
        assertThat(s.favicon()).isNull();
        assertThat(s.homepage()).isNull();
    }

    // ---- country code, codec, bitrate -----------------------------------------------------------

    @Test
    void countryCodeIsTwoAsciiLettersUpperCased() {
        assertThat(map(base().countryCode("ua").build()).countryCode()).isEqualTo("UA");
        for (String bad : new String[]{null, "", "U", "UAH", "U1", "УА", " UA"}) {
            assertThat(map(base().countryCode(bad).build()).countryCode()).as(String.valueOf(bad)).isNull();
        }
    }

    @Test
    void codecIsCleanedCutToThirtyTwoAndBlankIsNull() {
        assertThat(map(base().codecLabel("AAC+").build()).codec()).isEqualTo("AAC+");
        assertThat(map(base().codecLabel("M\u0000P3").build()).codec()).isEqualTo("MP3");
        assertThat(map(base().codecLabel("c".repeat(40)).build()).codec()).hasSize(32);
        assertThat(map(base().codecLabel("").build()).codec()).isNull();
        assertThat(map(base().codecLabel("  \u0000 ").build()).codec()).isNull();
    }

    @Test
    void bitrateIsPositiveOrNull() {
        assertThat(map(base().bitrate(128).build()).bitrate()).isEqualTo(128);
        assertThat(map(base().bitrate(0).build()).bitrate()).isNull();
        assertThat(map(base().bitrate(-5).build()).bitrate()).isNull();
    }

    @Test
    void flagsAndCountsAreCarriedOver() {
        RadioStation s = map(base().hls(true).lastCheckOk(false).votes(42).clickCount(7).build());
        assertThat(s.hls()).isTrue();
        assertThat(s.lastCheckOk()).isFalse();
        assertThat(s.votes()).isEqualTo(42);
        assertThat(s.clickCount()).isEqualTo(7);
    }

    @Test
    void theStreamAddressIsNeverPartOfTheApiShape() {
        assertThat(RadioStation.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("streamUrl", "url");
        List<String> names = java.util.Arrays.stream(RadioStation.class.getRecordComponents())
                .map(c -> c.getName()).toList();
        assertThat(names).containsExactly("uuid", "uri", "name", "favicon", "homepage", "countryCode", "languages",
                "tags", "codec", "bitrate", "hls", "lastCheckOk", "votes", "clickCount");
    }

    // ---- lists (RADIO-002) ---------------------------------------------------------------------

    @Test
    void aCountryNameIsCleanedAndCutAtOneHundredCodePointsAndTheCodeIsUpperCased() {
        List<RadioCountry> out = mapper.countries(List.of(new Country("ua", "Ukra\u0000ine", 312),
                new Country("US", "n".repeat(150), 7)));
        assertThat(out).extracting(RadioCountry::code).containsExactly("UA", "US");
        assertThat(out.get(0).name()).isEqualTo("Ukraine");
        assertThat(out.get(0).stationCount()).isEqualTo(312);
        assertThat(out.get(1).name()).hasSize(100);
    }

    @Test
    void aCountryWithoutAValidCodeIsDropped() {
        List<RadioCountry> out = mapper.countries(List.of(new Country("x1", "Odd", 1), new Country("USA", "Three", 1),
                new Country("", "Empty", 1), new Country(null, "Null", 1), new Country("\u0423\u0410", "Cyrillic", 1),
                new Country("DE", "Germany", 5)));
        assertThat(out).extracting(RadioCountry::code).containsExactly("DE");
    }

    @Test
    void aLanguageNameIsCleanedAndBlankOrOverlongDropsTheEntry() {
        List<RadioLanguage> out = mapper.languages(List.of(new Language("eng\u0000lish", "en", 9),
                new Language("  ", "xx", 1), new Language("l".repeat(65), "yy", 1), new Language("l".repeat(64), null, 2),
                new Language(null, "zz", 1)));
        assertThat(out).extracting(RadioLanguage::name).containsExactly("english", "l".repeat(64));
    }

    @Test
    void aLanguageCodeIsTwoOrThreeAsciiLettersLowerCasedElseNull() {
        List<RadioLanguage> out = mapper.languages(List.of(new Language("a", "EN", 1), new Language("b", "ast", 1),
                new Language("c", "e", 1), new Language("d", "engl", 1), new Language("e", "e1", 1),
                new Language("f", null, 1), new Language("g", "\u0443\u043a", 1)));
        assertThat(out).extracting(RadioLanguage::code).containsExactly("en", "ast", null, null, null, null, null);
    }

    @Test
    void aTagNameFollowsTheLanguageRulesAndTheOrderIsTheCataloguesNotSorted() {
        List<RadioTag> out = mapper.tags(List.of(new TagCount("zebra", 1), new TagCount("a\u0000lpha", 5),
                new TagCount(" ", 3), new TagCount("t".repeat(65), 3), new TagCount(null, 1), new TagCount("mid", 3)));
        assertThat(out).extracting(RadioTag::name).containsExactly("zebra", "alpha", "mid");
        assertThat(out).extracting(RadioTag::stationCount).containsExactly(1L, 5L, 3L);
    }
}
