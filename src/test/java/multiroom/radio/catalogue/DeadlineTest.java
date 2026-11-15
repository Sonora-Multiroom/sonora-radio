package multiroom.radio.catalogue;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void remainingShrinksWithTheClockAndNeverGoesNegative() {
        MutableClock clock = new MutableClock();
        Deadline deadline = Deadline.after(clock, Duration.ofSeconds(10));
        assertThat(deadline.remaining()).isEqualTo(Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(4));
        assertThat(deadline.remaining()).isEqualTo(Duration.ofSeconds(6));
        clock.advance(Duration.ofSeconds(60));
        assertThat(deadline.remaining()).isEqualTo(Duration.ZERO);
    }

    @Test
    void expiresWhenNothingIsLeft() {
        MutableClock clock = new MutableClock();
        Deadline deadline = Deadline.after(clock, Duration.ofSeconds(1));
        assertThat(deadline.expired()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(deadline.expired()).isTrue();
    }

    @Test
    void capIsTheSmallerOfTheLimitAndWhatRemains() {
        MutableClock clock = new MutableClock();
        Deadline deadline = Deadline.after(clock, Duration.ofSeconds(5));
        assertThat(deadline.cap(Duration.ofSeconds(2))).isEqualTo(Duration.ofSeconds(2));
        assertThat(deadline.cap(Duration.ofSeconds(9))).isEqualTo(Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(4));
        assertThat(deadline.cap(Duration.ofSeconds(2))).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void aDeadlineMadeWithoutAnAttemptLimitHasNone() {
        Deadline deadline = Deadline.after(new MutableClock(), Duration.ofSeconds(5));
        assertThat(deadline.attemptLimit()).isNull();
        assertThat(deadline.attemptCap(Duration.ofSeconds(6))).isEqualTo(Duration.ofSeconds(5));
        assertThat(deadline.attemptCap(Duration.ofSeconds(2))).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void aDeadlineMadeWithAnAttemptLimitReportsIt() {
        Deadline deadline = Deadline.after(new MutableClock(), Duration.ofSeconds(5), Duration.ofMillis(2500));
        assertThat(deadline.attemptLimit()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    void attemptCapIsTheSmallestOfTheConfiguredLimitTheAttemptLimitAndWhatIsLeft() {
        MutableClock clock = new MutableClock();
        Deadline deadline = Deadline.after(clock, Duration.ofSeconds(5), Duration.ofSeconds(2));
        assertThat(deadline.attemptCap(Duration.ofSeconds(6))).isEqualTo(Duration.ofSeconds(2));
        assertThat(deadline.attemptCap(Duration.ofSeconds(1))).isEqualTo(Duration.ofSeconds(1));
        clock.advance(Duration.ofMillis(4500));
        assertThat(deadline.attemptCap(Duration.ofSeconds(6))).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void theAttemptLimitNeverExceedsTheDeadline() {
        MutableClock clock = new MutableClock();
        Deadline deadline = Deadline.after(clock, Duration.ofSeconds(1), Duration.ofSeconds(30));
        assertThat(deadline.attemptCap(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(2));
        assertThat(deadline.attemptCap(Duration.ofSeconds(60))).isEqualTo(Duration.ZERO);
    }
}
