package multiroom.radio.catalogue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The end of one request's time budget; every network wait is capped by what is left of it.
 *
 * <p>A deadline may also carry an <em>attempt limit</em>: the longest one attempt on one server may
 * wait, so that a slow first mirror never leaves the second nothing. Plays carry none and behave as
 * they always did; the REST API sets half of its budget.
 *
 * @param clock        the clock the budget is measured on
 * @param end          when the budget ends
 * @param attemptLimit the longest wait for one attempt, or {@code null} for none
 */
public record Deadline(Clock clock, Instant end, Duration attemptLimit) {

    /** A deadline {@code budget} from now on the given clock, with no attempt limit. */
    public static Deadline after(Clock clock, Duration budget) {
        return new Deadline(clock, clock.instant().plus(budget), null);
    }

    /** A deadline {@code budget} from now with each attempt limited to {@code attemptLimit}. */
    public static Deadline after(Clock clock, Duration budget, Duration attemptLimit) {
        return new Deadline(clock, clock.instant().plus(budget), attemptLimit);
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

    /**
     * How long one attempt may wait: the smallest of the configured limit, the attempt limit (when
     * there is one) and what is left of the deadline.
     */
    public Duration attemptCap(Duration configured) {
        Duration limit = attemptLimit != null && attemptLimit.compareTo(configured) < 0 ? attemptLimit : configured;
        return cap(limit);
    }
}
