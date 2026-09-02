package cn.net.rms.syncmatica_r.web.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebCredentialStoreTest {
    @TempDir
    Path temporaryDirectory;

    private final WebPasswordHasher hasher = new WebPasswordHasher(1_000);

    @Test
    void persistsCompleteJsonWithAtomicReplacement() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final WebCredentialStore store = new WebCredentialStore(file, hasher);
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        store.set(first, "Alice", "first-password".toCharArray());
        store.set(second, "Bob", "second-password".toCharArray());

        final JsonObject root = readJson(file);
        assertEquals(1, root.get("version").getAsInt());
        assertEquals(2, root.getAsJsonArray("credentials").size());
        assertFalse(Files.readString(file).contains("first-password"));
        assertFalse(Files.readString(file).contains("second-password"));
        try (java.util.stream.Stream<Path> children = Files.list(temporaryDirectory)) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void authenticatesCurrentNameCaseInsensitivelyByUuidIdentity() throws Exception {
        final WebCredentialStore store = store();
        final UUID player = UUID.randomUUID();
        store.set(player, "Alice", "secret".toCharArray());

        assertEquals(Optional.of(player), store.authenticate("aLiCe", "secret".toCharArray()));
        assertEquals(Optional.empty(), store.authenticate("Alice", "wrong".toCharArray()));
        assertEquals(Optional.empty(), store.authenticate("OldName", "secret".toCharArray()));
    }

    @Test
    void unknownNamesPerformOneDummyDerivationWithoutPersistingDummyCredential()
            throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final AtomicInteger derivations = new AtomicInteger();
        final WebPasswordHasher countingHasher =
                new WebPasswordHasher(1_000, new java.security.SecureRandom(),
                        derivations::incrementAndGet);
        final WebCredentialStore store = new WebCredentialStore(file, countingHasher);

        assertEquals(Optional.empty(),
                store.authenticate("Unknown", "wrong".toCharArray()));

        assertEquals(1, derivations.get());
        assertFalse(Files.exists(file), "the in-process dummy record must not be persisted");

        store.set(UUID.randomUUID(), "Alice", "secret".toCharArray());
        derivations.set(0);
        assertEquals(Optional.empty(),
                store.authenticate("Alice", "wrong".toCharArray()));
        assertEquals(1, derivations.get(),
                "unknown and known-invalid names must each perform one derivation");
    }

    @Test
    void changingCredentialReplacesNameAndPasswordForTheSameUuid() throws Exception {
        final WebCredentialStore store = store();
        final UUID player = UUID.randomUUID();
        store.set(player, "OldName", "old-password".toCharArray());

        store.set(player, "NewName", "new-password".toCharArray());

        assertEquals(Optional.empty(),
                store.authenticate("OldName", "old-password".toCharArray()));
        assertEquals(Optional.empty(),
                store.authenticate("NewName", "old-password".toCharArray()));
        assertEquals(Optional.of(player),
                store.authenticate("NEWNAME", "new-password".toCharArray()));
    }

    @Test
    void disablingCredentialRemovesAccessAndPersistsAcrossRestart() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final UUID player = UUID.randomUUID();
        final WebCredentialStore store = new WebCredentialStore(file, hasher);
        store.set(player, "Alice", "secret".toCharArray());

        store.disable(player);

        final WebCredentialStore restarted = new WebCredentialStore(file, hasher);
        assertEquals(Optional.empty(),
                restarted.authenticate("Alice", "secret".toCharArray()));
    }

    @Test
    void credentialsSurviveRestartWithoutPersistingPlaintext() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final UUID player = UUID.randomUUID();
        new WebCredentialStore(file, hasher)
                .set(player, "Alice", "restart-secret".toCharArray());

        final WebCredentialStore restarted = new WebCredentialStore(file, hasher);

        assertEquals(Optional.of(player),
                restarted.authenticate("alice", "restart-secret".toCharArray()));
        assertFalse(Files.readString(file).contains("restart-secret"));
    }

    @Test
    void recoversFromMalformedFileWithoutRepeatingItsPlaintext() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        Files.writeString(file, "{malformed: leaked-plaintext");
        final WebCredentialStore store = new WebCredentialStore(file, hasher);

        store.set(UUID.randomUUID(), "Alice", "replacement".toCharArray());

        assertFalse(Files.readString(file).contains("leaked-plaintext"));
        assertFalse(Files.readString(file).contains("replacement"));
        assertEquals(1, readJson(file).getAsJsonArray("credentials").size());
    }

    @Test
    void failedPersistenceKeepsExistingCredentialInMemory() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final UUID player = UUID.randomUUID();
        final WebCredentialStore store = new WebCredentialStore(file, hasher);
        store.set(player, "Alice", "old-password".toCharArray());
        Files.delete(file);
        Files.createDirectory(file);

        assertThrows(IOException.class,
                () -> store.set(player, "Alice", "new-password".toCharArray()));

        assertEquals(Optional.of(player),
                store.authenticate("Alice", "old-password".toCharArray()));
        assertEquals(Optional.empty(),
                store.authenticate("Alice", "new-password".toCharArray()));
        assertTrue(Files.isDirectory(file));
    }

    @Test
    void unsupportedAtomicReplacementPreservesExistingFileBytes() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final UUID player = UUID.randomUUID();
        new WebCredentialStore(file, hasher)
                .set(player, "Alice", "old-password".toCharArray());
        final byte[] existingBytes = Files.readAllBytes(file);
        final WebCredentialStore store = new WebCredentialStore(
                file,
                hasher,
                (source, target) -> {
                    throw new AtomicMoveNotSupportedException(
                            source.toString(), target.toString(), "unsupported");
                });

        assertThrows(IOException.class,
                () -> store.set(player, "Alice", "new-password".toCharArray()));

        assertArrayEquals(existingBytes, Files.readAllBytes(file));
        final WebCredentialStore restarted = new WebCredentialStore(file, hasher);
        assertEquals(Optional.of(player),
                restarted.authenticate("Alice", "old-password".toCharArray()));
        assertEquals(Optional.empty(),
                restarted.authenticate("Alice", "new-password".toCharArray()));
    }

    @Test
    void transfersCanonicalNameOwnershipToTheCurrentUuid() throws Exception {
        final WebCredentialStore store = store();
        final UUID previousOwner = UUID.randomUUID();
        final UUID currentOwner = UUID.randomUUID();
        store.set(previousOwner, "Alice", "old-password".toCharArray());

        final Optional<UUID> displaced =
                store.set(currentOwner, "aLiCe", "new-password".toCharArray());

        assertEquals(Optional.of(previousOwner), displaced);
        assertEquals(Optional.empty(),
                store.authenticate("Alice", "old-password".toCharArray()));
        assertEquals(Optional.of(currentOwner),
                store.authenticate("ALICE", "new-password".toCharArray()));
    }

    @Test
    void duplicateCanonicalNamesInFileFailClosedWithoutRewriting() throws Exception {
        final Path file = temporaryDirectory.resolve("web-credentials.json");
        final JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        final JsonArray credentials = new JsonArray();
        credentials.add(credentialJson(
                UUID.randomUUID(), "Alice", hasher.hash("first".toCharArray())));
        credentials.add(credentialJson(
                UUID.randomUUID(), "ALICE", hasher.hash("second".toCharArray())));
        root.add("credentials", credentials);
        Files.writeString(file, new Gson().toJson(root), StandardCharsets.UTF_8);
        final byte[] duplicateBytes = Files.readAllBytes(file);

        final WebCredentialStore store = new WebCredentialStore(file, hasher);

        assertEquals(Optional.empty(), store.authenticate("Alice", "first".toCharArray()));
        assertThrows(IOException.class,
                () -> store.set(UUID.randomUUID(), "Bob", "third".toCharArray()));
        assertArrayEquals(duplicateBytes, Files.readAllBytes(file));
    }

    @Test
    void clearsTransferredPasswordsWhenOtherArgumentsAreInvalid() {
        final WebCredentialStore store = store();
        final char[] setPassword = "set-secret".toCharArray();
        final char[] authenticatePassword = "auth-secret".toCharArray();

        assertThrows(NullPointerException.class,
                () -> store.set(null, "Alice", setPassword));
        assertThrows(NullPointerException.class,
                () -> store.authenticate(null, authenticatePassword));

        assertTrue(Arrays.equals(new char[setPassword.length], setPassword));
        assertTrue(Arrays.equals(new char[authenticatePassword.length], authenticatePassword));
    }

    private WebCredentialStore store() {
        return new WebCredentialStore(
                temporaryDirectory.resolve("web-credentials.json"), hasher);
    }

    private static JsonObject readJson(final Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private static JsonObject credentialJson(
            final UUID playerId,
            final String name,
            final WebPasswordHasher.PasswordRecord password
    ) {
        final JsonObject credential = new JsonObject();
        credential.addProperty("uuid", playerId.toString());
        credential.addProperty("name", name);
        credential.addProperty("passwordVersion", password.getVersion());
        credential.addProperty("iterations", password.getIterations());
        credential.addProperty("salt", password.getSalt());
        credential.addProperty("hash", password.getHash());
        return credential;
    }
}
