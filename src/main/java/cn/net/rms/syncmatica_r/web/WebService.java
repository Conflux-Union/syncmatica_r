package cn.net.rms.syncmatica_r.web;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.service.IService;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.web.auth.LoginRateLimiter;
import cn.net.rms.syncmatica_r.web.auth.WebAuthenticationCoordinator;
import cn.net.rms.syncmatica_r.web.auth.WebCredentialStore;
import cn.net.rms.syncmatica_r.web.auth.WebSessionStore;
import io.undertow.Undertow;
import io.undertow.server.handlers.GracefulShutdownHandler;
import java.time.Duration;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public final class WebService implements IService {
    static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    static final int DEFAULT_PORT = 8080;
    static final int DEFAULT_SESSION_HOURS = 24;
    static final int DEFAULT_MAX_REQUEST_BYTES = 65_536;
    static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 10;

    private Context context;
    private boolean enabled;
    private String bindAddress = DEFAULT_BIND_ADDRESS;
    private int port = DEFAULT_PORT;
    private int sessionHours = DEFAULT_SESSION_HOURS;
    private boolean secureCookie;
    private int maxRequestBytes = DEFAULT_MAX_REQUEST_BYTES;
    private int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;
    private Undertow server;
    private GracefulShutdownHandler gracefulShutdown;
    private ThreadPoolExecutor authenticationExecutor;
    private MinecraftThreadExecutor minecraftExecutor;
    private WebCredentialStore credentialStore;
    private WebSessionStore sessionStore;
    private WebAuthenticationCoordinator authenticationCoordinator;

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(final Context context) {
        this.context = context;
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        configuration.saveBoolean("enabled", false);
        configuration.saveString("bind_address", DEFAULT_BIND_ADDRESS);
        configuration.saveInteger("port", DEFAULT_PORT);
        configuration.saveInteger("session_hours", DEFAULT_SESSION_HOURS);
        configuration.saveBoolean("secure_cookie", false);
        configuration.saveInteger("max_request_bytes", DEFAULT_MAX_REQUEST_BYTES);
        configuration.saveInteger("request_timeout_seconds", DEFAULT_REQUEST_TIMEOUT_SECONDS);
    }

    @Override
    public String getConfigKey() {
        return "web";
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", value -> enabled = value);
        configuration.loadString("bind_address", value -> bindAddress = validAddress(value)
                ? value : rewriteString(configuration, "bind_address", DEFAULT_BIND_ADDRESS));
        configuration.loadInteger("port", value -> port = validRange(value, 1, 65_535)
                ? value : rewriteInteger(configuration, "port", DEFAULT_PORT));
        configuration.loadInteger("session_hours", value -> sessionHours = validRange(value, 1, 8_760)
                ? value : rewriteInteger(configuration, "session_hours", DEFAULT_SESSION_HOURS));
        configuration.loadBoolean("secure_cookie", value -> secureCookie = value);
        configuration.loadInteger("max_request_bytes", value -> maxRequestBytes = validRange(value, 1_024, 1_048_576)
                ? value : rewriteInteger(configuration, "max_request_bytes", DEFAULT_MAX_REQUEST_BYTES));
        configuration.loadInteger("request_timeout_seconds",
                value -> requestTimeoutSeconds = validRange(value, 1, 120)
                        ? value : rewriteInteger(configuration, "request_timeout_seconds",
                        DEFAULT_REQUEST_TIMEOUT_SECONDS));
    }

    private static int rewriteInteger(final IServiceConfiguration configuration,
                                      final String key, final int value) {
        configuration.reportError();
        configuration.replaceInteger(key, value);
        return value;
    }

    private static String rewriteString(final IServiceConfiguration configuration,
                                        final String key, final String value) {
        configuration.reportError();
        configuration.replaceString(key, value);
        return value;
    }

    private static boolean validAddress(final String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean validRange(final int value, final int minimum, final int maximum) {
        return value >= minimum && value <= maximum;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getPort() {
        return port;
    }

    public int getSessionHours() {
        return sessionHours;
    }

    public boolean isSecureCookie() {
        return secureCookie;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    @Override
    public synchronized void startup() {
        if (!enabled || server != null) {
            return;
        }
        if (context == null || !context.isServer()) {
            throw new IllegalStateException("Web service requires a server context");
        }
        final MinecraftServer minecraftServer = context.getMinecraftServer();
        if (minecraftServer == null) {
            throw new IllegalStateException("Minecraft server is not attached");
        }
        try {
            authenticationExecutor = new ThreadPoolExecutor(
                    2, 2, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(32),
                    namedDaemonFactory("syncmatica-web-auth"),
                    new ThreadPoolExecutor.AbortPolicy());
            minecraftExecutor = new MinecraftThreadExecutor(
                    minecraftServer::execute, Duration.ofSeconds(requestTimeoutSeconds));
            credentialStore = new WebCredentialStore(context);
            sessionStore = new WebSessionStore(
                    Duration.ofHours(sessionHours), java.time.Clock.systemUTC());
            authenticationCoordinator =
                    new WebAuthenticationCoordinator(credentialStore, sessionStore);
            final WebRouter router = new WebRouter(
                    new WebFacade(context, dimension -> isDimensionLoaded(minecraftServer, dimension)),
                    authenticationCoordinator,
                    sessionStore,
                    new LoginRateLimiter(),
                    minecraftExecutor,
                    authenticationExecutor,
                    Permissions::check,
                    maxRequestBytes,
                    secureCookie,
                    sessionHours,
                    requestTimeoutSeconds);
            gracefulShutdown = new GracefulShutdownHandler(router.handler());
            server = Undertow.builder()
                    .setIoThreads(1)
                    .setWorkerThreads(4)
                    .addHttpListener(port, bindAddress)
                    .setHandler(gracefulShutdown)
                    .build();
            server.start();
        } catch (final RuntimeException | Error failure) {
            try {
                shutdown();
            } catch (final RuntimeException | Error shutdownFailure) {
                failure.addSuppressed(shutdownFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (minecraftExecutor != null) {
            minecraftExecutor.shutdown();
        }
        if (gracefulShutdown != null) {
            gracefulShutdown.shutdown();
            try {
                gracefulShutdown.awaitShutdown(requestTimeoutSeconds * 1_000L);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (server != null) {
            server.stop();
        }
        if (authenticationExecutor != null) {
            authenticationExecutor.shutdownNow();
            try {
                authenticationExecutor.awaitTermination(
                        requestTimeoutSeconds, TimeUnit.SECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        server = null;
        gracefulShutdown = null;
        minecraftExecutor = null;
        authenticationExecutor = null;
        sessionStore = null;
        authenticationCoordinator = null;
        credentialStore = null;
    }

    private static boolean isDimensionLoaded(final MinecraftServer server, final String dimension) {
        for (final ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static ThreadFactory namedDaemonFactory(final String name) {
        return task -> {
            final Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
