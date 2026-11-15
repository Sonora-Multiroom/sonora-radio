package multiroom.radio.rest.query;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestParametersTest {

    private static final Set<String> ALLOWED = Set.of("name", "limit");

    private static MultiValueMap<String, String> params(Object... pairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add((String) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void anEmptyMapPasses() {
        assertThatCode(() -> RequestParameters.check(params(), ALLOWED)).doesNotThrowAnyException();
    }

    @Test
    void allowedNamesPass() {
        assertThatCode(() -> RequestParameters.check(params("name", "x", "limit", "5"), ALLOWED))
                .doesNotThrowAnyException();
    }

    @Test
    void aNameOutsideTheAllowedSetIsRefusedAndNamed() {
        assertThatThrownBy(() -> RequestParameters.check(params("countrycode", "UA"), ALLOWED))
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> {
                    assertThat(e.parameter()).isEqualTo("countrycode");
                    assertThat(e.reason()).contains("not");
                });
    }

    @Test
    void aNameSentTwiceIsRefusedAndNamed() {
        MultiValueMap<String, String> twice = new LinkedMultiValueMap<>();
        twice.put("limit", List.of("5", "6"));
        assertThatThrownBy(() -> RequestParameters.check(twice, ALLOWED))
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> {
                    assertThat(e.parameter()).isEqualTo("limit");
                    assertThat(e.reason()).containsIgnoringCase("once");
                });
    }

    @Test
    void anUnknownNameWithControlCharactersIsNamedCleanedAndCutToThirtyTwo() {
        String hostile = "q\r\nInjected: header" + "z".repeat(60);
        assertThatThrownBy(() -> RequestParameters.check(params(hostile, "1"), ALLOWED))
                .isInstanceOfSatisfying(InvalidParameterException.class, e -> {
                    assertThat(e.parameter()).doesNotContain("\r").doesNotContain("\n");
                    assertThat(e.parameter().codePointCount(0, e.parameter().length())).isLessThanOrEqualTo(32);
                    assertThat(e.reason()).doesNotContain("\r").doesNotContain("\n");
                });
    }

    @Test
    void aLongUnknownNameIsCutToThirtyTwoCharacters() {
        assertThatThrownBy(() -> RequestParameters.check(params("n".repeat(100), "1"), ALLOWED))
                .isInstanceOfSatisfying(InvalidParameterException.class,
                        e -> assertThat(e.parameter()).hasSize(32));
    }
}
