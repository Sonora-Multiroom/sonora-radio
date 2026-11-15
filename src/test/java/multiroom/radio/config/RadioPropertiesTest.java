package multiroom.radio.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RadioPropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static RadioProperties bind(String... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put("multiroom.radio." + keyValues[i], keyValues[i + 1]);
        }
        return new Binder(new MapConfigurationPropertySource(map))
                .bind("multiroom.radio", Bindable.of(RadioProperties.class))
                .orElseGet(RadioProperties::new);
    }

    private static List<String> messages(RadioProperties properties) {
        return VALIDATOR.validate(properties).stream().map(v -> v.getMessage()).toList();
    }

    private static void assertRejected(String property, String... keyValues) {
        assertThat(messages(bind(keyValues))).anyMatch(m -> m.contains("multiroom.radio." + property));
    }

    @Test
    void defaultsAreValidAndMatchTheContract() {
        RadioProperties p = bind();
        assertThat(messages(p)).isEmpty();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getUserAgent()).isNull();
        assertThat(p.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.getReadTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(p.getResolveTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.getMaxServersPerRequest()).isEqualTo(3);
        assertThat(p.getServers()).isEmpty();
        assertThat(p.getDiscoveryHost()).isEqualTo("all.api.radio-browser.info");
        assertThat(p.getDiscoveryTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(p.getServerListTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(p.getStationCacheTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(p.getStationCacheSize()).isEqualTo(100);
        assertThat(p.isAllowHls()).isTrue();
        assertThat(p.getRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void boundValuesAreUsed() {
        RadioProperties p = bind("enabled", "false", "connect-timeout", "500ms", "allow-hls", "false",
                "servers[0]", "https://de1.api.radio-browser.info", "servers[1]", "http://localhost:8080/");
        assertThat(messages(p)).isEmpty();
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.isAllowHls()).isFalse();
        assertThat(p.getServers()).hasSize(2);
    }

    @Test
    void timeoutsMustBePositive() {
        assertRejected("connect-timeout", "connect-timeout", "0s");
        assertRejected("read-timeout", "read-timeout", "-1s");
        assertRejected("resolve-timeout", "resolve-timeout", "0s");
        assertRejected("discovery-timeout", "discovery-timeout", "0s");
        assertRejected("server-list-ttl", "server-list-ttl", "0s");
        assertRejected("station-cache-ttl", "station-cache-ttl", "0s");
    }

    @Test
    void resolveTimeoutMustCoverTheConnectTimeout() {
        assertRejected("resolve-timeout", "connect-timeout", "5s", "resolve-timeout", "1s");
    }

    @Test
    void numbersMustBeInRange() {
        assertRejected("max-servers-per-request", "max-servers-per-request", "0");
        assertRejected("max-servers-per-request", "max-servers-per-request", "11");
        assertRejected("station-cache-size", "station-cache-size", "0");
        assertRejected("station-cache-size", "station-cache-size", "10001");
        assertThat(messages(bind("max-servers-per-request", "10", "station-cache-size", "10000"))).isEmpty();
    }

    @Test
    void serverEntriesMustBeBareHttpUrls() {
        for (String bad : List.of("ftp://example.test", "https://example.test/json", "https://example.test/?q=1",
                "https://example.test/#f", "/relative", "https:///x")) {
            assertRejected("servers", "servers[0]", bad);
        }
    }

    @Test
    void discoveryHostMustBePlain() {
        for (String bad : List.of("https://all.api.radio-browser.info", "host:8080", "host/path", "", "a b")) {
            assertRejected("discovery-host", "discovery-host", bad);
        }
    }

    @Test
    void userAgentRules() {
        assertRejected("user-agent", "user-agent", " ");
        assertRejected("user-agent", "user-agent", "a\r\nb");
        assertRejected("user-agent", "user-agent", "x".repeat(201));
        assertThat(messages(bind("user-agent", "x".repeat(200)))).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1s", "abc"})
    void requestTimeoutMustBePositiveAndNamedInTheMessage(String value) {
        // "abc" cannot be bound at all; the binder's failure names the property too.
        try {
            assertRejected("request-timeout", "request-timeout", value);
        } catch (org.springframework.boot.context.properties.bind.BindException e) {
            assertThat(e.getMessage()).contains("multiroom.radio.request-timeout");
        }
    }
}
