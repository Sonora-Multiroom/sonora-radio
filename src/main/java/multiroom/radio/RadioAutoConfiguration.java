package multiroom.radio;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import multiroom.api.io.resolve.SupportedCodecRegistry;
import multiroom.radio.catalogue.CachingRadioBrowserClient;
import multiroom.radio.catalogue.CatalogueHttp;
import multiroom.radio.catalogue.HttpRadioBrowserClient;
import multiroom.radio.catalogue.RadioBrowserClient;
import multiroom.radio.catalogue.servers.DiscoveredServerSource;
import multiroom.radio.catalogue.servers.DnsResolver;
import multiroom.radio.catalogue.servers.JdkDnsResolver;
import multiroom.radio.catalogue.servers.PinnedServerSource;
import multiroom.radio.catalogue.servers.ServerSource;
import multiroom.radio.config.RadioProperties;
import multiroom.radio.metrics.MicrometerRadioMetrics;
import multiroom.radio.metrics.NoopRadioMetrics;
import multiroom.radio.metrics.RadioMetrics;
import multiroom.radio.resolve.CodecPolicy;
import multiroom.radio.resolve.RadioStationResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.util.Random;

/**
 * The extension's entry point, named by
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. There is
 * no {@code Extension} interface to implement and no {@code Extension-Class} manifest attribute.
 *
 * <p>{@code matchIfMissing = true} so an upgrade never silently disables a working extension.
 * Core contains no knowledge of this module. Nothing in a constructor or a bean method touches the
 * network or DNS: an unreachable catalogue must never stop the hub starting.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "multiroom.radio.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RadioProperties.class)
public class RadioAutoConfiguration {

    @Bean
    CatalogueHttp catalogueHttp(RadioProperties properties) {
        return new CatalogueHttp(properties);
    }

    /** The JDK's name service; replaceable so a test never touches real DNS. */
    @Bean
    @ConditionalOnMissingBean(DnsResolver.class)
    DnsResolver radioDnsResolver() {
        return new JdkDnsResolver();
    }

    @Bean
    ServerSource radioServerSource(RadioProperties properties, DnsResolver dns, CatalogueHttp http,
                                   ObjectProvider<ObjectMapper> mapper, RadioMetrics metrics) {
        if (!properties.getServers().isEmpty()) {
            return new PinnedServerSource(properties.getServers(), metrics);
        }
        return new DiscoveredServerSource(properties, dns, http, mapper.getIfAvailable(ObjectMapper::new), metrics,
                Clock.systemUTC(), new Random(), URI.create("https://" + properties.getDiscoveryHost()));
    }

    @Bean
    RadioBrowserClient radioBrowserClient(CatalogueHttp http, ServerSource servers, RadioProperties properties,
                                          RadioMetrics metrics, ObjectProvider<ObjectMapper> mapper) {
        RadioBrowserClient http1 = new HttpRadioBrowserClient(http, servers, properties, metrics,
                mapper.getIfAvailable(ObjectMapper::new));
        return new CachingRadioBrowserClient(http1, properties, Clock.systemUTC());
    }

    @Bean
    RadioStationResolver radioStationResolver(RadioBrowserClient client, ObjectProvider<SupportedCodecRegistry> codecs,
                                              RadioProperties properties, RadioMetrics metrics) {
        return new RadioStationResolver(client, new CodecPolicy(codecs), properties, metrics, Clock.systemUTC());
    }

    /**
     * Records into the host's registry when it has one. The registry is resolved through
     * {@code ObjectProvider} when this bean is created, after every bean definition is registered, so
     * no ordering is needed. A host with Micrometer but no registry gets the no-op. The class is named
     * by string so that the auto-configuration itself still loads on a host without Micrometer.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerMetricsConfiguration {

        @Bean
        RadioMetrics radioMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
            MeterRegistry registry = meterRegistry.getIfAvailable();
            return registry == null ? new NoopRadioMetrics() : new MicrometerRadioMetrics(registry);
        }
    }

    /** The no-op for a host without Micrometer on its classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
    static class NoopMetricsConfiguration {

        @Bean
        RadioMetrics radioMetrics() {
            return new NoopRadioMetrics();
        }
    }
}
