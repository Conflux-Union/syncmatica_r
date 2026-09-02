package cn.net.rms.syncmatica_r.web.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class WebPasswordHasher {
    public static final int CURRENT_VERSION = 1;
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MAX_ACCEPTED_ITERATIONS = 10_000_000;

    private final int iterations;
    private final SecureRandom random;

    public WebPasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public WebPasswordHasher(final int iterations) {
        this(iterations, new SecureRandom());
    }

    WebPasswordHasher(final int iterations, final SecureRandom random) {
        if (iterations <= 0 || iterations > MAX_ACCEPTED_ITERATIONS) {
            throw new IllegalArgumentException("Invalid PBKDF2 iteration count");
        }
        this.iterations = iterations;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Hashes the password and always clears the caller-owned array before returning.
     * PBKDF2 is CPU intensive and callers may run this method off the game thread.
     */
    public PasswordRecord hash(final char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            final byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);
            final byte[] hash = derive(password, salt, iterations);
            return new PasswordRecord(
                    CURRENT_VERSION,
                    iterations,
                    Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(hash));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies the password and always clears the caller-owned array before returning.
     * PBKDF2 is CPU intensive and callers may run this method off the game thread.
     */
    public boolean verify(final char[] password, final PasswordRecord record) {
        Objects.requireNonNull(password, "password");
        try {
            if (record == null
                    || record.getVersion() != CURRENT_VERSION
                    || record.getIterations() <= 0
                    || record.getIterations() > MAX_ACCEPTED_ITERATIONS) {
                return false;
            }
            final byte[] salt = Base64.getDecoder().decode(record.getSalt());
            final byte[] expected = Base64.getDecoder().decode(record.getHash());
            final byte[] actual = derive(password, salt, record.getIterations());
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (final IllegalArgumentException ignored) {
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] derive(final char[] password, final byte[] salt, final int iterations) {
        final PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", e);
        } finally {
            specification.clearPassword();
        }
    }

    public static final class PasswordRecord {
        private final int version;
        private final int iterations;
        private final String salt;
        private final String hash;

        public PasswordRecord(
                final int version,
                final int iterations,
                final String salt,
                final String hash
        ) {
            this.version = version;
            this.iterations = iterations;
            this.salt = Objects.requireNonNull(salt, "salt");
            this.hash = Objects.requireNonNull(hash, "hash");
        }

        public int getVersion() {
            return version;
        }

        public int getIterations() {
            return iterations;
        }

        public String getSalt() {
            return salt;
        }

        public String getHash() {
            return hash;
        }
    }
}
