package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.exchange.DownloadExchange;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.communication.exchange.VersionHandshakeClient;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.mixin_actor.ActorClientPlayNetworkHandler;
import fi.dy.masa.malilib.gui.Message;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public class ClientCommunicationManager extends CommunicationManager {

    private final ExchangeTarget server;
    private final Collection<ServerPlacement> sharing;

    public ClientCommunicationManager(final ExchangeTarget server) {
        super();
        this.server = server;
        broadcastTargets.add(server);
        sharing = new HashSet<>();
    }

    public ExchangeTarget getServer() {
        return server;
    }

    @Override
    protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.REGISTER_METADATA) {
            final ServerPlacement placement = receiveMetaData(packetBuf, source);
            context.getSyncmaticManager().addPlacement(placement);
            return;
        }
        if (type == PacketType.REMOVE_SYNCMATIC) {
            final UUID placementId = packetBuf.readUuid();
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
            if (placement != null) {
                final Exchange modifier = getModifier(placement);
                if (modifier != null) {
                    modifier.close(false);
                    notifyClose(modifier);
                }
                context.getSyncmaticManager().removePlacement(placement);
                if (LitematicManager.getInstance().isRendered(placement)) {
                    LitematicManager.getInstance().unrenderSyncmatic(placement);
                }
            }
            return;
        }
        if (type == PacketType.MODIFY) {
            final UUID placementId = packetBuf.readUuid();
            final ServerPlacement toModify = context.getSyncmaticManager().getPlacement(placementId);
            receivePositionData(toModify, packetBuf, source);
            receiveMaterialData(toModify, packetBuf, source);
            if (source.getFeatureSet().hasFeature(Feature.CORE_EX)) {
                final PlayerIdentifier lastModifiedBy = context.getPlayerIdentifierProvider().createOrGet(
                        packetBuf.readUuid(),
                        packetBuf.readString(32767)
                );
                if (toModify != null) {
                    toModify.setLastModifiedBy(lastModifiedBy);
                }
            }
            if (toModify != null) {
                LitematicManager.getInstance().updateRendered(toModify);
                context.getSyncmaticManager().updateServerPlacement(toModify);
            } else {

            }
            return;
        }
        if (type == PacketType.MESSAGE) {
            final Message.MessageType guiType = mapMessageType(MessageType.valueOf(packetBuf.readString(32767)));
            final String text = packetBuf.readString(32767);
            ScreenHelper.ifPresent(s -> s.addMessage(guiType, text));
            return;
        }
        if (type == PacketType.REGISTER_VERSION) {
            LitematicManager.clear();
            Syncmatica.restartClient();
            ActorClientPlayNetworkHandler.getInstance().packetEvent(id, packetBuf);
        }
    }

    @Override
    protected void handleExchange(final Exchange exchange) {
        if (exchange instanceof DownloadExchange && exchange.isSuccessful()) {
            LitematicManager.getInstance().renderSyncmatic(((DownloadExchange) exchange).getPlacement());
        }
    }

    @Override
    public void setDownloadState(final ServerPlacement syncmatic, final boolean state) {
        downloadState.put(syncmatic.getHash(), state);
        if (state || LitematicManager.getInstance().isRendered(syncmatic)) {
            context.getSyncmaticManager().updateServerPlacement(syncmatic);
        }
    }

    public void setSharingState(final ServerPlacement placement, final boolean state) {
        if (state) {
            sharing.add(placement);
        } else {
            sharing.remove(placement);
        }
    }

    public boolean getSharingState(final ServerPlacement placement) {
        return sharing.contains(placement);
    }

    @Override
    public void setContext(final Context con) {
        super.setContext(con);
        final VersionHandshakeClient hi = new VersionHandshakeClient(server, context);
        startExchangeUnchecked(hi);
    }

    private Message.MessageType mapMessageType(final MessageType m) {
        switch (m) {
            case SUCCESS:
                return Message.MessageType.SUCCESS;
            case WARNING:
                return Message.MessageType.WARNING;
            case ERROR:
                return Message.MessageType.ERROR;
            default:
                return Message.MessageType.INFO;
        }
    }
}
