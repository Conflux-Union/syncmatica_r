package cn.net.rms.syncmatica_r.web.auth;

import cn.net.rms.syncmatica_r.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WebCredentialStore {
    private static final int FILE_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path credentialFile;
    private final WebPasswordHasher hasher;
    private final WebPasswordHasher.PasswordRecord dummyPassword;
    private final AtomicFileMover atomicFileMover;
    private Map<UUID, Credential> credentials;
    private boolean writesBlocked;

    public WebCredentialStore(final Context context) {
        this(context, new WebPasswordHasher());
    }

    public WebCredentialStore(final Context context, final WebPasswordHasher hasher) {
        this(Objects.requireNonNull(context, "context").getConfigFolder().toPath()
                .resolve("web-credentials.json"), hasher);
    }

    public WebCredentialStore(final Path credentialFile, final WebPasswordHasher hasher) {
        this(credentialFile, hasher, WebCredentialStore::atomicReplace);
    }

    WebCredentialStore(
            final Path credentialFile,
            final WebPasswordHasher hasher,
            final AtomicFileMover atomicFileMover
    ) {
        this.credentialFile = Objects.requireNonNull(credentialFile, "credentialFile")
                .toAbsolutePath();
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        dummyPassword = hasher.dummyRecord();
        this.atomicFileMover = Objects.requireNonNull(atomicFileMover, "atomicFileMover");
        final LoadResult loaded = load();
        credentials = loaded.credentials;
        writesBlocked = loaded.writesBlocked;
    }

    /**
     * Stores a replacement credential. Ownership of {@code password} transfers to
     * this method, which clears it before returning or throwing.
     */
    public synchronized Optional<UUID> set(
            final UUID playerId,
            final String currentName,
            final char[] password
    ) throws IOException {
        Objects.requireNonNull(password, "password");
        try {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(currentName, "currentName");
            if (currentName.isEmpty()) {
                throw new IllegalArgumentException("Current player name must not be empty");
            }
            requireWritesAllowed();
            final WebPasswordHasher.PasswordRecord passwordRecord = hasher.hash(password);
            final Map<UUID, Credential> updated = new LinkedHashMap<>(credentials);
            final String canonicalName = canonicalName(currentName);
            UUID displacedPlayer = null;
            for (final Credential credential : credentials.values()) {
                if (!credential.playerId.equals(playerId)
                        && canonicalName(credential.currentName).equals(canonicalName)) {
                    displacedPlayer = credential.playerId;
                    updated.remove(credential.playerId);
                }
            }
            updated.put(playerId, new Credential(playerId, currentName, passwordRecord));
            persist(updated);
            credentials = updated;
            return Optional.ofNullable(displacedPlayer);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public synchronized void disable(final UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        requireWritesAllowed();
        if (!credentials.containsKey(playerId)) {
            return;
        }
        final Map<UUID, Credential> updated = new LinkedHashMap<>(credentials);
        updated.remove(playerId);
        persist(updated);
        credentials = updated;
    }

    /**
     * Authenticates the current player name. Ownership of {@code password}
     * transfers to this method, which clears it before returning or throwing.
     */
    public synchronized Optional<UUID> authenticate(
            final String currentName,
            final char[] password
    ) {
        Objects.requireNonNull(password, "password");
        try {
            Objects.requireNonNull(currentName, "currentName");
            final Credential credential = findByName(currentName);
            if (credential == null) {
                hasher.verify(password, dummyPassword);
                return Optional.empty();
            }
            return hasher.verify(password, credential.passwordRecord)
                    ? Optional.of(credential.playerId)
                    : Optional.empty();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private Credential findByName(final String currentName) {
        final String normalizedName = canonicalName(currentName);
        for (final Credential credential : credentials.values()) {
            if (canonicalName(credential.currentName).equals(normalizedName)) {
                return credential;
            }
        }
        return null;
    }

    private LoadResult load() {
        if (!Files.isRegularFile(credentialFile)) {
            return LoadResult.writable(new LinkedHashMap<>());
        }
        try (Reader reader = Files.newBufferedReader(credentialFile, StandardCharsets.UTF_8)) {
            final JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return LoadResult.writable(new LinkedHashMap<>());
            }
            if (root.get("version").getAsInt() != FILE_VERSION
                    || !root.get("credentials").isJsonArray()) {
                return LoadResult.writable(new LinkedHashMap<>());
            }
            final Map<UUID, Credential> loaded = new LinkedHashMap<>();
            final Map<String, UUID> names = new LinkedHashMap<>();
            for (final JsonElement element : root.getAsJsonArray("credentials")) {
                final Credential credential = fromJson(element.getAsJsonObject());
                final UUID existingNameOwner =
                        names.putIfAbsent(canonicalName(credential.currentName),
                                credential.playerId);
                if (existingNameOwner != null) {
                    return LoadResult.blocked();
                }
                loaded.put(credential.playerId, credential);
            }
            return LoadResult.writable(loaded);
        } catch (final IOException | RuntimeException ignored) {
            return LoadResult.writable(new LinkedHashMap<>());
        }
    }

    private static Credential fromJson(final JsonObject json) {
        final UUID playerId = UUID.fromString(json.get("uuid").getAsString());
        final String currentName = json.get("name").getAsString();
        final WebPasswordHasher.PasswordRecord passwordRecord =
                new WebPasswordHasher.PasswordRecord(
                        json.get("passwordVersion").getAsInt(),
                        json.get("iterations").getAsInt(),
                        json.get("salt").getAsString(),
                        json.get("hash").getAsString());
        return new Credential(playerId, currentName, passwordRecord);
    }

    private void persist(final Map<UUID, Credential> updated) throws IOException {
        final Path parent = credentialFile.getParent();
        Files.createDirectories(parent);
        final Path temporary = Files.createTempFile(
                parent, credentialFile.getFileName().toString() + ".", ".tmp");
        try {
            final JsonObject root = new JsonObject();
            root.addProperty("version", FILE_VERSION);
            final JsonArray serializedCredentials = new JsonArray();
            for (final Credential credential : updated.values()) {
                serializedCredentials.add(toJson(credential));
            }
            root.add("credentials", serializedCredentials);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                atomicFileMover.move(temporary, credentialFile);
            } catch (final IOException e) {
                throw new IOException(
                        "Atomic credential replacement failed; existing credentials were preserved",
                        e);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireWritesAllowed() throws IOException {
        if (writesBlocked) {
            throw new IOException(
                    "Credential file contains duplicate canonical player names");
        }
    }

    private static String canonicalName(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void atomicReplace(final Path source, final Path target)
            throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static JsonObject toJson(final Credential credential) {
        final JsonObject json = new JsonObject();
        json.addProperty("uuid", credential.playerId.toString());
        json.addProperty("name", credential.currentName);
        json.addProperty("passwordVersion", credential.passwordRecord.getVersion());
        json.addProperty("iterations", credential.passwordRecord.getIterations());
        json.addProperty("salt", credential.passwordRecord.getSalt());
        json.addProperty("hash", credential.passwordRecord.getHash());
        return json;
    }

    private static final class Credential {
        private final UUID playerId;
        private final String currentName;
        private final WebPasswordHasher.PasswordRecord passwordRecord;

        private Credential(
                final UUID playerId,
                final String currentName,
                final WebPasswordHasher.PasswordRecord passwordRecord
        ) {
            this.playerId = playerId;
            this.currentName = currentName;
            this.passwordRecord = passwordRecord;
        }
    }

    @FunctionalInterface
    interface AtomicFileMover {
        void move(Path source, Path target) throws IOException;
    }

    private static final class LoadResult {
        private final Map<UUID, Credential> credentials;
        private final boolean writesBlocked;

        private LoadResult(
                final Map<UUID, Credential> credentials,
                final boolean writesBlocked
        ) {
            this.credentials = credentials;
            this.writesBlocked = writesBlocked;
        }

        private static LoadResult writable(final Map<UUID, Credential> credentials) {
            return new LoadResult(credentials, false);
        }

        private static LoadResult blocked() {
            return new LoadResult(new LinkedHashMap<>(), true);
        }
    }
}
