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
        return id.equals(PacketType.FEATURE_REQUEST.identifier)
                || id.equals(PacketType.FEATURE.identifier);
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        if (id.equals(PacketType.FEATURE_REQUEST.identifier)) {
            sendFeatures();
        } else if (id.equals(PacketType.FEATURE.identifier)) {
            final FeatureSet fs = FeatureSet.fromString(packetBuf.readString(32767));
            getPartner().setFeatureSet(fs);
            onFeatureSetReceive();
        }
    }

    protected void onFeatureSetReceive() {
        succeed();
    }

    public void requestFeatureSet() {
        getPartner().sendPacket(PacketType.FEATURE_REQUEST.identifier, new PacketByteBuf(Unpooled.buffer()), getContext());
    }

    private void sendFeatures() {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        final FeatureSet fs = getContext().getFeatureSet();
        buf.writeString(fs.toString(), 32767);
        getPartner().sendPacket(PacketType.FEATURE.identifier, buf, getContext());
    }
}
