package multiroom.radio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadioVersionTest {

    @Test
    void versionIsFilteredByTheBuild() {
        assertThat(RadioVersion.version()).isNotBlank().isNotEqualTo("${project.version}").isNotEqualTo("unknown");
    }

    @Test
    void defaultUserAgentCarriesTheVersion() {
        assertThat(RadioVersion.defaultUserAgent()).isEqualTo("sonora-radio/" + RadioVersion.version());
    }

    @Test
    void aMissingResourceYieldsUnknownNotAnException() throws Exception {
        ClassLoader empty = new ClassLoader(null) { };
        Class<?> loaded = new java.net.URLClassLoader(new java.net.URL[]{
                RadioVersion.class.getProtectionDomain().getCodeSource().getLocation()}, null) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return null;
            }
        }.loadClass(RadioVersion.class.getName());
        assertThat(loaded.getMethod("defaultUserAgent").invoke(null)).isEqualTo("sonora-radio/unknown");
        assertThat(empty).isNotNull();
    }
}
