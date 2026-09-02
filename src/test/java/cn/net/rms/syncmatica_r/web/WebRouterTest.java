package cn.net.rms.syncmatica_r.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import cn.net.rms.syncmatica_r.web.auth.LoginRateLimiter;
import cn.net.rms.syncmatica_r.web.auth.WebAuthenticationCoordinator;
import cn.net.rms.syncmatica_r.web.auth.WebCredentialStore;
import cn.net.rms.syncmatica_r.web.auth.WebPasswordHasher;
import cn.net.rms.syncmatica_r.web.auth.WebSessionStore;
import com.google.gson.JsonObject;
import io.undertow.Undertow;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebRouterTest {
    private static final MaterialKey STONE =
            new MaterialKey(IdentifierUtil.require("minecraft:stone"), "");

    @TempDir
    Path tempDir;

    private Context context;
    private UUID aliceId;
    private ServerPlacement placement;
    private WebSessionStore sessions;
    private ThreadPoolExecutor authExecutor;
    private MinecraftThreadExecutor minecraft;
    private final java.util.concurrent.atomic.AtomicInteger minecraftOperations =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicReference<
            java.util.concurrent.CompletableFuture<Boolean>> permissionResult =
            new java.util.concurrent.atomic.AtomicReference<>();
    private Undertow server;
    private URI baseUri;
    private int serverPort;

    @BeforeEach
    void startServer() throws Exception {
        context = new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile());
        context.startup();
        aliceId = UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8));
        final PlayerIdentifier alice =
                context.getPlayerIdentifierProvider().createOrGet(aliceId, "Alice");
        placement = new ServerPlacement(UUID.randomUUID(), "project", UUID.randomUUID(), alice);
        placement.move("minecraft:overworld", new BlockPos(1, 2, 3),
                BlockRotation.NONE, BlockMirror.NONE);
        placement.getMaterialProgress().getOrCreate(STONE, 64);
        context.getSyncmaticManager().addPlacement(placement);
        context.getBuildService().replaceRegions(placement.getId(), Map.of("roof", 100L));

        final WebCredentialStore credentials = new WebCredentialStore(
                tempDir.resolve("web-credentials.json"), new WebPasswordHasher(1_000));
        credentials.set(aliceId, "Alice", "correct horse".toCharArray());
        sessions = new WebSessionStore(Duration.ofHours(1), Clock.systemUTC());
        authExecutor = new ThreadPoolExecutor(
                2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32));
        minecraft = new MinecraftThreadExecutor(task -> {
            minecraftOperations.incrementAndGet();
            task.run();
        }, Duration.ofSeconds(2));
        permissionResult.set(java.util.concurrent.CompletableFuture.completedFuture(true));
        final WebRouter router = new WebRouter(
                new WebFacade(context, "minecraft:overworld"::equals),
                new WebAuthenticationCoordinator(credentials, sessions),
                sessions,
                new LoginRateLimiter(),
                minecraft,
                authExecutor,
                (player, permission, fallback) -> permissionResult.get(),
                1_024,
                false,
                1,
                1);
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(router.handler())
                .build();
        server.start();
        final java.net.InetSocketAddress listener =
                (java.net.InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        serverPort = listener.getPort();
        baseUri = URI.create("http://127.0.0.1:" + serverPort);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (minecraft != null) {
            minecraft.shutdown();
        }
        if (authExecutor != null) {
            authExecutor.shutdownNow();
        }
        if (context != null && context.isStarted()) {
            context.shutdown();
        }
    }

    @Test
    void realSerializerPreservesEveryDtoFieldNestedObjectAndNull() {
        final WebDtos.Player player = new WebDtos.Player("player-id", "Alice");
        final List<Object> values = List.of(
                player,
                new WebDtos.Position("minecraft:overworld", 1, 2, 3),
                new WebDtos.ProjectSummary("id", "name", "owner", 4),
                new WebDtos.ProjectDetail("id", "name", "file", "hash", null, player,
                        1, 2, new WebDtos.Position("minecraft:overworld", 3, 4, 5),
                        "NONE", "NONE", "AVAILABLE"),
                new WebDtos.Material("minecraft:stone", "", 10, 4, 6, 40, List.of(player)),
                new WebDtos.MaterialSummary("minecraft:stone", "", 10, 4, 6, 40),
                new WebDtos.StockingArea("minecraft:overworld", 1, 2, 3, 4, 5, 6, 120),
                new WebDtos.BuildRegion("roof", 10, 4, true, 8, 40, List.of(player)));
        final List<Set<String>> fields = List.of(
                Set.of("id", "name"),
                Set.of("dimension", "x", "y", "z"),
                Set.of("id", "name", "ownerName", "lastModifiedAt"),
                Set.of("id", "name", "fileName", "hash", "owner", "lastModifiedBy",
                        "createdAt", "lastModifiedAt", "position", "rotation", "mirror",
                        "materialAvailability"),
                Set.of("itemId", "variant", "required", "supplied", "missing",
                        "progressPercent", "claimants"),
                Set.of("itemId", "variant", "required", "supplied", "missing",
                        "progressPercent"),
                Set.of("dimension", "minX", "minY", "minZ", "maxX", "maxY", "maxZ", "volume"),
                Set.of("name", "requiredBlocks", "placedBlocks", "scanned", "lastScanAt",
                        "progressPercent", "claimants"));

        for (int i = 0; i < values.size(); i++) {
            final JsonObject json = WebJson.parseObject(WebJson.toJson(values.get(i)));
            assertEquals(fields.get(i), keys(json), values.get(i).getClass().getSimpleName());
        }
        final JsonObject detail =
                WebJson.parseObject(WebJson.toJson(values.get(3)));
        assertTrue(detail.get("owner").isJsonNull());
        assertEquals(Set.of("id", "name"), keys(detail.getAsJsonObject("lastModifiedBy")));
        assertEquals(Set.of("dimension", "x", "y", "z"),
                keys(detail.getAsJsonObject("position")));
        assertTrue(WebJson.parseObject(WebJson.toJson(values.get(4)))
                .get("claimants").isJsonArray());
    }

    private static Set<String> keys(final JsonObject object) {
        return object.entrySet().stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void loginUsesGenericErrorsCanonicalIpAndStrictCookie() throws Exception {
        final HttpResponse<String> unknown = post("/api/v1/auth/login",
                "{\"name\":\"Unknown\",\"password\":\"wrong\"}", null, null);
        final HttpResponse<String> wrong = post("/api/v1/auth/login",
                "{\"name\":\"Alice\",\"password\":\"wrong\"}", null, null);

        assertEquals(401, unknown.statusCode());
        assertEquals(unknown.body(), wrong.body());
        assertEquals("invalid_credentials", json(unknown).get("code").getAsString());
        assertEquals(
                WebRouter.canonicalAddress(InetAddress.getByName("2001:db8::1")),
                WebRouter.canonicalAddress(InetAddress.getByName("2001:0db8:0:0:0:0:0:1")));

        final HttpResponse<String> login = login();
        final String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("syncmatica_session="));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Max-Age=3600"));
        assertFalse(cookie.contains("Secure"));
    }

    @Test
    void loginRequiresJsonAndRejectsCrossSiteBrowserRequestsWithoutCors() throws Exception {
        final HttpResponse<String> form = postWithHeaders(
                "name=Alice&password=correct+horse",
                "application/x-www-form-urlencoded", null, null);
        assertEquals(415, form.statusCode());
        assertEquals("unsupported_media_type", json(form).get("code").getAsString());

        final HttpResponse<String> crossOrigin = postWithHeaders(
                "{\"name\":\"Alice\",\"password\":\"correct horse\"}",
                "application/json", "https://attacker.example", "cross-site");
        assertEquals(403, crossOrigin.statusCode());
        assertEquals("cross_site_request", json(crossOrigin).get("code").getAsString());
        assertNull(crossOrigin.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        final HttpResponse<String> sameOrigin = postWithHeaders(
                "{\"name\":\"Alice\",\"password\":\"correct horse\"}",
                "application/json", baseUri.toString(), "same-origin");
        assertEquals(200, sameOrigin.statusCode());
    }

    @Test
    void secureCookiesExpectThePublicHttpsOriginBehindAReverseProxy() {
        assertEquals("https", WebRouter.originScheme(true, "http"));
        assertEquals("http", WebRouter.originScheme(false, "http"));
    }

    @Test
    void malformedAndNullJsonMapToBadRequest() throws Exception {
        for (final String body : List.of("{", "null")) {
            final HttpResponse<String> response =
                    post("/api/v1/auth/login", body, null, null);
            assertEquals(400, response.statusCode(), body);
            assertEquals("invalid_request", json(response).get("code").getAsString(), body);
        }
    }

    @Test
    void loginRateLimiterRejectsTheSixthAttempt() throws Exception {
        for (int attempt = 0; attempt < LoginRateLimiter.DEFAULT_MAX_ATTEMPTS; attempt++) {
            assertEquals(401, post("/api/v1/auth/login",
                    "{\"name\":\"Unknown\",\"password\":\"wrong\"}", null, null).statusCode());
        }

        final HttpResponse<String> limited = post("/api/v1/auth/login",
                "{\"name\":\"Unknown\",\"password\":\"wrong\"}", null, null);

        assertEquals(429, limited.statusCode());
        assertEquals("rate_limited", json(limited).get("code").getAsString());
    }

    @Test
    void secureCookieModeAddsSecureToSessionAndExpiryCookies() {
        assertTrue(WebRouter.sessionCookie("token", true, 1).contains("; Secure"));
        assertTrue(WebRouter.expiredCookie(true).contains("; Secure"));
        assertFalse(WebRouter.sessionCookie("token", false, 1).contains("; Secure"));
    }

    @Test
    void authenticationExecutorTimeoutReturnsStableGatewayTimeout() throws Exception {
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(2);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        for (int worker = 0; worker < 2; worker++) {
            authExecutor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS));

        final HttpResponse<String> response = login();
        release.countDown();

        assertEquals(503, response.statusCode());
        assertEquals("server_timeout", json(response).get("code").getAsString());
    }

    @Test
    void fullAuthenticationQueueRejectsLoginAsServerTimeout() throws Exception {
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(2);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final Runnable blocker = () -> {
            started.countDown();
            try {
                release.await();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        authExecutor.execute(blocker);
        authExecutor.execute(blocker);
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS));
        for (int queued = 0; queued < 32; queued++) {
            authExecutor.execute(() -> { });
        }

        final HttpResponse<String> response = login();
        release.countDown();

        assertEquals(503, response.statusCode());
        assertEquals("server_timeout", json(response).get("code").getAsString());
    }

    @Test
    void sessionCsrfLogoutAndSecurityHeadersAreEnforced() throws Exception {
        final Auth auth = auth();
        final HttpResponse<String> session = get("/api/v1/auth/session", auth.cookie);
        assertEquals(200, session.statusCode());
        assertEquals(aliceId.toString(), json(session).get("playerId").getAsString());
        assertEquals(auth.csrf, json(session).get("csrfToken").getAsString());
        assertEquals("no-store", session.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("default-src 'self'; object-src 'none'; frame-ancestors 'none'",
                session.headers().firstValue("Content-Security-Policy").orElseThrow());
        assertEquals("nosniff", session.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("no-referrer", session.headers().firstValue("Referrer-Policy").orElseThrow());
        assertEquals("DENY", session.headers().firstValue("X-Frame-Options").orElseThrow());
        assertNull(session.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        assertEquals(403, put(materialClaimPath(), "{}", auth.cookie, null).statusCode());
        assertEquals(200, put(materialClaimPath(), "{}", auth.cookie, auth.csrf).statusCode());
        assertEquals(200, delete(materialClaimPath(), auth.cookie, auth.csrf).statusCode());
        assertEquals(200, delete("/api/v1/projects/" + placement.getId()
                + "/material-claims/me", auth.cookie, auth.csrf).statusCode());

        final HttpResponse<String> logout =
                post("/api/v1/auth/logout", "{}", auth.cookie, auth.csrf);
        assertEquals(204, logout.statusCode());
        assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertEquals(401, get("/api/v1/auth/session", auth.cookie).statusCode());
    }

    @Test
    void permissionTimeoutReturnsStableGatewayTimeout() throws Exception {
        final Auth auth = auth();
        permissionResult.set(new java.util.concurrent.CompletableFuture<>());

        final HttpResponse<String> response =
                put(materialClaimPath(), "{}", auth.cookie, auth.csrf);

        assertEquals(503, response.statusCode());
        assertEquals("server_timeout", json(response).get("code").getAsString());
    }

    @Test
    void exposesOnlyApprovedReadAndMutationRoutes() throws Exception {
        final Auth auth = auth();
        final String project = "/api/v1/projects/" + placement.getId();

        assertEquals(200, get("/api/v1/projects", auth.cookie).statusCode());
        assertEquals(200, get(project, auth.cookie).statusCode());
        assertEquals(200, get(project + "/materials", auth.cookie).statusCode());
        assertEquals(200, get("/api/v1/materials/summary", auth.cookie).statusCode());
        assertEquals(200, get(project + "/build-regions", auth.cookie).statusCode());
        assertEquals(404, get(project + "/stocking-area", auth.cookie).statusCode());

        final String area = "{\"dimension\":\"minecraft:overworld\",\"minX\":0,\"minY\":1,"
                + "\"minZ\":2,\"maxX\":3,\"maxY\":4,\"maxZ\":5}";
        assertEquals(200, put(project + "/stocking-area", area, auth.cookie, auth.csrf).statusCode());
        assertEquals(200, get(project + "/stocking-area", auth.cookie).statusCode());
        final String fractionalArea = area.replace("\"minX\":0", "\"minX\":0.5");
        assertEquals(400,
                put(project + "/stocking-area", fractionalArea, auth.cookie, auth.csrf).statusCode());
        assertEquals(200, delete(project + "/stocking-area", auth.cookie, auth.csrf).statusCode());
        assertEquals(200, put(buildClaimPath(), "{}", auth.cookie, auth.csrf).statusCode());
        assertEquals(200, delete(buildClaimPath(), auth.cookie, auth.csrf).statusCode());

        assertEquals(405, post("/api/v1/projects", "{}", auth.cookie, auth.csrf).statusCode());
        assertEquals(405, delete(project, auth.cookie, auth.csrf).statusCode());
        assertEquals(404, get("/api/v1/config", auth.cookie).statusCode());
        assertEquals(404, post("/api/v1/projects/" + placement.getId() + "/rescan",
                "{}", auth.cookie, auth.csrf).statusCode());
        assertEquals(404, post("/api/v1/projects/" + placement.getId() + "/upload",
                "{}", auth.cookie, auth.csrf).statusCode());
    }

    @Test
    void nestedProjectReadsDistinguishUnknownProjectsFromEmptyData() throws Exception {
        final Auth auth = auth();
        final String project = "/api/v1/projects/" + UUID.randomUUID();

        for (final String path : List.of(
                project + "/materials",
                project + "/stocking-area",
                project + "/build-regions")) {
            final HttpResponse<String> response = get(path, auth.cookie);
            assertEquals(404, response.statusCode());
            assertEquals("project_not_found", json(response).get("code").getAsString());
        }
    }

    @Test
    void writesClassifyUnknownProjectsWithinOneMinecraftOperation() throws Exception {
        final Auth auth = auth();
        final UUID unknown = UUID.randomUUID();
        final List<String> paths = List.of(
                "/api/v1/projects/" + unknown
                        + "/materials/minecraft:stone/claim?variant=",
                "/api/v1/projects/" + unknown + "/build-regions/roof/claim",
                "/api/v1/projects/" + unknown + "/stocking-area");
        final List<String> bodies = List.of(
                "{}",
                "{}",
                "{\"dimension\":\"minecraft:overworld\",\"minX\":0,\"minY\":0,"
                        + "\"minZ\":0,\"maxX\":1,\"maxY\":1,\"maxZ\":1}");

        for (int index = 0; index < paths.size(); index++) {
            minecraftOperations.set(0);
            final HttpResponse<String> response =
                    put(paths.get(index), bodies.get(index), auth.cookie, auth.csrf);
            assertEquals(404, response.statusCode());
            assertEquals("project_not_found", json(response).get("code").getAsString());
            assertEquals(1, minecraftOperations.get(), paths.get(index));
        }
    }

    @Test
    void chunkedBodyOverLimitMapsToRequestTooLarge() throws Exception {
        final String body = "{\"name\":\"Alice\",\"password\":\""
                + "x".repeat(2_000) + "\"}";
        final RawResponse response = chunkedPost(body);

        assertEquals(413, response.status);
        assertEquals("request_too_large",
                WebJson.parseObject(response.body).get("code").getAsString());
    }

    @Test
    void futureJvmErrorsAreRethrownInsteadOfMapped() {
        final AssertionError error = new AssertionError("fatal");
        final AssertionError thrown = assertThrows(AssertionError.class,
                () -> WebRouter.futureFailure(
                        new java.util.concurrent.ExecutionException(error)));
        assertEquals(error, thrown);
    }

    @Test
    void limitsBodiesAndKeepsSpaFallbackOutOfApiSpace() throws Exception {
        final String oversized = "{\"name\":\"Alice\",\"password\":\"" + "x".repeat(2_000) + "\"}";
        final HttpResponse<String> tooLarge =
                post("/api/v1/auth/login", oversized, null, null);
        assertEquals(413, tooLarge.statusCode());
        assertEquals("request_too_large", json(tooLarge).get("code").getAsString());

        final HttpResponse<String> spa = get("/materials", null);
        assertEquals(200, spa.statusCode());
        assertTrue(spa.body().contains("<div id=\"root\"></div>"));
        assertEquals("text/html; charset=utf-8",
                spa.headers().firstValue("Content-Type").orElseThrow());

        final HttpResponse<String> api = get("/api/not-a-route", null);
        assertEquals(404, api.statusCode());
        assertEquals("not_found", json(api).get("code").getAsString());
        assertEquals(404, get("/api", null).statusCode());
    }

    @Test
    void servesFrontendAssetsReferencedByIndex() throws Exception {
        final HttpResponse<String> index = get("/", null);
        final Matcher references =
                Pattern.compile("(?:src|href)=\"([^\"]+)\"").matcher(index.body());
        assertTrue(references.find());
        do {
            final String path = references.group(1);
            assertEquals(200, get(path, null).statusCode(), path);
        } while (references.find());
    }

    private Auth auth() throws Exception {
        final HttpResponse<String> response = login();
        assertEquals(200, response.statusCode(), response.body());
        final String cookie = response.headers().firstValue("Set-Cookie")
                .orElseThrow().split(";", 2)[0];
        return new Auth(cookie, json(response).get("csrfToken").getAsString());
    }

    private HttpResponse<String> login() throws Exception {
        return post("/api/v1/auth/login",
                "{\"name\":\"Alice\",\"password\":\"correct horse\"}", null, null);
    }

    private String materialClaimPath() {
        return "/api/v1/projects/" + placement.getId()
                + "/materials/minecraft:stone/claim?variant=";
    }

    private String buildClaimPath() {
        return "/api/v1/projects/" + placement.getId() + "/build-regions/roof/claim";
    }

    private HttpResponse<String> get(final String path, final String cookie) throws Exception {
        return send("GET", path, null, cookie, null);
    }

    private HttpResponse<String> put(final String path, final String body,
                                     final String cookie, final String csrf) throws Exception {
        return send("PUT", path, body, cookie, csrf);
    }

    private HttpResponse<String> post(final String path, final String body,
                                      final String cookie, final String csrf) throws Exception {
        return send("POST", path, body, cookie, csrf);
    }

    private HttpResponse<String> postWithHeaders(final String body, final String contentType,
                                                 final String origin, final String fetchSite)
            throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/auth/login"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", contentType);
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (fetchSite != null) {
            request.header("Sec-Fetch-Site", fetchSite);
        }
        return HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(final String path, final String cookie,
                                        final String csrf) throws Exception {
        return send("DELETE", path, "{}", cookie, csrf);
    }

    private HttpResponse<String> send(final String method, final String path, final String body,
                                      final String cookie, final String csrf) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path));
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (csrf != null) {
            request.header("X-CSRF-Token", csrf);
        }
        return HttpClient.newHttpClient().send(
                request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(final HttpResponse<String> response) {
        return WebJson.parseObject(response.body());
    }

    private RawResponse chunkedPost(final String body) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", serverPort));
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            final String head = "POST /api/v1/auth/login HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + serverPort + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n\r\n"
                    + Integer.toHexString(bytes.length) + "\r\n";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            final String response = new String(
                    socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final int lineEnd = response.indexOf("\r\n");
            final int bodyStart = response.indexOf("\r\n\r\n");
            final int status = Integer.parseInt(response.substring(0, lineEnd).split(" ")[1]);
            return new RawResponse(status, response.substring(bodyStart + 4));
        }
    }

    private record Auth(String cookie, String csrf) {
    }

    private record RawResponse(int status, String body) {
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id,
                              final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
