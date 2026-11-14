package multiroom.radio.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Hand-written radio-browser responses under {@code src/test/resources/radiobrowser/}. */
public final class Fixtures {

    public static final UUID STATION = UUID.fromString("960e57c5-0601-11e8-ae97-52543be04c81");

    private Fixtures() {
    }

    public static String load(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/radiobrowser/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
