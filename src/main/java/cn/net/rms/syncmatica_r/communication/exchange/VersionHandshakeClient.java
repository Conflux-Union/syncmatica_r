package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.exchange.FeatureExchange;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;

public class VersionHandshakeClient extends FeatureExchange {

    private String partnerVersion;

    public VersionHandshakeClient(final ExchangeTarget partner, final Context con) {
        super(partner, con);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        return type == PacketType.CONFIRM_USER
                || type == PacketType.REGISTER_VERSION
                || super.checkPacket(id, packetBuf);
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.REGISTER_VERSION) {
            // Announce Reforged capability on the new namespace as early as possible.
            final PacketByteBuf revolutionBuf = new PacketByteBuf(Unpooled.buffer());
            getPartner().sendPacket(
                    PacketType.REVOLUTION.toIdentifier(
                            cn.net.rms.syncmatica_r.communication.ProtocolFlavor.NEW
                    ),
                    revolutionBuf,
                    getContext()
            );
            final String version = packetBuf.readString(32767);
            if (!getContext().checkPartnerVersion(version)) {

                LogManager.getLogger(VersionHandshakeClient.class).info("Denying syncmatica_r join due to outdated server with local version {} and server version {}", Syncmatica.getVersion(), version);
                close(false);
            } else {
                partnerVersion = version;
                final FeatureSet fs = FeatureSet.fromVersionString(version);
                if (fs == null) {
                    requestFeatureSet();
                } else {
                    getPartner().setFeatureSet(fs);
                    onFeatureSetReceive();
                }
            }
        } else if (type == PacketType.CONFIRM_USER) {
            final int placementCount = packetBuf.readInt();
            for (int i = 0; i < placementCount; i++) {
                final ServerPlacement p = getManager().receiveMetaData(packetBuf, getPartner());
                getContext().getSyncmaticManager().addPlacement(p);
            }
             LogManager.getLogger(VersionHandshakeClient.class).info("Joining syncmatica_r server with local version {} and server version {}", Syncmatica.getVersion(), partnerVersion);
            LitematicManager.getInstance().commitLoad();
            getContext().startup();
            succeed();
        } else {
            super.handle(id, packetBuf);
        }
    }

    @Override
    public void onFeatureSetReceive() {
        final PacketByteBuf versionBuf = new PacketByteBuf(Unpooled.buffer());
        versionBuf.writeString(Syncmatica.getVersion());
        // Reply on legacy Syncmatica channel for maximum compatibility.
        getPartner().sendPacket(
                PacketType.REGISTER_VERSION.toIdentifier(
                        cn.net.rms.syncmatica_r.communication.ProtocolFlavor.LEGACY
                ),
                versionBuf,
                getContext()
        );
    }

    @Override
    public void init() {

    }

}
