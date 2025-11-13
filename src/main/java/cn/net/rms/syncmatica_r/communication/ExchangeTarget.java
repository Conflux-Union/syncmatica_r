package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExchangeTarget {
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

    public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
        context.getDebugService().logSendPacket(id, persistentName);
        if (server == null) {
            final CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(id, packetBuf);
            client.sendPacket(packet);
        } else {
            final CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(id, packetBuf);
            server.sendPacket(packet);
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
