package multiroom.radio.catalogue.servers;

import multiroom.radio.catalogue.Deadline;
import multiroom.radio.metrics.RadioMetrics;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The servers the operator pinned in the configuration, always tried in the order given. A pinned
 * list never expires, is never rotated and is never discovered again.
 */
public class PinnedServerSource implements ServerSource {

    private final List<CatalogueServer> servers;
    private final RadioMetrics metrics;
    private final AtomicBoolean reported = new AtomicBoolean();

    /** Creates the source from the configured URLs. */
    public PinnedServerSource(List<URI> configured, RadioMetrics metrics) {
        this.servers = configured.stream().map(CatalogueServer::of).toList();
        this.metrics = metrics;
    }

    @Override
    public List<CatalogueServer> servers(Deadline deadline) {
        if (reported.compareAndSet(false, true)) {
            metrics.discovery(RadioMetrics.SOURCE_PINNED, RadioMetrics.DISCOVERY_OK);
        }
        return servers;
    }

    @Override
    public void answered(CatalogueServer server) {
        // a pinned list is never rotated
    }

    @Override
    public void allFailed() {
        // a pinned list is never discovered again
    }
}
