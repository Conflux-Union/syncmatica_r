package cn.net.rms.syncmatica_r.web;

import cn.net.rms.syncmatica_r.communication.PlacementAccessPolicy;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.service.BuildService;
import cn.net.rms.syncmatica_r.service.MaterialService;
import cn.net.rms.syncmatica_r.web.auth.LoginRateLimiter;
import cn.net.rms.syncmatica_r.web.auth.WebAuthenticationCoordinator;
import cn.net.rms.syncmatica_r.web.auth.WebSessionStore;
import com.google.gson.JsonObject;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RequestTooBigException;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Identifier;

public final class WebRouter {
    public static final String SESSION_COOKIE = "syncmatica_session";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final String API = "/api/v1";
    private static final HttpString CSP = new HttpString("Content-Security-Policy");
    private static final HttpString REFERRER_POLICY = new HttpString("Referrer-Policy");
    private static final HttpString FRAME_OPTIONS = new HttpString("X-Frame-Options");
    private static final HttpString CSRF = new HttpString(CSRF_HEADER);
    private static final Pattern PROJECT =
            Pattern.compile("^/api/v1/projects/([^/]+)$");
    private static final Pattern MATERIALS =
            Pattern.compile("^/api/v1/projects/([^/]+)/materials$");
    private static final Pattern MATERIAL_CLAIM =
            Pattern.compile("^/api/v1/projects/([^/]+)/materials/([^/]+)/claim$");
    private static final Pattern MATERIAL_CLAIMS =
            Pattern.compile("^/api/v1/projects/([^/]+)/material-claims/me$");
    private static final Pattern STOCKING =
            Pattern.compile("^/api/v1/projects/([^/]+)/stocking-area$");
    private static final Pattern BUILD_REGIONS =
            Pattern.compile("^/api/v1/projects/([^/]+)/build-regions$");
    private static final Pattern BUILD_CLAIM =
            Pattern.compile("^/api/v1/projects/([^/]+)/build-regions/([^/]+)/claim$");
    private static final Pattern ASSET_REFERENCE =
            Pattern.compile("(?:src|href)=\"(/assets/[A-Za-z0-9._-]+)\"");

    private final WebFacade facade;
    private final WebAuthenticationCoordinator authentication;
    private final WebSessionStore sessions;
    private final LoginRateLimiter limiter;
    private final MinecraftThreadExecutor minecraft;
    private final Executor authExecutor;
    private final PermissionChecker permissions;
    private final int maxRequestBytes;
    private final boolean secureCookie;
    private final int sessionHours;
    private final int requestTimeoutSeconds;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SessionIdentity> identities = new java.util.concurrent.ConcurrentHashMap<>();
    private final StaticResources staticResources = new StaticResources();

