package cn.net.rms.syncmatica_r.material;

/**
 * Why a placement has no usable material list. The server computes this while
 * extracting requirements and ships it to clients so an empty material list can
 * be told apart from one the server refused to build.
 *
 * <p>Wire codes are explicit so the enum can be reordered without breaking
 * older peers.</p>
 */
public enum MaterialAvailability {
    AVAILABLE(0, "", ""),
    DISABLED(1,
            "syncmatica_r.gui.label.material.unavailable.disabled",
            "syncmatica_r.error.material_disabled"),
    FILE_TOO_LARGE(2,
            "syncmatica_r.gui.label.material.unavailable.file_too_large",
            "syncmatica_r.error.material_file_too_large"),
    TOO_MANY_BLOCKS(3,
            "syncmatica_r.gui.label.material.unavailable.too_many_blocks",
            "syncmatica_r.error.material_too_many_blocks"),
    EXTRACTION_FAILED(4,
            "syncmatica_r.gui.label.material.unavailable.extraction_failed",
            "syncmatica_r.error.material_extraction_failed");

    private final int code;
    private final String translationKey;
    private final String messageKey;

    MaterialAvailability(final int code, final String translationKey, final String messageKey) {
        this.code = code;
        this.translationKey = translationKey;
        this.messageKey = messageKey;
    }

    public int getCode() {
        return code;
    }

    /**
     * Client-facing label without format arguments; the detailed numbers travel
     * separately as a chat message when the limit is first hit.
     */
    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Key for the one-shot notification sent to the placement owner, which can
     * carry a detail string with the offending and configured values.
     */
    public String getMessageKey() {
        return messageKey;
    }

    public boolean isBlocked() {
        return this != AVAILABLE;
    }

    public static MaterialAvailability fromCode(final int code) {
        for (final MaterialAvailability value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return AVAILABLE;
    }
}
