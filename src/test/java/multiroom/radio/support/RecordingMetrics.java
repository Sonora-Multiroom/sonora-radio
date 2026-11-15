package multiroom.radio.support;

import multiroom.radio.metrics.RadioMetrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Remembers every call, for assertions. */
public final class RecordingMetrics implements RadioMetrics {

    public record Resolution(String outcome, String server, Duration elapsed) {
    }

    public final List<Resolution> resolutions = new CopyOnWriteArrayList<>();
    public final List<List<String>> requests = new CopyOnWriteArrayList<>();
    public final List<List<String>> discoveries = new CopyOnWriteArrayList<>();

    @Override
    public void resolution(String outcome, String serverTag, Duration elapsed) {
        resolutions.add(new Resolution(outcome, serverTag, elapsed));
    }

    @Override
    public void catalogueRequest(String serverTag, String endpoint, String result) {
        requests.add(List.of(serverTag, endpoint, result));
    }

    @Override
    public void discovery(String source, String result) {
        discoveries.add(List.of(source, result));
    }
}
