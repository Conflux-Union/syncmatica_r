package cn.net.rms.syncmatica_r.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class WebPasswordHasherTest {
    private final WebPasswordHasher hasher = new WebPasswordHasher(1_000);

    @Test
    void usesUniqueSaltsForTheSamePassword() {
        final WebPasswordHasher.PasswordRecord first = hasher.hash("secret".toCharArray());
        final WebPasswordHasher.PasswordRecord second = hasher.hash("secret".toCharArray());

        assertNotEquals(first.getSalt(), second.getSalt());
        assertNotEquals(first.getHash(), second.getHash());
    }

    @Test
    void verifiesValidPasswordAndRejectsInvalidPassword() {
        final WebPasswordHasher.PasswordRecord record = hasher.hash("correct".toCharArray());

        assertTrue(hasher.verify("correct".toCharArray(), record));
        assertFalse(hasher.verify("incorrect".toCharArray(), record));
    }

    @Test
    void clearsCallerOwnedPasswordArrays() {
        final char[] passwordToHash = "hash-me".toCharArray();
        final WebPasswordHasher.PasswordRecord record = hasher.hash(passwordToHash);
        final char[] passwordToVerify = "hash-me".toCharArray();

        assertTrue(allCleared(passwordToHash));
        assertTrue(hasher.verify(passwordToVerify, record));
        assertTrue(allCleared(passwordToVerify));
    }

    private static boolean allCleared(final char[] password) {
        final char[] cleared = new char[password.length];
        return Arrays.equals(cleared, password);
    }
}
