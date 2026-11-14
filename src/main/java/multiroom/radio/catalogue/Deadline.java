package multiroom.radio.catalogue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** The end of one resolution's time budget; every network wait is capped by what is left of it. */
public record Deadline(Clock clock, Instant end) {

    /** A deadline {@code budget} from now on the given clock. */
    public static Deadline after(Clock clock, Duration budget) {
        return new Deadline(clock, clock.instant().plus(budget));
    }

    /** What is left, never negative. */
    public Duration remaining() {
        Duration left = Duration.between(clock.instant(), end);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** True when nothing is left. */
    public boolean expired() {
        return remaining().isZero();
    }

    /** The smaller of {@code limit} and what is left. */
    public Duration cap(Duration limit) {
        Duration left = remaining();
        return limit.compareTo(left) < 0 ? limit : left;
    }
}
