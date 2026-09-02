package cn.net.rms.syncmatica_r.web.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WebSessionStore {
    public static final Duration DEFAULT_LIFETIME = Duration.ofHours(24);

    private static final int TOKEN_BYTES = 32;

    private final Duration lifetime;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Session> sessions = new HashMap<>();

    public WebSessionStore() {
        this(DEFAULT_LIFETIME, Clock.systemUTC());
    }

    public WebSessionStore(final Duration lifetime, final Clock clock) {
        this(lifetime, clock, new SecureRandom());
    }

    WebSessionStore(
            final Duration lifetime,
            final Clock clock,
            final SecureRandom random
    ) {
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Session lifetime must be positive");
        }
        this.lifetime = lifetime;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized String create(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String token;
        do {
            final byte[] tokenBytes = new byte[TOKEN_BYTES];
            random.nextBytes(tokenBytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        } while (sessions.containsKey(token));
        sessions.put(token, new Session(playerId, clock.instant().plus(lifetime)));
        return token;
    }

    public synchronized Optional<UUID> resolve(final String token) {
        if (token == null) {
            return Optional.empty();
        }
        final Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(session.expiresAt)) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.playerId);
    }

    public synchronized void revoke(final String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public synchronized void revokePlayer(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        sessions.values().removeIf(session -> session.playerId.equals(playerId));
    }

    public synchronized void revokePlayers(final Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds");
        sessions.values().removeIf(session -> playerIds.contains(session.playerId));
    }

    private static final class Session {
        private final UUID playerId;
        private final Instant expiresAt;

        private Session(final UUID playerId, final Instant expiresAt) {
            this.playerId = playerId;
            this.expiresAt = expiresAt;
        }
    }
}
