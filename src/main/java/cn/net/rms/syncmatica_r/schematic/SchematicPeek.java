package cn.net.rms.syncmatica_r.schematic;

/**
 * Metadata read from a litematic file header without loading the schematic:
 * the display name stored by litematica plus the litematic format version and
 * the Minecraft data version the schematic was saved with.
 */
public final class SchematicPeek {

    public static final int UNKNOWN_VERSION = -1;

    private final String name;
    private final int litematicVersion;
    private final int dataVersion;

    public SchematicPeek(final String name, final int litematicVersion, final int dataVersion) {
        this.name = name == null ? "" : name;
        this.litematicVersion = litematicVersion;
        this.dataVersion = dataVersion;
    }

    public String getName() {
        return name;
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    public int getLitematicVersion() {
        return litematicVersion;
    }

    public int getDataVersion() {
        return dataVersion;
    }
}
