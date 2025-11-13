package cn.net.rms.syncmatica_r.client.update;

import cn.net.rms.syncmatica_r.util.VersionComparator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {

    private static final String RELEASES_URL = "https://api.github.com/repos/RMS-Server/syncmatica_r/releases/latest";
    private static final Logger LOGGER = LogManager.getLogger(UpdateChecker.class);
    private static UpdateChecker instance;

    private final String localVersion;
    private final boolean allowPreReleaseForStable;
    private UpdateState state;
    private String remoteVersion;
    private String remotePageUrl;
    private boolean notified;

    private UpdateChecker(final String localVersion, final boolean allowPreReleaseForStable) {
        this.localVersion = localVersion;
        this.allowPreReleaseForStable = allowPreReleaseForStable;
        this.state = UpdateState.NOT_CHECKED;
        this.remoteVersion = null;
        this.remotePageUrl = null;
        this.notified = false;
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

    public synchronized boolean isNotified() {
        return notified;
    }

    public synchronized void markNotified() {
        notified = true;
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
            final HttpURLConnection connection = openConnection();
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

    private HttpURLConnection openConnection() throws IOException {
        final URL url = new URL(RELEASES_URL);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
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
            final JsonObject root = new JsonParser().parse(payload).getAsJsonObject();
            final String tagName = root.get("tag_name").getAsString();
            final String pageUrl = root.get("html_url").getAsString();
            final String normalizedRemote = VersionComparator.normalize(tagName);
            final int compare;
            if (allowPreReleaseForStable) {
                compare = VersionComparator.compareAllowPreRelease(localVersion, normalizedRemote);
            } else {
                compare = VersionComparator.compare(localVersion, normalizedRemote);
            }
            final UpdateState newState;
            if (compare < 0) {
                newState = UpdateState.UPDATE_AVAILABLE;
            } else {
                newState = UpdateState.UP_TO_DATE;
            }
            synchronized (this) {
                this.remoteVersion = normalizedRemote;
                this.remotePageUrl = pageUrl;
                state = newState;
            }
            LOGGER.info(
                    "Syncmatica_r update check completed: localVersion={}, remoteVersion={}, state={}",
                    localVersion,
                    normalizedRemote,
                    newState
            );
        } catch (final Exception exception) {
            handleError(exception.getMessage());
        }
    }

    private void handleError(final String message) {
        synchronized (this) {
            state = UpdateState.FAILED;
        }
        LOGGER.info("Syncmatica_r update check failed: {}", message);
    }

    private enum UpdateState {
        NOT_CHECKED,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        FAILED
    }
}
