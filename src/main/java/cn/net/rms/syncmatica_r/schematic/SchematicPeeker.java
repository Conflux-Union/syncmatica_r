package cn.net.rms.syncmatica_r.schematic;

import cn.net.rms.syncmatica_r.util.NbtHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
//#if MC >= 12005
//$$ import net.minecraft.nbt.NbtSizeTracker;
//#else
import net.minecraft.nbt.NbtTagSizeTracker;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Reads litematic metadata (name, format version, Minecraft data version)
 * from a file without instantiating litematica classes, so it is usable on
 * both the dedicated server and the client.
 */
public final class SchematicPeeker {

    private static final Logger LOGGER = LogManager.getLogger(SchematicPeeker.class);
    private static final long MAX_NBT_BYTES = 64L * 1024L * 1024L;

    private SchematicPeeker() {
    }

    /**
     * Returns the peeked metadata, or null if the file is missing, unreadable
     * or not a litematic.
     */
    public static SchematicPeek peek(final File litematicFile) {
        if (litematicFile == null || !litematicFile.isFile()) {
            return null;
        }
        try (InputStream input = new FileInputStream(litematicFile)) {
//#if MC >= 12005
//$$             final NbtCompound root = NbtIo.readCompressed(input, NbtSizeTracker.of(MAX_NBT_BYTES));
//#else
            final NbtCompound root;
            try (DataInputStream dataInput = new DataInputStream(
                    new BufferedInputStream(new GZIPInputStream(input)))) {
                root = NbtIo.read(dataInput, new NbtTagSizeTracker(MAX_NBT_BYTES));
            }
//#endif
            final NbtCompound metadata = NbtHelper.getCompound(root, "Metadata");
            if (metadata == null) {
                return null;
            }
            final int version = NbtHelper.containsNumber(root, "Version")
                    ? NbtHelper.getInt(root, "Version")
                    : SchematicPeek.UNKNOWN_VERSION;
            final int dataVersion = NbtHelper.containsNumber(root, "MinecraftDataVersion")
                    ? NbtHelper.getInt(root, "MinecraftDataVersion")
                    : SchematicPeek.UNKNOWN_VERSION;
            return new SchematicPeek(NbtHelper.getString(metadata, "Name"), version, dataVersion);
        } catch (final Exception exception) {
            LOGGER.debug("Failed to peek litematic metadata from {}", litematicFile, exception);
            return null;
        }
    }
}
