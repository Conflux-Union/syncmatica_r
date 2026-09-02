package cn.net.rms.syncmatica_r.web;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class WebPasswordProtocol {
    public static final int MIN_PASSWORD_CHARACTERS = 10;
    public static final int MAX_PASSWORD_CHARACTERS = 128;
    public static final int MAX_PASSWORD_BYTES = 512;

    private WebPasswordProtocol() {
    }

    public static byte[] encodeSet(final char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
        ByteBuffer encoded = null;
        try {
            validateLength(password);
            encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(password));
            if (encoded.remaining() > MAX_PASSWORD_BYTES) {
                throw new IllegalArgumentException("Password is too long");
            }
            final byte[] packet = new byte[3 + encoded.remaining()];
            packet[0] = Action.SET.id;
            packet[1] = (byte) (encoded.remaining() >>> 8);
            packet[2] = (byte) encoded.remaining();
            encoded.get(packet, 3, encoded.remaining());
            return packet;
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("Password contains invalid characters", exception);
        } finally {
            Arrays.fill(password, '\0');
            if (encoded != null && encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
        }
    }

    public static byte[] encodeDisable() {
        return new byte[] {Action.DISABLE.id, 0, 0};
    }

    public static Request decode(final byte[] packet) {
        CharBuffer decoded = null;
        try {
            if (packet == null || packet.length < 3) {
                throw new IllegalArgumentException("Malformed password request");
            }
            final Action action = Action.fromId(packet[0]);
            final int length = (Byte.toUnsignedInt(packet[1]) << 8) | Byte.toUnsignedInt(packet[2]);
            if (length > MAX_PASSWORD_BYTES || packet.length != 3 + length) {
                throw new IllegalArgumentException("Malformed password request");
            }
            if (action == Action.DISABLE) {
                if (length != 0) {
                    throw new IllegalArgumentException("Disable request must not contain a password");
                }
                return new Request(action, new char[0]);
            }
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet, 3, length));
            final char[] password = new char[decoded.remaining()];
            decoded.get(password);
            try {
                validateLength(password);
                return new Request(action, password);
            } catch (final RuntimeException exception) {
                Arrays.fill(password, '\0');
                throw exception;
            }
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("Password contains invalid characters", exception);
        } finally {
            clear(packet);
            if (decoded != null && decoded.hasArray()) {
                Arrays.fill(decoded.array(), '\0');
            }
        }
    }

    public static void clear(final byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static void validateLength(final char[] password) {
        if (password.length < MIN_PASSWORD_CHARACTERS) {
            throw new IllegalArgumentException("Password is too short");
        }
        if (password.length > MAX_PASSWORD_CHARACTERS) {
            throw new IllegalArgumentException("Password is too long");
        }
    }

    public enum Action {
        SET((byte) 1),
        DISABLE((byte) 2);

        private final byte id;

        Action(final byte id) {
            this.id = id;
        }

        private static Action fromId(final byte id) {
            for (final Action action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown password action");
        }
    }

    public enum Result {
        PASSWORD_SET((byte) 1),
        PASSWORD_DISABLED((byte) 2),
        INVALID_REQUEST((byte) 3),
        FAILED((byte) 4),
        UNAVAILABLE((byte) 5),
        BUSY((byte) 6);

        private final byte id;

        Result(final byte id) {
            this.id = id;
        }

        public byte id() {
            return id;
        }

        public static Result fromId(final byte id) {
            for (final Result result : values()) {
                if (result.id == id) {
                    return result;
                }
            }
            throw new IllegalArgumentException("Unknown password result");
        }
    }

    public static final class Request implements AutoCloseable {
        private final Action action;
        private final char[] password;

        private Request(final Action action, final char[] password) {
            this.action = action;
            this.password = password;
        }

        public Action action() {
            return action;
        }

        public char[] password() {
            return password;
        }

        @Override
        public void close() {
            Arrays.fill(password, '\0');
        }
    }
}
