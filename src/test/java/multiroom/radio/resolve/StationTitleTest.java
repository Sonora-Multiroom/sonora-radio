package multiroom.radio.resolve;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationTitleTest {

    @Test
    void controlCharactersAndSeparatorsAreRemoved() {
        assertThat(StationTitle.of("Test\u0000 FM  \u0007")).contains("Test FM");
    }

    @Test
    void whitespaceRunsCollapseAndTheResultIsTrimmed() {
        assertThat(StationTitle.of("  Kiss \t\n  FM   ")).contains("Kiss FM");
    }

    @Test
    void blankNamesYieldNothing() {
        assertThat(StationTitle.of(null)).isEmpty();
        assertThat(StationTitle.of("")).isEmpty();
        assertThat(StationTitle.of(" \t\n\u0000 ")).isEmpty();
    }

    @Test
    void longNamesAreCutAt256CodePoints() {
        String title = StationTitle.of("x".repeat(300)).orElseThrow();
        assertThat(title.codePointCount(0, title.length())).isEqualTo(256);
    }

    @Test
    void theCutNeverSplitsASurrogatePair() {
        String name = "x".repeat(255) + "🎵" + "tail";
        String title = StationTitle.of(name).orElseThrow();
        assertThat(title.codePointCount(0, title.length())).isEqualTo(256);
        assertThat(title).endsWith("🎵");
    }

    @Test
    void htmlIsKeptAsText() {
        assertThat(StationTitle.of("<b>Loud</b> FM")).contains("<b>Loud</b> FM");
    }
}
