package cn.net.rms.syncmatica_r.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SchematicPeekerTest {

    @TempDir
    Path tempDir;

    @Test
    void readsNameAndVersionsFromLitematicHeader() throws IOException {
        final File file = writeLitematic("build.litematic", "Test Build", 5, 2730);

        final SchematicPeek peek = SchematicPeeker.peek(file);

        assertNotNull(peek);
        assertTrue(peek.hasName());
        assertEquals("Test Build", peek.getName());
        assertEquals(5, peek.getLitematicVersion());
        assertEquals(2730, peek.getDataVersion());
    }

    @Test
    void reportsUnknownVersionsWhenHeaderFieldsAreMissing() throws IOException {
        final NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", "No Versions");
        final NbtCompound root = new NbtCompound();
        root.put("Metadata", metadata);
        final File file = writeCompound("noversion.litematic", root);

        final SchematicPeek peek = SchematicPeeker.peek(file);

        assertNotNull(peek);
        assertEquals("No Versions", peek.getName());
        assertEquals(SchematicPeek.UNKNOWN_VERSION, peek.getLitematicVersion());
        assertEquals(SchematicPeek.UNKNOWN_VERSION, peek.getDataVersion());
    }

    @Test
    void returnsNullForFilesWithoutLitematicMetadata() throws IOException {
        final File file = writeCompound("nometa.litematic", new NbtCompound());

        assertNull(SchematicPeeker.peek(file));
    }

    @Test
    void returnsNullForMissingOrUnreadableFiles() throws IOException {
        assertNull(SchematicPeeker.peek(new File(tempDir.toFile(), "missing.litematic")));

        final File garbage = new File(tempDir.toFile(), "garbage.litematic");
        try (OutputStream output = new FileOutputStream(garbage)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        assertNull(SchematicPeeker.peek(garbage));
    }

    private File writeLitematic(final String fileName, final String name,
                                final int version, final int dataVersion) throws IOException {
        final NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", name);
        final NbtCompound root = new NbtCompound();
        root.put("Metadata", metadata);
        root.putInt("Version", version);
        root.putInt("MinecraftDataVersion", dataVersion);
        return writeCompound(fileName, root);
    }

    private File writeCompound(final String fileName, final NbtCompound root) throws IOException {
        final File file = new File(tempDir.toFile(), fileName);
        try (OutputStream output = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, output);
        }
        return file;
    }
}
