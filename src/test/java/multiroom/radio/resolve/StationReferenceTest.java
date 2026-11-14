package multiroom.radio.resolve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationReferenceTest {

    private static final String UUID_TEXT = "960e57c5-0601-11e8-ae97-52543be04c81";
    private static final UUID EXPECTED = UUID.fromString(UUID_TEXT);

    @ParameterizedTest
    @ValueSource(strings = {
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81/",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81#kissfm",
            "RADIOBROWSER://960E57C5-0601-11E8-AE97-52543BE04C81",
            "RadioBrowser://960e57C5-0601-11e8-AE97-52543be04c81/#x"})
    void acceptedForms(String uri) {
        assertThat(StationReference.parse(uri).uuid()).isEqualTo(EXPECTED);
    }

    @Test
    void uuidIsCanonicalLowerCase() {
        assertThat(StationReference.parse("radiobrowser://960E57C5-0601-11E8-AE97-52543BE04C81").uuid().toString())
                .isEqualTo(UUID_TEXT);
    }

    @Test
    void labelIsHeldButIrrelevantToEquality() {
        StationReference a = StationReference.parse("radiobrowser://" + UUID_TEXT + "#one");
        StationReference b = StationReference.parse("radiobrowser://" + UUID_TEXT + "#two");
        assertThat(a.label()).isEqualTo("one");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(StationReference.parse("radiobrowser://" + UUID_TEXT).label()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "radiobrowser://",
            "radiobrowser:///",
            "radiobrowser://kissfm",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81/extra",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81?x=1",
            "radiobrowser://user@960e57c5-0601-11e8-ae97-52543be04c81",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c81:80",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c8",
            "radiobrowser://960e57c50-601-11e8-ae97-52543be04c81",
            "radiobrowser://960e57c5-0601-11e8-ae97-52543be04c8g",
            "http://960e57c5-0601-11e8-ae97-52543be04c81"})
    void refusedForms(String uri) {
        assertThatThrownBy(() -> StationReference.parse(uri)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsRefused() {
        assertThatThrownBy(() -> StationReference.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQuestionMarkInTheLabelIsPartOfTheLabel() {
        assertThat(StationReference.parse("radiobrowser://" + UUID_TEXT + "#a?b").label()).isEqualTo("a?b");
    }
}
