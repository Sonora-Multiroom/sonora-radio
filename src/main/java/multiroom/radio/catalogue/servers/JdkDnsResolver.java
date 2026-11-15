package multiroom.radio.catalogue.servers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

/** The JDK's name service. It has no timeout of its own: callers bound the wait. */
public class JdkDnsResolver implements DnsResolver {

    @Override
    public List<InetAddress> lookup(String host) {
        try {
            return List.of(InetAddress.getAllByName(host));
        } catch (UnknownHostException e) {
            return List.of();
        }
    }

    @Override
    public Optional<String> reverse(InetAddress address) {
        String name = address.getCanonicalHostName();
        return name == null || name.equals(address.getHostAddress()) ? Optional.empty() : Optional.of(name);
    }
}
