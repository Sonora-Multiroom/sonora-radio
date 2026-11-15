package multiroom.radio.resolve;

import multiroom.api.io.resolve.SupportedCodecRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodecPolicyTest {

    private static CodecPolicy policy(Set<String> supported) {
        SupportedCodecRegistry registry = new SupportedCodecRegistry() {
            @Override
            public Set<String> getSupportedCodecNames() {
                return supported;
            }

            @Override
            public boolean isSupported(String codecName) {
                return supported.contains(codecName);
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<SupportedCodecRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return new CodecPolicy(provider);
    }

    @Test
    void mappedCodecsTheRegistryAcceptsAreAllowed() {
        CodecPolicy policy = policy(Set.of("mp3", "aac", "opus", "flac"));
        for (String label : new String[]{"MP3", " mp3 ", "AAC", "AAC+", "aac+", "OPUS", "FLAC"}) {
            assertThat(policy.refusal(label)).as(label).isEmpty();
        }
    }

    @Test
    void aMappedCodecTheRegistryRejectsIsRefusedNamingTheLabel() {
        CodecPolicy policy = policy(Set.of("mp3"));
        assertThat(policy.refusal("AAC+")).contains("AAC+");
        assertThat(policy.refusal("flac")).contains("flac");
        assertThat(policy.refusal("OPUS")).contains("OPUS");
        assertThat(policy.refusal("MP3")).isEmpty();
    }

    @Test
    void unmappedLabelsAreAllowed() {
        CodecPolicy policy = policy(Set.of());
        for (String label : new String[]{"OGG", "UNKNOWN", "", "  ", "WMA", null}) {
            assertThat(policy.refusal(label)).as(String.valueOf(label)).isEmpty();
        }
    }

    @Test
    void noRegistryMeansAllowed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SupportedCodecRegistry> empty = mock(ObjectProvider.class);
        assertThat(new CodecPolicy(empty).refusal("AAC")).isEmpty();
    }
}
