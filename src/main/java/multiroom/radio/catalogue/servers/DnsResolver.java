package multiroom.radio.catalogue.servers;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

/** The DNS lookups discovery needs, behind an interface so tests never touch real DNS. */
public interface DnsResolver {

    /** Forward lookup. */
    List<InetAddress> lookup(String host);

    /** Reverse lookup; empty when the address has no name. */
    Optional<String> reverse(InetAddress address);
}
