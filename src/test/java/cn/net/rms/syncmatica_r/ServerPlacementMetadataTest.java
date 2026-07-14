package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerPlacementMetadataTest {

    @TempDir
    Path tempDir;

    @Test
    void jsonRoundTripPreservesDisplayNameAndVersions() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = newPlacement("build");
            placement.setDisplayName("Fancy Build");
            placement.setVersion(6, 3465);

            final JsonObject json = placement.toJson();
            final ServerPlacement restored = ServerPlacement.fromJson(json, context);

            assertNotNull(restored);
            assertEquals("Fancy Build", restored.getName());
            assertEquals("build", restored.getFileName());
            assertEquals(6, restored.getLitematicVersion());
            assertEquals(3465, restored.getDataVersion());
            assertFalse(restored.consumeMetadataDirty());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void nameFallsBackToFileNameWithoutDisplayName() {
        final ServerPlacement placement = newPlacement("plain");

        assertEquals("plain", placement.getName());
        assertEquals(SchematicPeek.UNKNOWN_VERSION, placement.getLitematicVersion());
        assertEquals(SchematicPeek.UNKNOWN_VERSION, placement.getDataVersion());
    }

    @Test
    void serverEnrichesLegacyJsonFromStoredLitematicFile() throws IOException {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = newPlacement("legacy");
            final JsonObject json = placement.toJson();
            json.remove("display_name");

            final File litematicFolder = context.getLitematicFolder();
            assertTrue(litematicFolder.isDirectory() || litematicFolder.mkdirs());
            writeLitematic(new File(litematicFolder, placement.getHash() + ".litematic"),
                    "Restored Name", 5, 2730);

            final ServerPlacement restored = ServerPlacement.fromJson(json, context);

            assertNotNull(restored);
            assertEquals("Restored Name", restored.getName());
            assertEquals(5, restored.getLitematicVersion());
            assertEquals(2730, restored.getDataVersion());
            assertTrue(restored.consumeMetadataDirty());
            assertFalse(restored.consumeMetadataDirty());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void missingLitematicFileLeavesLegacyJsonUntouched() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = newPlacement("orphanless");
            final JsonObject json = placement.toJson();
            json.remove("display_name");

            final ServerPlacement restored = ServerPlacement.fromJson(json, context);

            assertNotNull(restored);
            assertEquals("orphanless", restored.getName());
            assertEquals(SchematicPeek.UNKNOWN_VERSION, restored.getLitematicVersion());
            assertFalse(restored.consumeMetadataDirty());
        } finally {
            context.shutdown();
        }
    }

    private ServerPlacement newPlacement(final String fileName) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(),
                fileName,
                UUID.randomUUID(),
                PlayerIdentifier.MISSING_PLAYER
        );
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        return placement;
    }

    private Context newServerContext() {
        return new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
    }

    private static void writeLitematic(final File file, final String name,
                                       final int version, final int dataVersion) throws IOException {
        final NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", name);
        final NbtCompound root = new NbtCompound();
        root.put("Metadata", metadata);
        root.putInt("Version", version);
        root.putInt("MinecraftDataVersion", dataVersion);
        try (OutputStream output = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, output);
        }
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
