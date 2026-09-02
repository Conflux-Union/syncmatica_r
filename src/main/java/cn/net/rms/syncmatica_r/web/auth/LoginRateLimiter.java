package cn.net.rms.syncmatica_r.web.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class LoginRateLimiter {
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    public static final int DEFAULT_MAX_KEYS = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final int maxKeys;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new HashMap<>();

    public LoginRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW, DEFAULT_MAX_KEYS, Clock.systemUTC());
    }

    public LoginRateLimiter(
            final int maxAttempts,
            final Duration window,
            final int maxKeys,
            final Clock clock
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Maximum attempts must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit window must be positive");
        }
        if (maxKeys < 2) {
            throw new IllegalArgumentException("Maximum tracked keys must be at least two");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxKeys = maxKeys;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean tryAcquire(final String ipAddress, final String playerName) {
        Objects.requireNonNull(ipAddress, "ipAddress");
        Objects.requireNonNull(playerName, "playerName");
        if (ipAddress.isEmpty() || playerName.isEmpty()) {
            throw new IllegalArgumentException("Rate limit keys must not be empty");
        }

        final Instant now = clock.instant();
        final Instant cutoff = now.minus(window);
        discardExpired(cutoff);

        final String ipKey = "ip:" + ipAddress;
        final String playerKey = "player:" + playerName.toLowerCase(Locale.ROOT);
        final int newKeys = (attempts.containsKey(ipKey) ? 0 : 1)
                + (attempts.containsKey(playerKey) ? 0 : 1);
        if (attempts.size() + newKeys > maxKeys) {
            return false;
        }

        final Deque<Instant> ipAttempts = attempts.get(ipKey);
        final Deque<Instant> playerAttempts = attempts.get(playerKey);
        if ((ipAttempts != null && ipAttempts.size() >= maxAttempts)
                || (playerAttempts != null && playerAttempts.size() >= maxAttempts)) {
            return false;
        }

        attempts.computeIfAbsent(ipKey, ignored -> new ArrayDeque<>()).addLast(now);
        attempts.computeIfAbsent(playerKey, ignored -> new ArrayDeque<>()).addLast(now);
        return true;
    }

    synchronized int trackedKeyCount() {
        return attempts.size();
    }

    private void discardExpired(final Instant cutoff) {
        final Iterator<Deque<Instant>> iterator = attempts.values().iterator();
        while (iterator.hasNext()) {
            final Deque<Instant> keyAttempts = iterator.next();
            while (!keyAttempts.isEmpty() && !keyAttempts.peekFirst().isAfter(cutoff)) {
                keyAttempts.removeFirst();
            }
            if (keyAttempts.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
