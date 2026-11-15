package multiroom.radio;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The extension's own version, read from the build-filtered {@code multiroom/radio/radio-version.properties}.
 * Under the hub's shared classloader the manifest cannot be trusted to be this JAR's, so the resource
 * lives in this module's package and is read relative to it.
 */
public final class RadioVersion {

    private static final String UNKNOWN = "unknown";

    private RadioVersion() {
    }

    /** The version, or {@code unknown} when the resource is missing or unreadable. */
    public static String version() {
        try (InputStream in = RadioVersion.class.getResourceAsStream("radio-version.properties")) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            return version == null || version.isBlank() ? UNKNOWN : version.trim();
        } catch (IOException | IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** {@code sonora-radio/<version>}; says nothing about the owner or the home. */
    public static String defaultUserAgent() {
        return "sonora-radio/" + version();
    }
}
