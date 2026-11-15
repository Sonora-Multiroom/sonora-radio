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

    /** A station with the fields a play needs and neutral values for the rest. */
    public static multiroom.radio.catalogue.Station station(UUID uuid, String name, String streamUrl, String codec,
                                                            boolean hls) {
        return new multiroom.radio.catalogue.Station(uuid, name, streamUrl, codec, hls,
                null, null, null, null, null, 0, true, 0, 0);
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
