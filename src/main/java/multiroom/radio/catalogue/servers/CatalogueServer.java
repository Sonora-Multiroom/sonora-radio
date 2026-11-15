package multiroom.radio.catalogue.servers;

import java.net.URI;

/**
 * One catalogue server.
 *
 * @param baseUri where requests are sent
 * @param tag     the host, plus {@code :port} when not the scheme's default; used in logs and metric tags
 */
public record CatalogueServer(URI baseUri, String tag) {

    /** Builds a server from its base URI, deriving the tag. */
    public static CatalogueServer of(URI baseUri) {
        int port = baseUri.getPort();
        String scheme = baseUri.getScheme() == null ? "" : baseUri.getScheme().toLowerCase();
        boolean defaultPort = port < 0 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        return new CatalogueServer(baseUri, defaultPort ? baseUri.getHost() : baseUri.getHost() + ":" + port);
    }
}
