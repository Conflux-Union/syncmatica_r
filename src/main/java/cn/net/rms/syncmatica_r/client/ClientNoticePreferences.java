package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

final class ClientNoticePreferences {
    private static final Logger LOGGER = LogManager.getLogger(ClientNoticePreferences.class);
    private static final String FIELD_DISMISSED_NOTICES = "dismissed_breaking_change_notices";

    private final File configFile;
    private final Set<String> dismissedNotices = new LinkedHashSet<>();

    ClientNoticePreferences() {
        this(new File(new File("config", Syncmatica.MOD_ID), "client_notices.json"));
    }

    ClientNoticePreferences(final File configFile) {
        this.configFile = configFile;
    }

    void load() {
        dismissedNotices.clear();
        if (!configFile.isFile()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            final JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            final JsonElement notices = root.get(FIELD_DISMISSED_NOTICES);
            if (notices != null && notices.isJsonArray()) {
                for (final JsonElement notice : notices.getAsJsonArray()) {
                    addString(notice);
                }
            }
        } catch (final Exception exception) {
            LOGGER.warn("Failed to load client notice preferences from {}", configFile.getAbsolutePath(), exception);
        }
    }

    boolean isDismissed(final String noticeId) {
        return noticeId != null && dismissedNotices.contains(noticeId);
    }

    boolean dismiss(final String noticeId) {
        if (noticeId == null || noticeId.isEmpty()) {
            return false;
        }
        if (!dismissedNotices.add(noticeId)) {
            return true;
        }
        if (save()) {
            return true;
        }
        dismissedNotices.remove(noticeId);
        return false;
    }

    private void addString(final JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return;
        }
        final String noticeId = element.getAsString();
        if (!noticeId.isEmpty()) {
            dismissedNotices.add(noticeId);
        }
    }

    private boolean save() {
        final File folder = configFile.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            LOGGER.warn("Failed to create client notice configuration folder {}", folder.getAbsolutePath());
            return false;
        }
        final JsonObject root = new JsonObject();
        final JsonArray notices = new JsonArray();
        for (final String dismissedNotice : dismissedNotices) {
            notices.add(dismissedNotice);
        }
        root.add(FIELD_DISMISSED_NOTICES, notices);
        try (BufferedWriter writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            return true;
        } catch (final IOException exception) {
            LOGGER.warn("Failed to save client notice preferences to {}", configFile.getAbsolutePath(), exception);
            return false;
        }
    }
}
