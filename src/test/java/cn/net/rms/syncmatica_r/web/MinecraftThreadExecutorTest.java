package cn.net.rms.syncmatica_r.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

final class MinecraftThreadExecutorTest {
    @Test
    void schedulesWorkOnTheSuppliedServerExecutor() {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        final MinecraftThreadExecutor executor =
                new MinecraftThreadExecutor(tasks::add, Duration.ofSeconds(1));

        final java.util.concurrent.CompletableFuture<String> result =
                executor.submit(() -> "snapshot");

        assertEquals(1, tasks.size());
        assertTrue(!result.isDone());
        tasks.remove().run();
        assertEquals("snapshot", result.join());
    }

    @Test
    void timesOutWorkThatDoesNotRun() {
        final MinecraftThreadExecutor executor =
                new MinecraftThreadExecutor(ignored -> { }, Duration.ofMillis(20));

        final CompletionException failure =
                assertThrows(CompletionException.class, () -> executor.submit(() -> "late").join());

        assertInstanceOf(TimeoutException.class, failure.getCause());
    }

    @Test
    void rejectsNewWorkAfterShutdown() {
        final MinecraftThreadExecutor executor =
                new MinecraftThreadExecutor(Runnable::run, Duration.ofSeconds(1));

        executor.shutdown();

        final CompletionException failure =
                assertThrows(CompletionException.class, () -> executor.submit(() -> "late").join());
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
    }

    @Test
    void shutdownRejectsWorkAlreadyWaitingForTheServerThread() {
        final MinecraftThreadExecutor executor =
                new MinecraftThreadExecutor(ignored -> { }, Duration.ofMillis(20));
        final java.util.concurrent.CompletableFuture<String> pending =
                executor.submit(() -> "late");

        executor.shutdown();

        final CompletionException failure =
                assertThrows(CompletionException.class, pending::join);
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
    }

    @Test
    void preservesTaskFailures() throws Exception {
        final MinecraftThreadExecutor executor =
                new MinecraftThreadExecutor(Runnable::run, Duration.ofSeconds(1));

        final java.util.concurrent.ExecutionException failure =
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> executor.submit(() -> {
                            throw new IllegalStateException("boom");
                        }).get(1, TimeUnit.SECONDS));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("boom", failure.getCause().getMessage());
    }
}
