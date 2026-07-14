package cn.net.rms.syncmatica_r.communication;

public final class ProtocolLimits {
    public static final int MAX_PACKET_BYTES = 1_048_576;
    public static final int MAX_TRANSFER_CHUNK_BYTES = 16_384;
    public static final long DEFAULT_MAX_SCHEMATIC_BYTES = 64L * 1024L * 1024L;
    public static final long DEFAULT_MAX_SCHEMATIC_BLOCKS = 8_000_000L;
    public static final int MAX_NESTED_CONTAINER_DEPTH = 10;
    public static final int MAX_ACTIVE_EXCHANGES = 8;
    public static final long EXCHANGE_TIMEOUT_MILLIS = 60_000L;
    public static final int MAX_SERVER_PLACEMENTS = 4_096;
    public static final int MAX_SUBREGIONS = 256;
    public static final int MAX_MATERIAL_ENTRIES = 512;
    public static final int MAX_CLAIMANTS_PER_MATERIAL = 4;
    public static final int MAX_FEATURE_STRING_LENGTH = 2_048;
    public static final int MAX_VERSION_LENGTH = 64;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_DISPLAY_NAME_LENGTH = 255;
    public static final int MAX_DIMENSION_ID_LENGTH = 256;
    public static final int MAX_SUBREGION_NAME_LENGTH = 128;
    public static final int MAX_PLAYER_NAME_LENGTH = 64;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    public static final int MAX_VARIANT_LENGTH = 128;
    public static final int MAX_MESSAGE_LENGTH = 2_048;

    private ProtocolLimits() {
    }

    public static int requireCount(final int value, final int maximum, final String field) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(field + " is outside the allowed range: " + value);
        }
        return value;
    }

    public static int requireIndex(final int value, final int length, final String field) {
        if (value < 0 || value >= length) {
            throw new IllegalArgumentException(field + " is outside the allowed range: " + value);
        }
        return value;
    }

    public static int requirePacketSize(final int size) {
        if (size < 0 || size > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Syncmatica_r payload exceeds the maximum size");
        }
        return size;
    }

    public static boolean isNestedContainerDepthAllowed(final int depth) {
        return depth >= 0 && depth <= MAX_NESTED_CONTAINER_DEPTH;
    }

    public static int requireTransferChunk(final int size, final int readableBytes) {
        if (size <= 0 || size > MAX_TRANSFER_CHUNK_BYTES || size != readableBytes) {
            throw new IllegalArgumentException("Invalid transfer chunk size: " + size);
        }
        return size;
    }
}
