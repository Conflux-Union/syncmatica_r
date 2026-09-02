package cn.net.rms.syncmatica_r.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebAuthenticationCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    private final ExecutorService authenticationExecutor = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopExecutor() {
        authenticationExecutor.shutdownNow();
    }

    @Test
    void passwordChangeOrdersAfterConcurrentOldPasswordLoginAndRevokesItsSession()
            throws Exception {
        final UUID player = UUID.randomUUID();
        final WebCredentialStore credentials = credentials();
        credentials.set(player, "Alice", "old-password".toCharArray());
        final WebSessionStore sessions = sessions();
        final BlockingHook hook = new BlockingHook();
        final WebAuthenticationCoordinator coordinator =
                new WebAuthenticationCoordinator(credentials, sessions, hook);

        final Future<Optional<String>> login = authenticationExecutor.submit(
                () -> coordinator.authenticateAndCreateSession(
                        "Alice", "old-password".toCharArray()));
        hook.awaitAuthenticated();
        final CountDownLatch updateStarted = new CountDownLatch(1);
        final Future<?> update = authenticationExecutor.submit(() -> {
            updateStarted.countDown();
            coordinator.set(player, "Alice", "new-password".toCharArray());
            return null;
        });
        updateStarted.await();
        assertThrows(TimeoutException.class, () -> update.get(100, TimeUnit.MILLISECONDS));

        hook.release();
        final String staleToken = login.get().orElseThrow(AssertionError::new);
        update.get();

        assertEquals(Optional.empty(), sessions.resolve(staleToken));
        assertEquals(Optional.empty(), coordinator.authenticateAndCreateSession(
                "Alice", "old-password".toCharArray()));
        assertFalse(coordinator.authenticateAndCreateSession(
                "Alice", "new-password".toCharArray()).isEmpty());
    }

    @Test
    void disableOrdersAfterConcurrentLoginAndRevokesItsSession() throws Exception {
        final UUID player = UUID.randomUUID();
        final WebCredentialStore credentials = credentials();
        credentials.set(player, "Alice", "old-password".toCharArray());
        final WebSessionStore sessions = sessions();
        final BlockingHook hook = new BlockingHook();
        final WebAuthenticationCoordinator coordinator =
                new WebAuthenticationCoordinator(credentials, sessions, hook);

        final Future<Optional<String>> login = authenticationExecutor.submit(
                () -> coordinator.authenticateAndCreateSession(
                        "Alice", "old-password".toCharArray()));
        hook.awaitAuthenticated();
        final CountDownLatch disableStarted = new CountDownLatch(1);
        final Future<?> disable = authenticationExecutor.submit(() -> {
            disableStarted.countDown();
            coordinator.disable(player);
            return null;
        });
        disableStarted.await();
        assertThrows(TimeoutException.class, () -> disable.get(100, TimeUnit.MILLISECONDS));

        hook.release();
        final String staleToken = login.get().orElseThrow(AssertionError::new);
        disable.get();

        assertEquals(Optional.empty(), sessions.resolve(staleToken));
        assertEquals(Optional.empty(), coordinator.authenticateAndCreateSession(
                "Alice", "old-password".toCharArray()));
    }

    @Test
    void nameTransferRevokesPreviousAndCurrentOwnersSessions() throws Exception {
        final UUID previousOwner = UUID.randomUUID();
        final UUID currentOwner = UUID.randomUUID();
        final WebCredentialStore credentials = credentials();
        credentials.set(previousOwner, "Alice", "old-password".toCharArray());
        final WebSessionStore sessions = sessions();
        final String previousToken = sessions.create(previousOwner);
        final String currentToken = sessions.create(currentOwner);
        final WebAuthenticationCoordinator coordinator =
                new WebAuthenticationCoordinator(credentials, sessions);

        coordinator.set(currentOwner, "ALICE", "new-password".toCharArray());

        assertEquals(Optional.empty(), sessions.resolve(previousToken));
        assertEquals(Optional.empty(), sessions.resolve(currentToken));
        assertEquals(Optional.empty(), coordinator.authenticateAndCreateSession(
                "Alice", "old-password".toCharArray()));
        assertFalse(coordinator.authenticateAndCreateSession(
                "Alice", "new-password".toCharArray()).isEmpty());
    }

    private WebCredentialStore credentials() {
        return new WebCredentialStore(
                temporaryDirectory.resolve("web-credentials.json"),
                new WebPasswordHasher(1_000));
    }

    private static WebSessionStore sessions() {
        return new WebSessionStore(Duration.ofHours(1), Clock.systemUTC());
    }

    private static final class BlockingHook implements Runnable {
        private final CountDownLatch authenticated = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void run() {
            authenticated.countDown();
            try {
                release.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private void awaitAuthenticated() throws InterruptedException {
            authenticated.await();
        }

        private void release() {
            release.countDown();
        }
    }
}
