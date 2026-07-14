package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
//$$ import cn.net.rms.syncmatica_r.communication.SyncmaticaPayload;
//$$ import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
//$$ import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
//#else
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
//#endif
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExchangeTarget {
    private static final Logger LOGGER = LogManager.getLogger(ExchangeTarget.class);
    private final String persistentName;
    private final List<Exchange> ongoingExchanges = new ArrayList<>();
    private ClientPlayNetworkHandler server = null;
    private ServerPlayNetworkHandler client = null;
    private FeatureSet features;
    private ProtocolFlavor protocolFlavor = ProtocolFlavor.LEGACY;

    public ExchangeTarget(final ClientPlayNetworkHandler server) {
        this.server = server;
        persistentName = StringUtils.getWorldOrServerName();
    }

    public ExchangeTarget(final ServerPlayNetworkHandler client) {
        this.client = client;
        persistentName = client.player.getUuidAsString();
    }

    ExchangeTarget(final String persistentName) {
        this.persistentName = persistentName;
    }

    public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
        context.getDebugService().logSendPacket(id, persistentName);
        if (packetBuf == null || packetBuf.readableBytes() > ProtocolLimits.MAX_PACKET_BYTES) {
            LOGGER.warn("Refusing to send oversized Syncmatica_r packet {} to {}", id, persistentName);
            return;
        }
        try {
            if (server == null) {
//#if MC >= 12005
//$$                 final SyncmaticaPayload payload = new SyncmaticaPayload(id, packetBuf);
//$$                 final CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(payload);
//$$                 client.sendPacket(packet);
//#else
                final CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(id, packetBuf);
                client.sendPacket(packet);
//#endif
            } else {
//#if MC >= 12005
//$$                 final SyncmaticaPayload payload = new SyncmaticaPayload(id, packetBuf);
//$$                 final CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(payload);
//$$                 server.sendPacket(packet);
//#else
                final CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(id, packetBuf);
                server.sendPacket(packet);
//#endif
            }
        } catch (final Exception e) {
            // Silently ignore packet send failures for fake players/NPCs
            // This prevents crashes when mods like DonateMenu spawn fake players
            // that don't have proper network connections
            LOGGER.debug("Failed to send packet to {}: {}", persistentName, e.getMessage());
        }
    }

    public FeatureSet getFeatureSet() {
        return features;
    }

    public void setFeatureSet(final FeatureSet f) {
        features = f;
    }

    public ProtocolFlavor getProtocolFlavor() {
        return protocolFlavor;
    }

    public void setProtocolFlavor(final ProtocolFlavor flavor) {
        protocolFlavor = flavor;
    }

    public Collection<Exchange> getExchanges() {
        return ongoingExchanges;
    }

    public String getPersistentName() {
        return persistentName;
    }
}
