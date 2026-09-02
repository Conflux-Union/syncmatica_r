package cn.net.rms.syncmatica_r.web;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class MinecraftThreadExecutor {
    private final Executor serverExecutor;
    private final Duration timeout;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final java.util.Set<CompletableFuture<?>> pending =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MinecraftThreadExecutor(final Executor serverExecutor, final Duration timeout) {
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    public <T> CompletableFuture<T> submit(final Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        final CompletableFuture<T> result = new CompletableFuture<>();
        if (shutdown.get()) {
            result.completeExceptionally(new RejectedExecutionException("Minecraft executor is shut down"));
            return result;
        }
        pending.add(result);
        result.whenComplete((value, failure) -> pending.remove(result));
        if (shutdown.get()) {
            result.completeExceptionally(new RejectedExecutionException("Minecraft executor is shut down"));
            return result;
        }
        try {
            serverExecutor.execute(() -> {
                if (result.isDone()) {
                    return;
                }
                try {
                    result.complete(task.get());
                } catch (final Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (final RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            for (final CompletableFuture<?> result : pending) {
                result.completeExceptionally(
                        new RejectedExecutionException("Minecraft executor is shut down"));
            }
        }
    }
}
