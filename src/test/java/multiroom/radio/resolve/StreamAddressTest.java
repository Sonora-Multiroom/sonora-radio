package multiroom.radio.resolve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://stream.example.test/live", "https://stream.example.test:8443/live.mp3?x=1",
            "HTTP://STREAM.example.test/a", "Https://stream.example.test/a"})
    void httpAndHttpsAreAccepted(String address) {
        assertThat(StreamAddress.parse(address)).hasToString(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "jar:file:/a.jar!/x", "alsa://hw:0,0", "ftp://example.test/a",
            "/relative/path", "relative", "http://", "https:///nohost", "http://exa mple.test", "", " "})
    void everythingElseIsRefused(String address) {
        assertThatThrownBy(() -> StreamAddress.parse(address)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsRefused() {
        assertThatThrownBy(() -> StreamAddress.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRefusalNamesTheSchemeNotTheAddress() {
        assertThatThrownBy(() -> StreamAddress.parse("file:///secret/path"))
                .hasMessage("file").hasMessageNotContaining("secret");
    }
}
