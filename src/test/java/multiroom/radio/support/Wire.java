package multiroom.radio.support;

import java.io.IOException;
import java.net.ServerSocket;

/** Test helpers for local ports. */
public final class Wire {

    private Wire() {
    }

    /**
     * Sends one real request through a JDK HTTP client to a throwaway WireMock, so the JVM's one-off
     * set-up cost (client, selector, WireMock's first request) is not charged to a short timeout in a test.
     */
    public static void warmUp() {
        com.github.tomakehurst.wiremock.WireMockServer server = new com.github.tomakehurst.wiremock.WireMockServer(
                com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());
        try {
            server.start();
            server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                    com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/warm-up"))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withBody("[]")));
            java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                            "http://localhost:" + server.port() + "/warm-up")).build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // warming up is best effort
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop();
        }
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
