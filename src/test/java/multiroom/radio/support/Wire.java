package multiroom.radio.support;

import java.io.IOException;
import java.net.ServerSocket;

/** Test helpers for local ports. */
public final class Wire {

    private Wire() {
    }

    /** Builds a JDK HTTP client once, so the JVM's one-off set-up cost is not charged to a short timeout in a test. */
    public static void warmUp() {
        java.net.http.HttpClient.newHttpClient();
    }

    /** A port nothing listens on. */
    public static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