    public WebRouter(
            final WebFacade facade,
            final WebAuthenticationCoordinator authentication,
            final WebSessionStore sessions,
            final LoginRateLimiter limiter,
            final MinecraftThreadExecutor minecraft,
            final Executor authExecutor,
            final PermissionChecker permissions,
            final int maxRequestBytes,
            final boolean secureCookie,
            final int sessionHours,
            final int requestTimeoutSeconds
    ) {
        this.facade = java.util.Objects.requireNonNull(facade, "facade");
        this.authentication = java.util.Objects.requireNonNull(authentication, "authentication");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        this.limiter = java.util.Objects.requireNonNull(limiter, "limiter");
        this.minecraft = java.util.Objects.requireNonNull(minecraft, "minecraft");
        this.authExecutor = java.util.Objects.requireNonNull(authExecutor, "authExecutor");
        this.permissions = java.util.Objects.requireNonNull(permissions, "permissions");
        this.maxRequestBytes = maxRequestBytes;
        this.secureCookie = secureCookie;
        this.sessionHours = sessionHours;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public HttpHandler handler() {
        return exchange -> {
            securityHeaders(exchange);
            exchange.dispatch(() -> {
                try {
                    if (exchange.getRequestPath().equals("/api")
                            || exchange.getRequestPath().startsWith("/api/")) {
                        handleApi(exchange);
                    } else {
                        handleStatic(exchange);
                    }
                } catch (final RequestTooLargeException | RequestTooBigException failure) {
                    exchange.setPersistent(false);
                    error(exchange, StatusCodes.REQUEST_ENTITY_TOO_LARGE,
                            "request_too_large", "Request body is too large");
                } catch (final IllegalArgumentException failure) {
                    error(exchange, StatusCodes.BAD_REQUEST, "invalid_request", "Invalid request");
                } catch (final TimeoutException | RejectedExecutionException failure) {
                    error(exchange, StatusCodes.SERVICE_UNAVAILABLE,
                            "server_timeout", "Server operation timed out");
                } catch (final RuntimeException failure) {
                    error(exchange, StatusCodes.INTERNAL_SERVER_ERROR,
                            "internal_error", "Internal server error");
                } catch (final Exception failure) {
                    error(exchange, StatusCodes.INTERNAL_SERVER_ERROR,
                            "internal_error", "Internal server error");
                }
            });
        };
    }

    private void handleApi(final HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-store");
        final String path = exchange.getRequestPath();
        if ((API + "/auth/login").equals(path)) {
            if (!Methods.POST.equals(exchange.getRequestMethod())) {
                methodNotAllowed(exchange, "POST");
                return;
            }
            if (!requireJson(exchange) || !requireSameOrigin(exchange)) {
                return;
            }
            login(exchange);
            return;
        }
        if (!isApprovedApiPath(path)) {
            error(exchange, StatusCodes.NOT_FOUND, "not_found", "Not found");
            return;
        }

        final Authenticated authenticated = authenticate(exchange);
        if (authenticated == null) {
            return;
        }
        if ((API + "/auth/session").equals(path)) {
            if (!Methods.GET.equals(exchange.getRequestMethod())) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            json(exchange, StatusCodes.OK, Map.of(
                    "authenticated", true,
                    "playerId", authenticated.identity.playerId.toString(),
                    "csrfToken", authenticated.identity.csrfToken));
            return;
        }
        if ((API + "/auth/logout").equals(path)) {
            if (!Methods.POST.equals(exchange.getRequestMethod())) {
                methodNotAllowed(exchange, "POST");
                return;
            }
            if (!requireCsrf(exchange, authenticated.identity)) {
                return;
            }
            sessions.revoke(authenticated.token);
            identities.remove(authenticated.token);
            exchange.getResponseHeaders().add(Headers.SET_COOKIE, expiredCookie());
            exchange.setStatusCode(StatusCodes.NO_CONTENT);
            exchange.endExchange();
            return;
        }
        routeDomain(exchange, authenticated.identity);
    }

    private void login(final HttpServerExchange exchange) throws Exception {
        final JsonObject body = readObject(exchange);
        final String name = requiredString(body, "name", 64);
        final String password = requiredString(body, "password", 1_024);
        final String address = canonicalAddress(remoteAddress(exchange));
        if (!limiter.tryAcquire(address, name)) {
            error(exchange, StatusCodes.TOO_MANY_REQUESTS,
                    "rate_limited", "Too many login attempts");
            return;
        }
        final Optional<String> token;
        CompletableFuture<Optional<String>> authenticationTask = null;
        try {
            authenticationTask = CompletableFuture.supplyAsync(
                    () -> authentication.authenticateAndCreateSession(
                            name, password.toCharArray()), authExecutor);
            token = authenticationTask.get(requestTimeoutSeconds, TimeUnit.SECONDS);
        } catch (final TimeoutException | RejectedExecutionException failure) {
            if (authenticationTask != null) {
                authenticationTask.cancel(true);
            }
            throw failure;
        } catch (final ExecutionException failure) {
            futureFailure(failure);
            return;
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        } catch (final Exception failure) {
            error(exchange, StatusCodes.SERVICE_UNAVAILABLE,
                    "authentication_unavailable", "Authentication unavailable");
            return;
        }
        if (token.isEmpty()) {
            error(exchange, StatusCodes.UNAUTHORIZED,
                    "invalid_credentials", "Invalid player name or password");
            return;
        }
        final UUID playerId = sessions.resolve(token.get()).orElseThrow();
        final String csrfToken = randomToken();
        identities.put(token.get(), new SessionIdentity(playerId, name, csrfToken));
        exchange.getResponseHeaders().add(Headers.SET_COOKIE, sessionCookie(token.get()));
        json(exchange, StatusCodes.OK, Map.of(
                "authenticated", true,
                "playerId", playerId.toString(),
                "csrfToken", csrfToken));
    }

    private void routeDomain(final HttpServerExchange exchange, final SessionIdentity identity)
            throws Exception {
        final String path = exchange.getRequestPath();
        final String method = exchange.getRequestMethod().toString();
        if ((API + "/projects").equals(path)) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            gameResponse(exchange, () -> facade.listProjects());
            return;
        }
        if ((API + "/materials/summary").equals(path)) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            gameResponse(exchange, () -> facade.getMaterialSummary());
            return;
        }

