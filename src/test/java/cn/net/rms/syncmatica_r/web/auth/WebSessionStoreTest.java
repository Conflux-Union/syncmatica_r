package cn.net.rms.syncmatica_r.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WebSessionStoreTest {
    private final MutableClock clock =
            new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void createsRandomOpaqueCookieReadyTokens() {
        final WebSessionStore store = new WebSessionStore(Duration.ofHours(1), clock);
        final UUID player = UUID.randomUUID();

        final String first = store.create(player);
        final String second = store.create(player);

        assertNotEquals(first, second);
        assertFalse(first.contains(player.toString()));
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertEquals(Optional.of(player), store.resolve(first));
        assertEquals(Optional.of(player), store.resolve(second));
    }

    @Test
    void expiresSessionsAtTheConfiguredLifetime() {
        final WebSessionStore store = new WebSessionStore(Duration.ofMinutes(5), clock);
        final String token = store.create(UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));

        assertEquals(Optional.empty(), store.resolve(token));
    }

    @Test
    void revokesOneTokenOnLogout() {
        final WebSessionStore store = new WebSessionStore(Duration.ofHours(1), clock);
        final UUID player = UUID.randomUUID();
        final String loggedOut = store.create(player);
        final String retained = store.create(player);

        store.revoke(loggedOut);

        assertEquals(Optional.empty(), store.resolve(loggedOut));
        assertEquals(Optional.of(player), store.resolve(retained));
    }

    @Test
    void revokesAllSessionsForAPlayer() {
        final WebSessionStore store = new WebSessionStore(Duration.ofHours(1), clock);
        final UUID revokedPlayer = UUID.randomUUID();
        final UUID retainedPlayer = UUID.randomUUID();
        final String firstRevoked = store.create(revokedPlayer);
        final String secondRevoked = store.create(revokedPlayer);
        final String retained = store.create(retainedPlayer);

        store.revokePlayer(revokedPlayer);

        assertEquals(Optional.empty(), store.resolve(firstRevoked));
        assertEquals(Optional.empty(), store.resolve(secondRevoked));
        assertEquals(Optional.of(retainedPlayer), store.resolve(retained));
    }

    @Test
    void revokesSessionsForMultiplePlayersAsOneOperation() {
        final WebSessionStore store = new WebSessionStore(Duration.ofHours(1), clock);
        final UUID previousOwner = UUID.randomUUID();
        final UUID currentOwner = UUID.randomUUID();
        final UUID retainedPlayer = UUID.randomUUID();
        final String previousToken = store.create(previousOwner);
        final String currentToken = store.create(currentOwner);
        final String retainedToken = store.create(retainedPlayer);

        store.revokePlayers(Set.of(previousOwner, currentOwner));

        assertEquals(Optional.empty(), store.resolve(previousToken));
        assertEquals(Optional.empty(), store.resolve(currentToken));
        assertEquals(Optional.of(retainedPlayer), store.resolve(retainedToken));
    }

    @Test
    void sessionsAreNotPersistedAcrossStoreInstances() {
        final WebSessionStore firstStore =
                new WebSessionStore(Duration.ofHours(1), clock);
        final String token = firstStore.create(UUID.randomUUID());

        final WebSessionStore restarted =
                new WebSessionStore(Duration.ofHours(1), clock);

        assertEquals(Optional.empty(), restarted.resolve(token));
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
