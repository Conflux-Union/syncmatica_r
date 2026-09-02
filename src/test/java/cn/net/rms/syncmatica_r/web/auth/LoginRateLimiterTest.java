package cn.net.rms.syncmatica_r.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

final class LoginRateLimiterTest {
    private final MutableClock clock =
            new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void throttlesAttemptsSharedByTheSameIp() {
        final LoginRateLimiter limiter = limiter(2, 20);

        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));
        assertTrue(limiter.tryAcquire("192.0.2.1", "Bob"));
        assertFalse(limiter.tryAcquire("192.0.2.1", "Carol"));
        assertTrue(limiter.tryAcquire("192.0.2.2", "Carol"));
    }

    @Test
    void throttlesAttemptsSharedByPlayerNameCaseInsensitively() {
        final LoginRateLimiter limiter = limiter(2, 20);

        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));
        assertTrue(limiter.tryAcquire("192.0.2.2", "ALICE"));
        assertFalse(limiter.tryAcquire("192.0.2.3", "alice"));
        assertTrue(limiter.tryAcquire("192.0.2.3", "Bob"));
    }

    @Test
    void permitsAttemptsAgainAfterTheWindow() {
        final LoginRateLimiter limiter = limiter(1, 20);
        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));
        assertFalse(limiter.tryAcquire("192.0.2.1", "Alice"));

        clock.advance(Duration.ofMinutes(5));

        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));
    }

    @Test
    void capsTrackedKeysAndFailsClosedForUnknownKeys() {
        final LoginRateLimiter limiter = limiter(5, 2);
        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));

        assertFalse(limiter.tryAcquire("192.0.2.2", "Bob"));
        assertEquals(2, limiter.trackedKeyCount());
        assertTrue(limiter.tryAcquire("192.0.2.1", "Alice"));
    }

    private LoginRateLimiter limiter(final int attempts, final int maxKeys) {
        return new LoginRateLimiter(
                attempts, Duration.ofMinutes(5), maxKeys, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
