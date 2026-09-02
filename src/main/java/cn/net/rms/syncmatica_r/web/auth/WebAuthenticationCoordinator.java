package cn.net.rms.syncmatica_r.web.auth;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orders credential checks, credential changes, and session mutations.
 * Password operations are CPU intensive; callers must invoke this class from
 * an authentication executor rather than the Minecraft main thread.
 */
public final class WebAuthenticationCoordinator {
    private final WebCredentialStore credentials;
    private final WebSessionStore sessions;
    private final Runnable authenticatedHook;
    private final Object lock = new Object();

    public WebAuthenticationCoordinator(
            final WebCredentialStore credentials,
            final WebSessionStore sessions
    ) {
        this(credentials, sessions, () -> { });
    }

    WebAuthenticationCoordinator(
            final WebCredentialStore credentials,
            final WebSessionStore sessions,
            final Runnable authenticatedHook
    ) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authenticatedHook = Objects.requireNonNull(authenticatedHook, "authenticatedHook");
    }

    public Optional<String> authenticateAndCreateSession(
            final String currentName,
            final char[] password
    ) {
        synchronized (lock) {
            final Optional<UUID> playerId = credentials.authenticate(currentName, password);
            if (playerId.isEmpty()) {
                return Optional.empty();
            }
            authenticatedHook.run();
            return Optional.of(sessions.create(playerId.get()));
        }
    }

    public void set(
            final UUID playerId,
            final String currentName,
            final char[] password
    ) throws IOException {
        synchronized (lock) {
            final Optional<UUID> displacedPlayer =
                    credentials.set(playerId, currentName, password);
            final Set<UUID> revokedPlayers = new LinkedHashSet<>();
            revokedPlayers.add(playerId);
            displacedPlayer.ifPresent(revokedPlayers::add);
            sessions.revokePlayers(revokedPlayers);
        }
    }

    public void disable(final UUID playerId) throws IOException {
        synchronized (lock) {
            credentials.disable(playerId);
            sessions.revokePlayer(playerId);
        }
    }
}
