package cn.net.rms.syncmatica_r.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class WebPasswordProtocolTest {
    @Test
    void setRoundTripTransfersAndClearsPassword() {
        final char[] password = "correct horse battery staple".toCharArray();

        final byte[] encoded = WebPasswordProtocol.encodeSet(password);

        assertTrue(allZero(password));
        try (WebPasswordProtocol.Request request = WebPasswordProtocol.decode(encoded)) {
            assertEquals(WebPasswordProtocol.Action.SET, request.action());
            assertArrayEquals("correct horse battery staple".toCharArray(), request.password());
        }
        assertTrue(allZero(encoded));
    }

    @Test
    void disableRoundTripHasNoPassword() {
        try (WebPasswordProtocol.Request request =
                     WebPasswordProtocol.decode(WebPasswordProtocol.encodeDisable())) {
            assertEquals(WebPasswordProtocol.Action.DISABLE, request.action());
            assertEquals(0, request.password().length);
        }
    }

    @Test
    void rejectsMalformedActionsAndPasswordLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> WebPasswordProtocol.decode(new byte[] {99}));
        assertThrows(IllegalArgumentException.class,
                () -> WebPasswordProtocol.encodeSet("short".toCharArray()));
        final char[] tooLong = new char[WebPasswordProtocol.MAX_PASSWORD_CHARACTERS + 1];
        Arrays.fill(tooLong, 'a');
        assertThrows(IllegalArgumentException.class,
                () -> WebPasswordProtocol.encodeSet(tooLong));
        assertTrue(allZero(tooLong));
    }

    @Test
    void closingRequestClearsDecodedPassword() {
        final WebPasswordProtocol.Request request =
                WebPasswordProtocol.decode(WebPasswordProtocol.encodeSet(
                        "another secure password".toCharArray()));
        final char[] decoded = request.password();

        request.close();

        assertTrue(allZero(decoded));
    }

    @Test
    void malformedRequestBytesAreCleared() {
        final byte[] malformed = new byte[] {1, 0, 1, (byte) 0x80};

        assertThrows(IllegalArgumentException.class,
                () -> WebPasswordProtocol.decode(malformed));

        assertTrue(allZero(malformed));
    }

    @Test
    void resultCodesRoundTrip() {
        for (final WebPasswordProtocol.Result result : WebPasswordProtocol.Result.values()) {
            assertEquals(result, WebPasswordProtocol.Result.fromId(result.id()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> WebPasswordProtocol.Result.fromId((byte) 99));
    }

    private static boolean allZero(final char[] value) {
        for (final char character : value) {
            if (character != '\0') {
                return false;
            }
        }
        return true;
    }

    private static boolean allZero(final byte[] value) {
        for (final byte element : value) {
            if (element != 0) {
                return false;
            }
        }
        return true;
    }
}
