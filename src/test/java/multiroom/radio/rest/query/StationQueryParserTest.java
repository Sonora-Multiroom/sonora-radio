package multiroom.radio.rest.query;

import multiroom.radio.catalogue.SortDirection;
import multiroom.radio.catalogue.StationOrder;
import multiroom.radio.catalogue.StationQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationQueryParserTest {

    /** Builds a query from the parameters a test cares about; everything else is not given. */
    private static final class Args {
        String name, tag, language, countryCode, codec, order, direction;
        Integer bitrateMin, limit, offset;

        Args name(String v) { name = v; return this; }
        Args tag(String v) { tag = v; return this; }
        Args language(String v) { language = v; return this; }
        Args countryCode(String v) { countryCode = v; return this; }
        Args codec(String v) { codec = v; return this; }
        Args order(String v) { order = v; return this; }
        Args direction(String v) { direction = v; return this; }
        Args bitrateMin(Integer v) { bitrateMin = v; return this; }
        Args limit(Integer v) { limit = v; return this; }
        Args offset(Integer v) { offset = v; return this; }

        StationQuery parse() {
            return StationQueryParser.parse(name, tag, language, countryCode, codec, bitrateMin, order, direction,
                    limit, offset);
        }
    }

    private static void assertRefused(Args args, String parameter) {
        assertThatThrownBy(args::parse)
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> {
                    assertThat(e.parameter()).isEqualTo(parameter);
                    assertThat(e.reason()).isNotBlank();
                });
    }

    @Test
    void defaultsAreTwentyFromTheStartMostVotedFirst() {
        StationQuery q = new Args().parse();
        assertThat(q.limit()).isEqualTo(20);
        assertThat(q.offset()).isZero();
        assertThat(q.order()).isEqualTo(StationOrder.VOTES);
        assertThat(q.direction()).isEqualTo(SortDirection.DESC);
        assertThat(q.name()).isNull();
        assertThat(q.tag()).isNull();
        assertThat(q.language()).isNull();
        assertThat(q.countryCode()).isNull();
        assertThat(q.codec()).isNull();
        assertThat(q.bitrateMin()).isNull();
    }

    @Test
    void theDefaultDirectionDependsOnTheOrder() {
        assertThat(new Args().order("name").parse().direction()).isEqualTo(SortDirection.ASC);
        assertThat(new Args().order("bitrate").parse().direction()).isEqualTo(SortDirection.ASC);
        assertThat(new Args().order("votes").parse().direction()).isEqualTo(SortDirection.DESC);
        assertThat(new Args().order("clickCount").parse().direction()).isEqualTo(SortDirection.DESC);
        assertThat(new Args().order("clickCount").direction("asc").parse().direction()).isEqualTo(SortDirection.ASC);
        assertThat(new Args().order("clickCount").parse().order()).isEqualTo(StationOrder.CLICK_COUNT);
    }

    @Test
    void textFiltersAreTrimmedAndBlankMeansAbsent() {
        assertThat(new Args().name("  jazz fm  ").parse().name()).isEqualTo("jazz fm");
        assertThat(new Args().name("   ").parse().name()).isNull();
        assertThat(new Args().name("").parse().name()).isNull();
        assertThat(new Args().codec(" ").parse().codec()).isNull();
    }

    @Test
    void tagAndLanguageAreLowerCasedAndCountryCodeUpperCased() {
        StationQuery q = new Args().tag("Jazz").language("UKRAINIAN").countryCode("ua").parse();
        assertThat(q.tag()).isEqualTo("jazz");
        assertThat(q.language()).isEqualTo("ukrainian");
        assertThat(q.countryCode()).isEqualTo("UA");
    }

    @Test
    void nameAndCodecKeepTheirCase() {
        StationQuery q = new Args().name("Radio Roks").codec("AAC+").parse();
        assertThat(q.name()).isEqualTo("Radio Roks");
        assertThat(q.codec()).isEqualTo("AAC+");
    }

    @Test
    void textOfOneHundredCodePointsPassesAndOneMoreIsRefused() {
        assertThat(new Args().name("x".repeat(100)).parse().name()).hasSize(100);
        assertRefused(new Args().name("x".repeat(101)), "name");
        assertRefused(new Args().tag("x".repeat(101)), "tag");
        assertRefused(new Args().language("x".repeat(101)), "language");
        // 100 supplementary characters are 200 UTF-16 units but 100 code points
        assertThat(new Args().name("🎵".repeat(100)).parse().name()).isNotNull();
        assertRefused(new Args().name("🎵".repeat(101)), "name");
    }

    @Test
    void codecIsLimitedToThirtyTwoCodePoints() {
        assertThat(new Args().codec("c".repeat(32)).parse().codec()).hasSize(32);
        assertRefused(new Args().codec("c".repeat(33)), "codec");
    }

    @Test
    void controlCharactersAndPercentAreRefused() {
        assertRefused(new Args().name("a\u0000b"), "name");
        assertRefused(new Args().name("a\nb"), "name");
        assertRefused(new Args().tag("a\u0007"), "tag");
        assertRefused(new Args().language("a b"), "language");
        assertRefused(new Args().codec("a\u0001"), "codec");
        assertRefused(new Args().name("50%"), "name");
        assertRefused(new Args().tag("%41"), "tag");
    }

    @Test
    void aTagMayContainASlash() {
        assertThat(new Args().tag("r/b").parse().tag()).isEqualTo("r/b");
    }

    @Test
    void countryCodeIsExactlyTwoAsciiLetters() {
        assertRefused(new Args().countryCode("U"), "countryCode");
        assertRefused(new Args().countryCode("UAH"), "countryCode");
        assertRefused(new Args().countryCode("U1"), "countryCode");
        assertRefused(new Args().countryCode("УА"), "countryCode");
        assertRefused(new Args().countryCode("u "), "countryCode");
    }

    @Test
    void bitrateMinIsZeroToOneMillion() {
        assertThat(new Args().bitrateMin(0).parse().bitrateMin()).isZero();
        assertThat(new Args().bitrateMin(1000000).parse().bitrateMin()).isEqualTo(1000000);
        assertRefused(new Args().bitrateMin(-1), "bitrateMin");
        assertRefused(new Args().bitrateMin(1000001), "bitrateMin");
    }

    @Test
    void limitIsOneToOneHundredAndIsRefusedNeverReduced() {
        assertThat(new Args().limit(1).parse().limit()).isEqualTo(1);
        assertThat(new Args().limit(100).parse().limit()).isEqualTo(100);
        assertRefused(new Args().limit(0), "limit");
        assertRefused(new Args().limit(101), "limit");
        assertRefused(new Args().limit(-5), "limit");
    }

    @Test
    void offsetIsZeroToTenThousand() {
        assertThat(new Args().offset(10000).parse().offset()).isEqualTo(10000);
        assertRefused(new Args().offset(-1), "offset");
        assertRefused(new Args().offset(10001), "offset");
    }

    @Test
    void orderAndDirectionAreCaseSensitiveAndLimitedToTheAllowedWords() {
        assertRefused(new Args().order("popularity"), "order");
        assertRefused(new Args().order("Votes"), "order");
        assertRefused(new Args().order("CLICK_COUNT"), "order");
        assertRefused(new Args().order("clickcount"), "order");
        assertRefused(new Args().direction("up"), "direction");
        assertRefused(new Args().direction("DESC"), "direction");
        assertRefused(new Args().order("").direction("x"), "direction");
    }

    @Test
    void theReasonNamesTheAllowedRange() {
        assertThatThrownBy(() -> new Args().limit(500).parse())
                .isInstanceOfSatisfying(InvalidParameterException.class,
                        e -> assertThat(e.reason()).contains("1").contains("100"));
    }

    @Test
    void theReasonNeverEchoesALongValue() {
        assertThatThrownBy(() -> new Args().name("secret".repeat(40)).parse())
                .isInstanceOfSatisfying(InvalidParameterException.class,
                        e -> assertThat(e.reason()).doesNotContain("secretsecret"));
    }
}
