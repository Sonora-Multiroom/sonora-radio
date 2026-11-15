package multiroom.radio.catalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StationUuidTest {

    private static final String CANONICAL = "960e57c5-0601-11e8-ae97-52543be04c81";

    @Test
    void acceptsACanonicalUuidInAnyLetterCaseAndReturnsItLowerCased() {
        assertThat(StationUuid.parse(CANONICAL)).contains(UUID.fromString(CANONICAL));
        assertThat(StationUuid.parse(CANONICAL.toUpperCase())).contains(UUID.fromString(CANONICAL));
        assertThat(StationUuid.parse("960E57c5-0601-11E8-ae97-52543BE04c81").orElseThrow().toString())
                .isEqualTo(CANONICAL);
    }

    @Test
    void refusesNullAndBlank() {
        assertThat(StationUuid.parse(null)).isEmpty();
        assertThat(StationUuid.parse("")).isEmpty();
        assertThat(StationUuid.parse("   ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "960e57c5-0601-11e8-ae97-52543be04c8",       // 35 characters
            "960e57c5-0601-11e8-ae97-52543be04c811",     // 37 characters
            "{960e57c5-0601-11e8-ae97-52543be04c81}",    // braces
            "960e57c5-0601-11e8-ae97-52543be04c8g",      // non-hex digit
            "960e57c5 0601 11e8 ae97 52543be04c81",      // spaces
            " 960e57c5-0601-11e8-ae97-52543be04c81",     // leading whitespace
            "960e57c5-0601-11e8-ae97-52543be04c81 ",     // trailing whitespace
            "960e57c50601-11e8-ae97-52543be04c81"        // a hyphen missing
    })
    void refusesAnythingElse(String text) {
        assertThat(StationUuid.parse(text)).isEmpty();
    }
}
