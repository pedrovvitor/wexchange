package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A test {@link Clock} whose instant can be advanced deterministically, for exercising token-bucket refill. */
final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(final Instant instant) {
        this.instant = instant;
    }

    void advance(final Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
