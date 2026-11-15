package multiroom.radio.metrics;

import java.time.Duration;

/** Records nothing; used when the host has no meter registry. */
public class NoopRadioMetrics implements RadioMetrics {

    @Override
    public void resolution(String outcome, String serverTag, Duration elapsed) {
        // nothing to record
    }

    @Override
    public void catalogueRequest(String serverTag, String endpoint, String result) {
        // nothing to record
    }

    @Override
    public void discovery(String source, String result) {
        // nothing to record
    }
}