        Matcher matcher = PROJECT.matcher(path);
        if (matcher.matches()) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            final Optional<WebDtos.ProjectDetail> detail =
                    game(() -> facade.getProject(placementId));
            if (detail.isEmpty()) {
                error(exchange, StatusCodes.NOT_FOUND, "project_not_found", "Project not found");
            } else {
                json(exchange, StatusCodes.OK, detail.get());
            }
            return;
        }
        matcher = MATERIALS.matcher(path);
        if (matcher.matches()) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            if (!requireProject(exchange, placementId)) {
                return;
            }
            gameResponse(exchange, () -> facade.getMaterials(placementId));
            return;
        }
        matcher = MATERIAL_CLAIM.matcher(path);
        if (matcher.matches()) {
            if (!mutationMethod(exchange, identity, method)) {
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            final String itemId = decode(matcher.group(2));
            final String variant = firstQuery(exchange, "variant", "");
            final boolean allowed = permission(identity.playerId,
                    PlacementAccessPolicy.CLAIM_PERMISSION, true);
            if (!allowed) {
                error(exchange, StatusCodes.FORBIDDEN, "permission_denied", "Permission denied");
                return;
            }
            final ProjectOperation<MaterialService.ClaimOutcome> operation = game(() -> {
                if (facade.getProject(placementId).isEmpty()) {
                    return ProjectOperation.missing();
                }
                return ProjectOperation.found(facade.setMaterialClaim(
                        placementId,
                        new MaterialKey(parseIdentifier(itemId), variant),
                        identity.player(),
                        "PUT".equals(method)));
            });
            if (requireProject(exchange, operation)) {
                materialOutcome(exchange, operation.value);
            }
            return;
        }
        matcher = MATERIAL_CLAIMS.matcher(path);
        if (matcher.matches()) {
            if (!"DELETE".equals(method)) {
                methodNotAllowed(exchange, "DELETE");
                return;
            }
            if (!requireCsrf(exchange, identity)) {
                return;
            }
            if (!permission(identity.playerId, PlacementAccessPolicy.CLAIM_PERMISSION, true)) {
                error(exchange, StatusCodes.FORBIDDEN, "permission_denied", "Permission denied");
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            final ProjectOperation<MaterialService.ReleaseClaimsOutcome> operation = game(() -> {
                if (facade.getProject(placementId).isEmpty()) {
                    return ProjectOperation.missing();
                }
                return ProjectOperation.found(
                        facade.releaseMaterialClaims(placementId, identity.player()));
            });
            if (requireProject(exchange, operation)) {
                releaseClaimsOutcome(exchange, operation.value);
            }
            return;
        }
        matcher = STOCKING.matcher(path);
        if (matcher.matches()) {
            final UUID placementId = uuid(matcher.group(1));
            if ("GET".equals(method)) {
                if (!requireProject(exchange, placementId)) {
                    return;
                }
                final Optional<WebDtos.StockingArea> area =
                        game(() -> facade.getStockingArea(placementId));
                if (area.isEmpty()) {
                    error(exchange, StatusCodes.NOT_FOUND,
                            "stocking_area_not_found", "Stocking area not found");
                } else {
                    json(exchange, StatusCodes.OK, area.get());
                }
                return;
            }
            if (!mutationMethod(exchange, identity, method)) {
                return;
            }
            final boolean elevated = permission(identity.playerId,
                    PlacementAccessPolicy.MANAGE_PERMISSION, false);
            final String dimension;
            final int minX;
            final int minY;
            final int minZ;
            final int maxX;
            final int maxY;
            final int maxZ;
            if ("PUT".equals(method)) {
                final JsonObject body = readObject(exchange);
                dimension = requiredString(body, "dimension", 128);
                minX = requiredInt(body, "minX");
                minY = requiredInt(body, "minY");
                minZ = requiredInt(body, "minZ");
                maxX = requiredInt(body, "maxX");
                maxY = requiredInt(body, "maxY");
                maxZ = requiredInt(body, "maxZ");
            } else {
                dimension = null;
                minX = minY = minZ = maxX = maxY = maxZ = 0;
            }
            final ProjectOperation<WebFacade.StockingAreaOutcome> operation = game(() -> {
                if (facade.getProject(placementId).isEmpty()) {
                    return ProjectOperation.missing();
                }
                return ProjectOperation.found("PUT".equals(method)
                        ? facade.setStockingArea(
                                placementId, identity.player(), elevated, dimension,
                                minX, minY, minZ, maxX, maxY, maxZ)
                        : facade.clearStockingArea(
                                placementId, identity.player(), elevated));
            });
            if (requireProject(exchange, operation)) {
                stockingOutcome(exchange, operation.value);
            }
            return;
        }
        matcher = BUILD_REGIONS.matcher(path);
        if (matcher.matches()) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            if (!requireProject(exchange, placementId)) {
                return;
            }
            gameResponse(exchange, () -> facade.getBuildRegions(placementId));
            return;
        }
        matcher = BUILD_CLAIM.matcher(path);
        if (matcher.matches()) {
            if (!mutationMethod(exchange, identity, method)) {
                return;
            }
            final boolean allowed = permission(identity.playerId,
                    PlacementAccessPolicy.BUILD_CLAIM_PERMISSION, true);
            if (!allowed) {
                error(exchange, StatusCodes.FORBIDDEN, "permission_denied", "Permission denied");
                return;
            }
            final UUID placementId = uuid(matcher.group(1));
            final String region = decode(matcher.group(2));
            final ProjectOperation<BuildService.ClaimOutcome> operation = game(() -> {
                if (facade.getProject(placementId).isEmpty()) {
                    return ProjectOperation.missing();
                }
                return ProjectOperation.found(facade.setBuildClaim(
                        placementId, region, identity.player(), "PUT".equals(method)));
            });
            if (requireProject(exchange, operation)) {
                buildOutcome(exchange, operation.value);
            }
        }
    }

    private boolean mutationMethod(final HttpServerExchange exchange,
                                   final SessionIdentity identity, final String method) {
        if (!"PUT".equals(method) && !"DELETE".equals(method)) {
            methodNotAllowed(exchange, "PUT, DELETE");
            return false;
        }
        return requireCsrf(exchange, identity);
    }

    private <T> T game(final Supplier<T> operation) throws Exception {
        try {
            return minecraft.submit(operation).get();
        } catch (final ExecutionException failure) {
            futureFailure(failure);
            throw new IllegalStateException("Unreachable");
        }
    }

    static void futureFailure(final ExecutionException failure) throws Exception {
        final Throwable cause = failure.getCause();
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        if (cause instanceof Exception) {
            throw (Exception) cause;
        }
        throw new IllegalStateException(cause);
    }

    private boolean requireProject(final HttpServerExchange exchange, final UUID placementId)
            throws Exception {
        if (game(() -> facade.getProject(placementId)).isPresent()) {
            return true;
        }
        error(exchange, StatusCodes.NOT_FOUND, "project_not_found", "Project not found");
        return false;
    }

    private static boolean requireProject(
            final HttpServerExchange exchange,
            final ProjectOperation<?> operation
    ) {
        if (operation.projectFound) {
            return true;
        }
        error(exchange, StatusCodes.NOT_FOUND, "project_not_found", "Project not found");
        return false;
    }

    private boolean permission(final UUID playerId, final String permission,
                               final boolean fallback) throws Exception {
        try {
            return permissions.check(playerId, permission, fallback)
                    .get(requestTimeoutSeconds, TimeUnit.SECONDS);
        } catch (final ExecutionException failure) {
            futureFailure(failure);
            throw new IllegalStateException("Unreachable");
        }
    }

    private void gameResponse(final HttpServerExchange exchange, final Supplier<?> operation)
            throws Exception {
        try {
            json(exchange, StatusCodes.OK, game(operation));
        } catch (final TimeoutException failure) {
            error(exchange, StatusCodes.SERVICE_UNAVAILABLE,
                    "server_timeout", "Minecraft server did not respond");
        }
    }

    private static boolean requireJson(final HttpServerExchange exchange) {
        final String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null
                && "application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
            return true;
        }
        error(exchange, StatusCodes.UNSUPPORTED_MEDIA_TYPE,
                "unsupported_media_type", "Content-Type must be application/json");
        return false;
    }

    private boolean requireSameOrigin(final HttpServerExchange exchange) {
        final String fetchSite = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if (fetchSite != null && !"same-origin".equalsIgnoreCase(fetchSite)) {
            error(exchange, StatusCodes.FORBIDDEN,
                    "cross_site_request", "Cross-site login is not allowed");
            return false;
        }
        final String origin = exchange.getRequestHeaders().getFirst(Headers.ORIGIN);
        if (origin == null) {
            return true;
        }
        final String expected = originScheme(secureCookie, exchange.getRequestScheme()) + "://"
                + exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (stripTrailingSlash(origin).equalsIgnoreCase(expected)) {
            return true;
        }
        error(exchange, StatusCodes.FORBIDDEN,
                "cross_site_request", "Cross-site login is not allowed");
        return false;
    }

    private static String stripTrailingSlash(final String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    static String originScheme(final boolean secureCookie, final String requestScheme) {
        return secureCookie ? "https" : requestScheme;
    }

    private void materialOutcome(final HttpServerExchange exchange,
                                 final MaterialService.ClaimOutcome outcome) {
        switch (outcome) {
            case CLAIMED, RELEASED, ALREADY_CLAIMED, ALREADY_RELEASED ->
                    json(exchange, StatusCodes.OK, Map.of("outcome", outcome.name().toLowerCase()));
            case CLAIMED_BY_OTHER ->
                    error(exchange, StatusCodes.CONFLICT, "claim_conflict", "Already claimed");
            case UNKNOWN_MATERIAL ->
                    error(exchange, StatusCodes.NOT_FOUND, "material_not_found", "Material not found");
            case DISABLED ->
                    error(exchange, StatusCodes.CONFLICT, "feature_disabled", "Feature disabled");
        }
    }

    private void releaseClaimsOutcome(
            final HttpServerExchange exchange,
            final MaterialService.ReleaseClaimsOutcome outcome
    ) {
        switch (outcome) {
            case RELEASED, ALREADY_RELEASED ->
                    json(exchange, StatusCodes.OK, Map.of("outcome", outcome.name().toLowerCase()));
            case UNKNOWN_PLACEMENT ->
                    error(exchange, StatusCodes.NOT_FOUND, "project_not_found", "Project not found");
            case DISABLED ->
                    error(exchange, StatusCodes.CONFLICT, "feature_disabled", "Feature disabled");
        }
    }

    private void buildOutcome(final HttpServerExchange exchange,
                              final BuildService.ClaimOutcome outcome) {
        switch (outcome) {
            case CLAIMED, RELEASED, ALREADY_CLAIMED, ALREADY_RELEASED ->
                    json(exchange, StatusCodes.OK, Map.of("outcome", outcome.name().toLowerCase()));
            case CLAIMED_BY_OTHER ->
                    error(exchange, StatusCodes.CONFLICT, "claim_conflict", "Already claimed");
            case UNKNOWN_REGION ->
                    error(exchange, StatusCodes.NOT_FOUND, "region_not_found", "Region not found");
            case DISABLED ->
                    error(exchange, StatusCodes.CONFLICT, "feature_disabled", "Feature disabled");
        }
    }

    private void stockingOutcome(final HttpServerExchange exchange,
                                  final WebFacade.StockingAreaOutcome outcome) {
        switch (outcome) {
            case UPDATED, UNCHANGED ->
                    json(exchange, StatusCodes.OK, Map.of("outcome", outcome.name().toLowerCase()));
            case UNKNOWN_PLACEMENT ->
                    error(exchange, StatusCodes.NOT_FOUND, "project_not_found", "Project not found");
            case FORBIDDEN ->
                    error(exchange, StatusCodes.FORBIDDEN, "permission_denied", "Permission denied");
            case DIMENSION_NOT_LOADED ->
                    error(exchange, StatusCodes.CONFLICT,
                            "dimension_not_loaded", "Dimension is not loaded");
            case TOO_LARGE ->
                    error(exchange, StatusCodes.UNPROCESSABLE_ENTITY,
                            "stocking_area_too_large", "Stocking area is too large");
            case DISABLED ->
                    error(exchange, StatusCodes.CONFLICT, "feature_disabled", "Feature disabled");
        }
    }

    private Authenticated authenticate(final HttpServerExchange exchange) {
        final String token = cookie(exchange, SESSION_COOKIE);
        final Optional<UUID> playerId = sessions.resolve(token);
        final SessionIdentity identity = token == null ? null : identities.get(token);
        if (playerId.isEmpty() || identity == null || !playerId.get().equals(identity.playerId)) {
            if (token != null) {
                identities.remove(token);
            }
            error(exchange, StatusCodes.UNAUTHORIZED, "unauthorized", "Authentication required");
            return null;
        }
        return new Authenticated(token, identity);
    }

    private boolean requireCsrf(final HttpServerExchange exchange, final SessionIdentity identity) {
        if (!identity.csrfToken.equals(exchange.getRequestHeaders().getFirst(CSRF))) {
            error(exchange, StatusCodes.FORBIDDEN, "csrf_failed", "CSRF validation failed");
            return false;
        }
        return true;
    }

    private JsonObject readObject(final HttpServerExchange exchange) throws IOException {
        final long declared = exchange.getRequestContentLength();
        if (declared > maxRequestBytes) {
            throw new RequestTooLargeException();
        }
        exchange.startBlocking();
        try (InputStream input = exchange.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4_096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxRequestBytes) {
                    throw new RequestTooLargeException();
                }
                output.write(buffer, 0, read);
            }
            return WebJson.parseObject(output.toString(StandardCharsets.UTF_8));
        }
    }

    private void handleStatic(final HttpServerExchange exchange) {
        if (!Methods.GET.equals(exchange.getRequestMethod())
                && !Methods.HEAD.equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        final String path = exchange.getRequestPath();
        if (path.startsWith("/assets/")) {
            final StaticResource asset = staticResources.assets.get(path);
            if (asset == null) {
                error(exchange, StatusCodes.NOT_FOUND, "not_found", "Not found");
                return;
            }
            sendResource(exchange, asset);
            return;
        }
        sendResource(exchange, staticResources.index);
    }

    private static void sendResource(final HttpServerExchange exchange, final StaticResource resource) {
        if (resource == null) {
            error(exchange, StatusCodes.NOT_FOUND, "not_found", "Not found");
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, resource.contentType);
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL,
                resource.contentType.startsWith("text/html") ? "no-cache" : "public, max-age=31536000, immutable");
        if (Methods.HEAD.equals(exchange.getRequestMethod())) {
            exchange.setStatusCode(StatusCodes.OK);
            exchange.endExchange();
        } else {
            exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(resource.bytes));
        }
    }

    private static void securityHeaders(final HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(CSP,
                "default-src 'self'; object-src 'none'; frame-ancestors 'none'");
        exchange.getResponseHeaders().put(Headers.X_CONTENT_TYPE_OPTIONS, "nosniff");
        exchange.getResponseHeaders().put(REFERRER_POLICY, "no-referrer");
        exchange.getResponseHeaders().put(FRAME_OPTIONS, "DENY");
    }

    private static boolean isApprovedApiPath(final String path) {
        return (API + "/auth/session").equals(path)
                || (API + "/auth/logout").equals(path)
                || (API + "/projects").equals(path)
                || (API + "/materials/summary").equals(path)
                || PROJECT.matcher(path).matches()
                || MATERIALS.matcher(path).matches()
                || MATERIAL_CLAIM.matcher(path).matches()
                || MATERIAL_CLAIMS.matcher(path).matches()
                || STOCKING.matcher(path).matches()
                || BUILD_REGIONS.matcher(path).matches()
                || BUILD_CLAIM.matcher(path).matches();
    }

    static String canonicalAddress(final InetAddress address) {
        return Base64.getEncoder().encodeToString(address.getAddress());
    }

    private static InetAddress remoteAddress(final HttpServerExchange exchange) {
        final InetSocketAddress source = exchange.getSourceAddress();
        return source == null || source.getAddress() == null
                ? InetAddress.getLoopbackAddress() : source.getAddress();
    }

    private String sessionCookie(final String token) {
        return sessionCookie(token, secureCookie, sessionHours);
    }

    static String sessionCookie(final String token, final boolean secureCookie,
                                final int sessionHours) {
        return SESSION_COOKIE + "=" + token
                + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + TimeUnit.HOURS.toSeconds(sessionHours)
                + (secureCookie ? "; Secure" : "");
    }

    private String expiredCookie() {
        return expiredCookie(secureCookie);
    }

    static String expiredCookie(final boolean secureCookie) {
        return SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"
                + (secureCookie ? "; Secure" : "");
    }

    private String randomToken() {
        final byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String cookie(final HttpServerExchange exchange, final String name) {
        final String header = exchange.getRequestHeaders().getFirst(Headers.COOKIE);
        if (header == null) {
            return null;
        }
        for (final String part : header.split(";")) {
            final int separator = part.indexOf('=');
            if (separator > 0 && part.substring(0, separator).trim().equals(name)) {
                return part.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private static String firstQuery(final HttpServerExchange exchange,
                                     final String key, final String fallback) {
        final Deque<String> values = exchange.getQueryParameters().get(key);
        return values == null || values.isEmpty() ? fallback : values.getFirst();
    }

    private static UUID uuid(final String value) {
        return UUID.fromString(value);
    }

    private static Identifier parseIdentifier(final String value) {
//#if MC >= 12005
//$$         final Identifier identifier = Identifier.tryParse(value);
//#else
        final Identifier identifier = Identifier.tryParse(value);
//#endif
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        return identifier;
    }

    private static String decode(final String value) {
        return value;
    }

    private static String requiredString(final JsonObject body, final String key, final int maximum) {
        if (!body.has(key) || !body.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        final String value = body.get(key).getAsString();
        if (value.isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return value;
    }

    private static int requiredInt(final JsonObject body, final String key) {
        if (!body.has(key)
                || !body.get(key).isJsonPrimitive()
                || !body.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        try {
            return body.getAsJsonPrimitive(key).getAsBigDecimal().intValueExact();
        } catch (final ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + key, exception);
        }
    }

    private static void methodNotAllowed(final HttpServerExchange exchange, final String allow) {
        exchange.getResponseHeaders().put(Headers.ALLOW, allow);
        error(exchange, StatusCodes.METHOD_NOT_ALLOWED,
                "method_not_allowed", "Method not allowed");
    }

    private static void json(final HttpServerExchange exchange, final int status, final Object body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(WebJson.toJson(body));
    }

    private static void error(final HttpServerExchange exchange, final int status,
                              final String code, final String message) {
        if (!exchange.isResponseStarted()) {
            json(exchange, status, new WebError(code, message));
        }
    }

    @FunctionalInterface
    public interface PermissionChecker {
        CompletableFuture<Boolean> check(UUID playerId, String permission, boolean fallback);
    }

    private record SessionIdentity(UUID playerId, String name, String csrfToken) {
        private PlayerIdentifier player() {
            return new PlayerIdentifier(playerId, name);
        }
    }

    private record Authenticated(String token, SessionIdentity identity) {
    }

    private record ProjectOperation<T>(boolean projectFound, T value) {
        private static <T> ProjectOperation<T> missing() {
            return new ProjectOperation<>(false, null);
        }

        private static <T> ProjectOperation<T> found(final T value) {
            return new ProjectOperation<>(true, value);
        }
    }

    private record StaticResource(byte[] bytes, String contentType) {
    }

    private static final class StaticResources {
        private final StaticResource index;
        private final Map<String, StaticResource> assets;

        private StaticResources() {
            index = load("/web/index.html", "text/html; charset=utf-8");
            final Map<String, StaticResource> loadedAssets = new HashMap<>();
            if (index != null) {
                final Matcher matcher =
                        ASSET_REFERENCE.matcher(new String(index.bytes, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    final String path = matcher.group(1);
                    final StaticResource resource = load("/web" + path, contentType(path));
                    if (resource != null) {
                        loadedAssets.put(path, resource);
                    }
                }
            }
            assets = Map.copyOf(loadedAssets);
        }

        private static StaticResource load(final String path, final String contentType) {
            try (InputStream input = WebRouter.class.getResourceAsStream(path)) {
                return input == null ? null : new StaticResource(input.readAllBytes(), contentType);
            } catch (final IOException ignored) {
                return null;
            }
        }

        private static String contentType(final String path) {
            if (path.endsWith(".js")) {
                return "text/javascript; charset=utf-8";
            }
            if (path.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (path.endsWith(".svg")) {
                return "image/svg+xml";
            }
            if (path.endsWith(".png")) {
                return "image/png";
            }
            return "application/octet-stream";
        }
    }

    private static final class RequestTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
