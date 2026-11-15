package multiroom.radio.resolve;

import multiroom.radio.text.Text;
import multiroom.api.io.resolve.SupportedCodecRegistry;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether the hub can decode a station's codec. radio-browser's free-form label is mapped to
 * a hub codec name and checked with the hub's {@link SupportedCodecRegistry}; anything unmapped, and a
 * host without a registry, is let through because the decoder detects the format from the stream.
 */
public class CodecPolicy {

    private static final Map<String, String> MAPPED = Map.of(
            "MP3", "mp3", "AAC", "aac", "AAC+", "aac", "OPUS", "opus", "FLAC", "flac");

    private final ObjectProvider<SupportedCodecRegistry> registry;

    /** Creates the policy; the registry is looked up at each decision, never at construction. */
    public CodecPolicy(ObjectProvider<SupportedCodecRegistry> registry) {
        this.registry = registry;
    }

    /** The label to name in the refusal, or empty when the station may play. */
    public Optional<String> refusal(String codecLabel) {
        if (codecLabel == null) {
            return Optional.empty();
        }
        String hubName = MAPPED.get(codecLabel.trim().toUpperCase(Locale.ROOT));
        if (hubName == null) {
            return Optional.empty();
        }
        SupportedCodecRegistry codecs = registry.getIfAvailable();
        if (codecs == null || codecs.isSupported(hubName)) {
            return Optional.empty();
        }
        return Optional.of(Text.bounded(codecLabel.trim(), 32));
    }
}
