package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
//$$ import cn.net.rms.syncmatica_r.communication.SyncmaticaPayload;
//$$ import net.minecraft.network.packet.CustomPayload;
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
//#if MC >= 12005
//$$         dispatch(toTransportPayload(id, packetBuf));
//#else
        try {
            if (server == null) {
                final CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(id, packetBuf);
                client.sendPacket(packet);
            } else {
                final CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(id, packetBuf);
                server.sendPacket(packet);
            }
        } catch (final Exception e) {
            // Silently ignore packet send failures for fake players/NPCs
            // This prevents crashes when mods like DonateMenu spawn fake players
            // that don't have proper network connections
            LOGGER.debug("Failed to send packet to {}: {}", persistentName, e.getMessage());
        }
//#endif
    }

//#if MC >= 12005
//$$     /** Legacy-addressed packet forced onto syncmatica_r:payload: pre-0.5.5 clients only listen there. */
//$$     public void sendPacketOnOwnTransport(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
//$$         context.getDebugService().logSendPacket(id, persistentName);
//$$         if (packetBuf == null || packetBuf.readableBytes() > ProtocolLimits.MAX_PACKET_BYTES) {
//$$             LOGGER.warn("Refusing to send oversized Syncmatica_r packet {} to {}", id, persistentName);
//$$             return;
//$$         }
//$$         dispatch(new SyncmaticaPayload(id, packetBuf));
//$$     }

//$$     private void dispatch(final CustomPayload payload) {
//$$         try {
//$$             if (server == null) {
//$$                 client.sendPacket(new CustomPayloadS2CPacket(payload));
//$$             } else {
//$$                 server.sendPacket(new CustomPayloadC2SPacket(payload));
//$$             }
//$$         } catch (final Exception e) {
//$$             // Silently ignore packet send failures for fake players/NPCs
//$$             // This prevents crashes when mods like DonateMenu spawn fake players
//$$             // that don't have proper network connections
//$$             LOGGER.debug("Failed to send packet to {}: {}", persistentName, e.getMessage());
//$$         }
//$$     }

//$$     // Inner namespace picks the transport: legacy ids ride syncmatica:main
//$$     // for upstream peers; ours stay on syncmatica_r:payload, which upstream
//$$     // drops unopened instead of crashing on an untypeable inner id.
//$$     private static CustomPayload toTransportPayload(final Identifier id, final PacketByteBuf packetBuf) {
//$$         if (ProtocolFlavor.fromNamespace(id.getNamespace()) == ProtocolFlavor.LEGACY) {
//$$             return new LegacySyncmaticaPayload(id, packetBuf);
//$$         }
//$$         return new SyncmaticaPayload(id, packetBuf);
//$$     }
//#endif

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
