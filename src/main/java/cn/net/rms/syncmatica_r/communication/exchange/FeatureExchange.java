package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.exchange.AbstractExchange;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public abstract class FeatureExchange extends AbstractExchange {

    protected FeatureExchange(final ExchangeTarget partner, final Context con) {
        super(partner, con);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        return type == PacketType.FEATURE_REQUEST
                || type == PacketType.FEATURE;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.FEATURE_REQUEST) {
            sendFeatures();
        } else if (type == PacketType.FEATURE) {
            final FeatureSet fs = FeatureSet.fromString(packetBuf.readString(32767));
            getPartner().setFeatureSet(fs);
            onFeatureSetReceive();
        }
    }

    protected void onFeatureSetReceive() {
        succeed();
    }

    public void requestFeatureSet() {
        getPartner().sendPacket(PacketType.FEATURE_REQUEST.toIdentifier(getPartner().getProtocolFlavor()), new PacketByteBuf(Unpooled.buffer()), getContext());
    }

    private void sendFeatures() {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        final FeatureSet fs = getContext().getFeatureSet();
        buf.writeString(fs.toString(), 32767);
        getPartner().sendPacket(PacketType.FEATURE.toIdentifier(getPartner().getProtocolFlavor()), buf, getContext());
    }
}
