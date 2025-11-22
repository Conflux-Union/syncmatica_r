package cn.net.rms.syncmatica_r.client.update;

import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.util.VersionComparator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

public final class UpdateChecker {

    private static final String RELEASES_URL = "https://api.github.com/repos/RMS-Server/syncmatica_r/releases?per_page=10";
    private static final String SHA256_SUFFIX = ".sha256";
    private static final Logger LOGGER = LogManager.getLogger(UpdateChecker.class);
    private static UpdateChecker instance;

    private final String localVersion;
    private final boolean allowPreReleaseForStable;
    private UpdateState state;
    private String remoteVersion;
    private String remotePageUrl;
    private boolean notified;
    private boolean integrityNotified;

    private UpdateChecker(final String localVersion, final boolean allowPreReleaseForStable) {
        this.localVersion = localVersion;
        this.allowPreReleaseForStable = allowPreReleaseForStable;
        this.state = UpdateState.NOT_CHECKED;
        this.remoteVersion = null;
        this.remotePageUrl = null;
        this.notified = false;
        this.integrityNotified = false;
    }

    public static synchronized void init(final String localVersion, final boolean allowPreReleaseForStable) {
        if (instance == null) {
            instance = new UpdateChecker(localVersion, allowPreReleaseForStable);
        }
    }

    public static UpdateChecker getInstance() {
        return instance;
    }

    public synchronized void checkForUpdatesAsync() {
        if (state != UpdateState.NOT_CHECKED) {
            return;
        }
        state = UpdateState.CHECKING;
        final Thread thread = new Thread(this::runCheck, "syncmatica_r-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized boolean hasUpdate() {
        return state == UpdateState.UPDATE_AVAILABLE;
    }

    public synchronized boolean hasIntegrityWarning() {
        return state == UpdateState.INTEGRITY_WARNING;
    }

    public synchronized boolean isNotified() {
        return notified;
    }

    public synchronized boolean isIntegrityNotified() {
        return integrityNotified;
    }

    public synchronized void markNotified() {
        notified = true;
    }

    public synchronized void markIntegrityNotified() {
        integrityNotified = true;
    }

    public synchronized String getRemoteVersion() {
        return remoteVersion;
    }

    public synchronized String getRemotePageUrl() {
        return remotePageUrl;
    }

    private void runCheck() {
        try {
            LOGGER.info(
                    "Syncmatica_r update check: contacting {} (localVersion={}, allowPreReleaseForStable={})",
                    RELEASES_URL,
                    localVersion,
                    allowPreReleaseForStable
            );
            final HttpURLConnection connection = openReleaseConnection();
            final int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                handleError("Unexpected HTTP status: " + status);
                connection.disconnect();
                return;
            }
            final String payload = readPayload(connection);
            connection.disconnect();
            handlePayload(payload);
        } catch (final Exception exception) {
            handleError(exception.getMessage());
        }
    }

    private HttpURLConnection openReleaseConnection() throws IOException {
        return openConnection(RELEASES_URL, "application/vnd.github.v3+json");
    }

