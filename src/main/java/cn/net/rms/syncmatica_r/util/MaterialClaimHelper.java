package cn.net.rms.syncmatica_r.util;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MaterialClaimHelper {

    private static final Logger LOGGER = LogManager.getLogger();

    private MaterialClaimHelper() {
        // Utility class
    }

    /**
     * Unclaim all materials for the current player in a single placement
     * @return number of materials unclaimed, or -1 if operation failed
     */
    public static int unclaimAllMaterials(final ServerPlacement placement) {
        if (placement == null) {
            return -1;
        }
        
        final String playerName = getCurrentPlayerName();
        if (playerName == null || playerName.isEmpty()) {
            return -1;
        }

        final ExchangeTarget server = getServer();
        if (server == null) {
            return -1;
        }

        final Context context = LitematicManager.getInstance().getActiveContext();
        return unclaimMaterialsInPlacement(placement, playerName, server, context);
    }

    /**
     * Unclaim all materials for the current player across multiple placements
     * @return total number of materials unclaimed, or -1 if operation failed
     */
    public static int unclaimAllMaterials(final Collection<ServerPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return -1;
        }

        final String playerName = getCurrentPlayerName();
        if (playerName == null || playerName.isEmpty()) {
            return -1;
        }

        final ExchangeTarget server = getServer();
        if (server == null) {
            return -1;
        }

        final Context context = LitematicManager.getInstance().getActiveContext();
        int totalUnclaimed = 0;
        for (final ServerPlacement placement : placements) {
            if (placement != null) {
                final int unclaimed = unclaimMaterialsInPlacement(placement, playerName, server, context);
                if (unclaimed >= 0) {
                    totalUnclaimed += unclaimed;
                }
            }
        }
        return totalUnclaimed;
    }

    private static int unclaimMaterialsInPlacement(
            final ServerPlacement placement,
            final String playerName,
            final ExchangeTarget server,
            final Context context) {
        
        if (placement.getMaterialList() == null) {
            return 0;
        }

        final List<SyncmaticaMaterialEntry> entries = placement.getMaterialList().getEntries();
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        // Collect entries that need to be unclaimed to reduce network traffic
        final List<SyncmaticaMaterialEntry> toUnclaim = new ArrayList<>();
        for (final SyncmaticaMaterialEntry entry : entries) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }

            // Only unclaim materials claimed by current player
            if (entry.getClaimers().contains(playerName)) {
                toUnclaim.add(entry);
            }
        }

        // Send unclaim packets for collected entries
        int successCount = 0;
        for (final SyncmaticaMaterialEntry entry : toUnclaim) {
            if (sendUnclaimPacket(placement, entry, server, context)) {
                successCount++;
            }
        }
        return successCount;
    }

    private static boolean sendUnclaimPacket(
            final ServerPlacement placement,
            final SyncmaticaMaterialEntry entry,
            final ExchangeTarget server,
            final Context context) {

        try {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            buf.writeString(entry.getKey().itemId().toString());
            buf.writeString(entry.getKey().variant());
            server.sendPacket(PacketType.MATERIAL_CLAIM_TOGGLE.toIdentifier(server.getProtocolFlavor()), buf, context);
            return true;
        } catch (final Exception e) {
            LOGGER.error("Failed to send unclaim packet for {}: {}", entry.getKey().itemId(), e.getMessage());
            return false;
        }
    }

    private static String getCurrentPlayerName() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getGameProfile() == null) {
            return null;
        }
        return SyncmaticaUtil.getProfileName(client.player.getGameProfile());
    }

    private static ExchangeTarget getServer() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (!(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return null;
        }
        return ((ClientCommunicationManager) context.getCommunicationManager()).getServer();
    }
}
