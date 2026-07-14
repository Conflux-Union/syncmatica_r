package cn.net.rms.syncmatica_r.util;

import com.mojang.authlib.GameProfile;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

public class SyncmaticaUtil {

    static final int[] ILLEGAL_CHARS = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47};
    static final String ILLEGAL_PATTERNS = "(^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$)|(^\\.\\.*$)";
    private static final int MAX_FILE_NAME_BYTES = 200;

    static {
        Arrays.sort(ILLEGAL_CHARS);
    }

    private SyncmaticaUtil() {

    }

    public static String getProfileName(final GameProfile profile) {
        //#if MC >= 12110
        //$$ return profile.name();
        //#else
        return profile.getName();
        //#endif
    }

    public static UUID getProfileId(final GameProfile profile) {
        //#if MC >= 12110
        //$$ return profile.id();
        //#else
        return profile.getId();
        //#endif
    }

    public static UUID createChecksum(final InputStream fis) throws NoSuchAlgorithmException, IOException {

        final byte[] buffer = new byte[4096];
        final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                messageDigest.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return UUID.nameUUIDFromBytes(messageDigest.digest());
    }

    public static String sanitizeFileName(final String badFileName) {
        if (badFileName == null || badFileName.isEmpty()) {
            return "_";
        }
        final StringBuilder sanitized = new StringBuilder();
        int encodedBytes = 0;

        for (int i = 0; i < badFileName.length();) {
            final int c = badFileName.codePointAt(i);
            i += Character.charCount(c);
            if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
                final String codePoint = new String(Character.toChars(c));
                final int codePointBytes = codePoint.getBytes(StandardCharsets.UTF_8).length;
                if (encodedBytes + codePointBytes > MAX_FILE_NAME_BYTES) {
                    break;
                }
                sanitized.appendCodePoint(c);
                encodedBytes += codePointBytes;
            }
        }

        final String normalized = sanitized.toString()
                .replaceAll("[. ]+$", "")
                .replaceAll(ILLEGAL_PATTERNS, "_");
        return normalized.isEmpty() ? "_" : normalized;
    }

    public static boolean backupAndReplace(final Path backup, final Path current, final Path incoming) {
        if (!Files.exists(incoming)) {
            return false;
        }

        if (!overwrite(backup, current, 2)) {
            return false;
        }
        if (overwrite(current, incoming, 4)) {
            return true;
        }
        overwrite(current, backup, 8);
        return false;
    }

    private static boolean overwrite(final Path backup, final Path current, final int tries) {
        if (!Files.exists(current)) {

            return true;
        }
        try {
            Files.deleteIfExists(backup);
            Files.move(current, backup);
        } catch (final IOException exception) {
            if (tries <= 0) {
                LogManager.getLogger(SyncmaticaUtil.class).error("Excessive retries when trying to write Syncmatica_r placement", exception);

                return false;
            }
            return overwrite(backup, current, tries - 1);
        }

        return true;
    }

}