    private HttpURLConnection openConnection(final String urlString, final String accept) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "syncmatica_r/" + localVersion);
        return connection;
    }

    private String readPayload(final HttpURLConnection connection) throws IOException {
        final InputStream stream = connection.getInputStream();
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void handlePayload(final String payload) {
        try {
            final JsonElement parsed = new JsonParser().parse(payload);
            final boolean localIsPre = VersionComparator.isPreRelease(localVersion);
            final boolean includePreReleases = localIsPre || allowPreReleaseForStable;
            final JsonObject root = extractReleaseObject(parsed, includePreReleases);
            if (root == null) {
                handleError("No releases available in payload");
                return;
            }
            final String tagName = root.get("tag_name").getAsString();
            final String pageUrl = root.get("html_url").getAsString();
            final String normalizedRemote = VersionComparator.normalize(tagName);
            final boolean allowPre = localIsPre || allowPreReleaseForStable;
            final int compare;
            if (allowPre) {
                compare = VersionComparator.compareAllowPreRelease(localVersion, normalizedRemote);
            } else {
                compare = VersionComparator.compare(localVersion, normalizedRemote);
            }
            final UpdateState newState;
            boolean integrityWarning = false;
            if (compare < 0) {
                newState = UpdateState.UPDATE_AVAILABLE;
            } else if (compare > 0) {
                newState = UpdateState.UP_TO_DATE;
            } else {
                integrityWarning = shouldWarnIntegrity(root);
                newState = integrityWarning ? UpdateState.INTEGRITY_WARNING : UpdateState.UP_TO_DATE;
            }
            synchronized (this) {
                this.remoteVersion = normalizedRemote;
                this.remotePageUrl = pageUrl;
                state = newState;
                integrityNotified = false;
            }
            LOGGER.info(
                    "Syncmatica_r update check completed: localVersion={}, remoteVersion={}, state={}",
                    localVersion,
                    normalizedRemote,
                    newState
            );
            if (integrityWarning) {
                LOGGER.warn("Syncmatica_r SHA-256 mismatch detected for version {}", normalizedRemote);
            }
        } catch (final Exception exception) {
            handleError(exception.getMessage());
        }
    }

    private JsonObject extractReleaseObject(final JsonElement element, final boolean includePreReleases) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            final JsonObject obj = element.getAsJsonObject();
            if (!includePreReleases && isPreRelease(obj)) {
                return null;
            }
            return obj;
        }
        if (element.isJsonArray()) {
            final JsonArray array = element.getAsJsonArray();
            if (array.size() > 0) {
                if (includePreReleases) {
                    final JsonElement first = array.get(0);
                    if (first != null && first.isJsonObject()) {
                        return first.getAsJsonObject();
                    }
                } else {
                    for (int i = 0; i < array.size(); i++) {
                        final JsonElement candidate = array.get(i);
                        if (candidate == null || !candidate.isJsonObject()) {
                            continue;
                        }
                        final JsonObject obj = candidate.getAsJsonObject();
                        if (!isPreRelease(obj)) {
                            return obj;
                        }
                    }
                    final JsonElement fallback = array.get(0);
                    if (fallback != null && fallback.isJsonObject()) {
                        return fallback.getAsJsonObject();
                    }
                }
            }
        }
        return null;
    }

    private boolean isPreRelease(final JsonObject obj) {
        return obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
    }

    private void handleError(final String message) {
        synchronized (this) {
            state = UpdateState.FAILED;
            integrityNotified = false;
        }
        LOGGER.info("Syncmatica_r update check failed: {}", message);
    }

    private enum UpdateState {
        NOT_CHECKED,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        INTEGRITY_WARNING,
        FAILED
    }

    private boolean shouldWarnIntegrity(final JsonObject root) {
        final Optional<Path> jarPath = locateCurrentModJar();
        if (!jarPath.isPresent()) {
            return false;
        }
        final String jarName = jarPath.get().getFileName().toString();
        final String remoteSha = resolveRemoteSha(root, jarName);
        if (remoteSha == null) {
            return false;
        }
        try {
            final String localSha = computeSha256(jarPath.get());
            LOGGER.info("Syncmatica_r integrity check: remote SHA={}, local SHA={}", remoteSha, localSha);
            return !remoteSha.equalsIgnoreCase(localSha);
        } catch (final IOException | NoSuchAlgorithmException exception) {
            LOGGER.info("Failed to compute local SHA-256: {}", exception.getMessage());
            return false;
        }
    }

    private Optional<Path> locateCurrentModJar() {
        final Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Syncmatica.MOD_ID);
        if (!container.isPresent()) {
            return Optional.empty();
        }
        return container.get().getOrigin().getPaths().stream()
                .filter(path -> path != null && path.toString().endsWith(".jar"))
                .filter(path -> Files.isRegularFile(path))
                .findFirst();
    }

    private String resolveRemoteSha(final JsonObject root, final String jarFileName) {
        if (!root.has("assets")) {
            return null;
        }
        final JsonElement assetsElement = root.get("assets");
        if (assetsElement == null || !assetsElement.isJsonArray()) {
            return null;
        }
        final JsonArray assets = assetsElement.getAsJsonArray();
        final String wantedAssetName = jarFileName + SHA256_SUFFIX;
        for (final JsonElement element : assets) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            final JsonObject asset = element.getAsJsonObject();
            if (!asset.has("name")) {
                continue;
            }
            final String assetName = asset.get("name").getAsString();
            if (!assetName.endsWith(SHA256_SUFFIX)) {
                continue;
            }
            if (wantedAssetName.equals(assetName)) {
                return downloadShaFromAsset(asset);
            }
        }
        return null;
    }

    private String downloadShaFromAsset(final JsonObject asset) {
        if (!asset.has("browser_download_url")) {
            return null;
        }
        final String url = asset.get("browser_download_url").getAsString();
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url, "text/plain");
            final int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                LOGGER.info("Failed to download SHA asset {}: HTTP {}", url, status);
                return null;
            }
            final String payload = readPayload(connection);
            return extractShaFromPayload(payload);
        } catch (final IOException exception) {
            LOGGER.info("Failed to download SHA asset {}: {}", url, exception.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractShaFromPayload(final String payload) {
        if (payload == null) {
            return null;
        }
        final String trimmed = payload.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        final int separator = findSeparator(trimmed);
        final String candidate = separator < 0 ? trimmed : trimmed.substring(0, separator);
        if (candidate.length() != 64) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            final char c = candidate.charAt(i);
            if (!isHexChar(c)) {
                return null;
            }
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    private int findSeparator(final String value) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isHexChar(final char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private String computeSha256(final Path jarPath) throws IOException, NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(jarPath)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private String toHex(final byte[] data) {
        final char[] digits = "0123456789abcdef".toCharArray();
        final char[] chars = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            final int v = data[i] & 0xFF;
            chars[i * 2] = digits[v >>> 4];
            chars[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(chars);
    }
}
