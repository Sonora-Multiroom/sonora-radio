package multiroom.radio;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import multiroom.radio.catalogue.CachingRadioBrowserClient;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.servers.DiscoveredServerSource;
import multiroom.radio.catalogue.servers.DnsResolver;
import multiroom.radio.catalogue.servers.PinnedServerSource;
import multiroom.radio.catalogue.servers.ServerSource;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.MicrometerRadioMetrics;
import multiroom.radio.metrics.NoopRadioMetrics;
import multiroom.radio.metrics.RadioMetrics;
import multiroom.radio.resolve.RadioStationResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.assertj.core.api.Assertions.assertThat;

class RadioAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RadioAutoConfiguration.class));

    @Test
    void defaultContextContainsTheAutoConfiguration() {
        runner.run(context -> assertThat(context).hasSingleBean(RadioAutoConfiguration.class));
    }

    @Test
    void disabledContextContributesNothing() {
        runner.withPropertyValues("multiroom.radio.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RadioAutoConfiguration.class));
    }

    @Test
    void defaultContextHasPropertiesAndMetrics() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RadioProperties.class);
            assertThat(context).hasSingleBean(RadioMetrics.class);
            assertThat(context.getBean(RadioMetrics.class)).isInstanceOf(NoopRadioMetrics.class);
        });
    }

    @Test
    void disabledContextHasNeitherPropertiesNorMetrics() {
        runner.withPropertyValues("multiroom.radio.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RadioProperties.class);
            assertThat(context).doesNotHaveBean(RadioMetrics.class);
        });
    }

    @Test
    void malformedConfigurationFailsStartUpNamingThePropertyAndPrefix() {
        for (String bad : new String[]{"multiroom.radio.servers[0]=not a url", "multiroom.radio.connect-timeout=0s",
                "multiroom.radio.max-servers-per-request=11"}) {
            runner.withPropertyValues(bad).run(context -> {
                assertThat(context).hasFailed();
                String property = bad.substring(0, bad.indexOf('='));
                String text = flatten(context.getStartupFailure());
                assertThat(text).contains("multiroom.radio");
                assertThat(text).containsIgnoringCase(property.substring("multiroom.radio.".length()).replaceAll("\\[0]", ""));
            });
        }
    }

    private static String flatten(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void defaultContextWiresTheResolverTheClientAndADiscoveredServerSource() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RadioStationResolver.class);
            assertThat(context).hasSingleBean(RadioBrowserClient.class);
            assertThat(context.getBean(RadioBrowserClient.class)).isInstanceOf(CachingRadioBrowserClient.class);
            assertThat(context).hasSingleBean(ServerSource.class);
            assertThat(context.getBean(ServerSource.class)).isInstanceOf(DiscoveredServerSource.class);
        });
    }

    @Test
    void pinnedServersSelectThePinnedSource() {
        runner.withPropertyValues("multiroom.radio.servers[0]=https://de1.api.radio-browser.info")
                .run(context -> assertThat(context.getBean(ServerSource.class)).isInstanceOf(PinnedServerSource.class));
    }

    @Test
    void disabledContextHasNoResolverClientOrServerSource() {
        runner.withPropertyValues("multiroom.radio.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RadioStationResolver.class);
            assertThat(context).doesNotHaveBean(RadioBrowserClient.class);
            assertThat(context).doesNotHaveBean(ServerSource.class);
        });
    }

    @Test
    void startingWithAPinnedServerContactsNothing() {
        WireMockServer wire = new WireMockServer(options().dynamicPort());
        wire.start();
        try {
            runner.withPropertyValues("multiroom.radio.servers[0]=http://localhost:" + wire.port())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(RadioStationResolver.class)).isNotNull();
                    });
            assertThat(wire.getAllServeEvents()).isEmpty();
        } finally {
            wire.stop();
        }
    }

    @Test
    void startingWithDiscoveryMakesNoDnsLookup() {
        AtomicInteger lookups = new AtomicInteger();
        DnsResolver counting = new DnsResolver() {
            @Override
            public List<InetAddress> lookup(String host) {
                lookups.incrementAndGet();
                return List.of();
            }

            @Override
            public Optional<String> reverse(InetAddress address) {
                lookups.incrementAndGet();
                return Optional.empty();
            }
        };
        runner.withBean(DnsResolver.class, () -> counting).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RadioStationResolver.class)).isNotNull();
            assertThat(context.getBean(DnsResolver.class)).isSameAs(counting);
        });
        assertThat(lookups).hasValue(0);
    }

    @Test
    void withoutAMeterRegistryTheMetricsAreNoop() {
        runner.run(context -> assertThat(context.getBean(RadioMetrics.class)).isInstanceOf(NoopRadioMetrics.class));
    }

    @Test
    void withAMeterRegistryTheMetricsAreMicrometer() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context.getBean(RadioMetrics.class)).isInstanceOf(MicrometerRadioMetrics.class));
    }

    @Test
    void withoutMicrometerOnTheClasspathTheContextStillStartsWithTheNoop() {
        runner.withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RadioMetrics.class)).isInstanceOf(NoopRadioMetrics.class);
            assertThat(context.getBean(RadioStationResolver.class)).isNotNull();
        });
    }
}
