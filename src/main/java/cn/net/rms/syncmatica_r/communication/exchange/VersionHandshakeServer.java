package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.ProtocolFlavor;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.exchange.FeatureExchange;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;

import java.util.Collection;

public class VersionHandshakeServer extends FeatureExchange {

    private String partnerVersion;
    private boolean awaitingFeatureSet;

    public VersionHandshakeServer(final ExchangeTarget partner, final Context con) {
        super(partner, con);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.REGISTER_VERSION
                || type == PacketType.REVOLUTION
                || type == PacketType.FEATURE_REQUEST) {
            return true;
        }
        return type == PacketType.FEATURE && partnerVersion != null && awaitingFeatureSet;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.REGISTER_VERSION) {
            partnerVersion = packetBuf.readString(ProtocolLimits.MAX_VERSION_LENGTH);
            if (!getContext().checkPartnerVersion(partnerVersion)) {
                LogManager.getLogger(VersionHandshakeServer.class).info("Denying syncmatica_r join due to outdated client with local version {} and client version {}", Syncmatica.getVersion(), partnerVersion);

                close(false);
                return;
            }
            final FeatureSet fs = FeatureSet.fromVersionString(partnerVersion);
            if (fs == null) {
                awaitingFeatureSet = true;
                requestFeatureSet();
            } else {
                getPartner().setFeatureSet(fs);
                onFeatureSetReceive();
            }
        } else if (type == PacketType.REVOLUTION) {
            getPartner().setProtocolFlavor(ProtocolFlavor.NEW);
        } else {
            super.handle(id, packetBuf);
        }

    }

    @Override
    public void onFeatureSetReceive() {
        if (partnerVersion == null) {
            close(false);
            return;
        }
        awaitingFeatureSet = false;
        LogManager.getLogger(VersionHandshakeServer.class).info("Syncmatica_r client joining with local version {} and client version {}", Syncmatica.getVersion(), partnerVersion);
        sendInitialState();
        succeed();
        if (getPartner().getProtocolFlavor() == ProtocolFlavor.LEGACY) {
            final ServerCommunicationManager serverComms =
                    (ServerCommunicationManager) getContext().getCommunicationManager();
            serverComms.sendMessage(
                    getPartner(),
                    MessageType.WARNING,
                    "This server uses the Reforged version of Syncmatica (syncmatica_r) and you are using the original Syncmatica. There may be compatibility issues."
            );
        } else if (getContext().getFeatureSet().hasFeature(Feature.NAMED_STOCKING_AREAS)
                && getPartner().getFeatureSet() != null
                && !getPartner().getFeatureSet().hasFeature(Feature.NAMED_STOCKING_AREAS)) {
            // Both languages are inlined on purpose: a pre-registry client has
            // no lang entry for a 0.5.0 key and may run either locale, so a
            // translation key would render raw. The server-feature guard
            // mirrors the protocol gate so no prompt fires when the server
            // itself dropped the feature.
            final String serverVersion = Syncmatica.getVersion();
            final ServerCommunicationManager serverComms =
                    (ServerCommunicationManager) getContext().getCommunicationManager();
            serverComms.sendMessage(
                    getPartner(),
                    MessageType.WARNING,
                    "本服务器启用了命名备货区（syncmatica_r " + serverVersion
                            + "），你的客户端（" + partnerVersion
                            + "）版本过旧，更新前备货区为只读。"
                            + " | This server uses named stocking areas (syncmatica_r " + serverVersion
                            + ") and your client (" + partnerVersion
                            + ") is outdated: stocking areas are read-only until you update syncmatica_r."
            );
        }
    }

    protected void sendInitialState() {
        final Collection<ServerPlacement> placements = getContext().getSyncmaticManager().getAll();
        for (final ServerPlacement placement : placements) {
            getManager().sendMetaData(placement, getPartner());
        }
        final PacketByteBuf confirmationBuf = new PacketByteBuf(Unpooled.buffer());
        confirmationBuf.writeInt(0);
        getPartner().sendPacket(
                PacketType.CONFIRM_USER.toIdentifier(getPartner().getProtocolFlavor()),
                confirmationBuf,
                getContext()
        );
    }

    @Override
    public void init() {
        final PacketByteBuf newBuf = new PacketByteBuf(Unpooled.buffer());
        newBuf.writeString(Syncmatica.getVersion(), ProtocolLimits.MAX_VERSION_LENGTH);
        // Initial handshake always uses legacy Syncmatica channel for compatibility.
        getPartner().sendPacket(PacketType.REGISTER_VERSION.toIdentifier(ProtocolFlavor.LEGACY), newBuf, getContext());
    }
}
