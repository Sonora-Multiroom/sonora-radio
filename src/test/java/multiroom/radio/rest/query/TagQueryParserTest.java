package multiroom.radio.rest.query;

import multiroom.radio.catalogue.TagQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagQueryParserTest {

    private static void assertRefused(String name, Integer limit, Integer offset, String parameter) {
        assertThatThrownBy(() -> TagQueryParser.parse(name, limit, offset))
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> {
                    assertThat(e.parameter()).isEqualTo(parameter);
                    assertThat(e.reason()).isNotBlank();
                });
    }

    @Test
    void defaultsAreTwentyFromTheStartWithNoFilter() {
        TagQuery q = TagQueryParser.parse(null, null, null);
        assertThat(q.name()).isNull();
        assertThat(q.limit()).isEqualTo(20);
        assertThat(q.offset()).isZero();
    }

    @Test
    void theNameIsTrimmedLowerCasedAndBlankMeansAbsent() {
        assertThat(TagQueryParser.parse("  Smooth Jazz ", null, null).name()).isEqualTo("smooth jazz");
        assertThat(TagQueryParser.parse("   ", null, null).name()).isNull();
        assertThat(TagQueryParser.parse("", null, null).name()).isNull();
    }

    @Test
    void theNameHasTheSameCapsAsASearchFilter() {
        assertThat(TagQueryParser.parse("x".repeat(100), null, null).name()).hasSize(100);
        assertRefused("x".repeat(101), null, null, "name");
        assertRefused("a\u0000b", null, null, "name");
        assertRefused("a\nb", null, null, "name");
        assertRefused("50%", null, null, "name");
    }

    @Test
    void aSlashIsRefusedBecauseTheNameBecomesAPathSegment() {
        assertRefused("r/b", null, null, "name");
    }

    @Test
    void limitAndOffsetFollowTheSearchRules() {
        assertThat(TagQueryParser.parse(null, 100, 10000).limit()).isEqualTo(100);
        assertThat(TagQueryParser.parse(null, 100, 10000).offset()).isEqualTo(10000);
        assertRefused(null, 0, null, "limit");
        assertRefused(null, 101, null, "limit");
        assertRefused(null, null, -1, "offset");
        assertRefused(null, null, 10001, "offset");
    }
}
