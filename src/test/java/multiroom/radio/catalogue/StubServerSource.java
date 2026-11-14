package multiroom.radio.catalogue;

import multiroom.radio.catalogue.servers.CatalogueServer;
import multiroom.radio.catalogue.servers.ServerSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** A fixed server list that remembers what it was told. */
final class StubServerSource implements ServerSource {

    final List<CatalogueServer> servers;
    final List<CatalogueServer> answered = new ArrayList<>();
    int allFailedCalls;

    StubServerSource(String... baseUris) {
        this.servers = java.util.Arrays.stream(baseUris).map(u -> CatalogueServer.of(URI.create(u))).toList();
    }

    @Override
    public List<CatalogueServer> servers(Deadline deadline) {
        return servers;
    }

    @Override
    public void answered(CatalogueServer server) {
        answered.add(server);
    }

    @Override
    public void allFailed() {
        allFailedCalls++;
    }
}
