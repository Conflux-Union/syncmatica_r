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
            if (context.getSyncmaticManager().getPlacement(placement.getId()) == null
                    && context.getSyncmaticManager().getAll().size() >= ProtocolLimits.MAX_SERVER_PLACEMENTS) {
                return;
            }
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
            receiveModificationData(toModify, packetBuf, source);
            final FeatureSet featureSet = source.getFeatureSet();
            final boolean hasCoreEx = featureSet != null && featureSet.hasFeature(Feature.CORE_EX);
            final boolean hasTimestamps = hasCoreEx && supportsTimestamps(source);
            if (hasCoreEx) {
                final PlayerIdentifier lastModifiedBy = context.getPlayerIdentifierProvider().createOrGet(
                        packetBuf.readUuid(),
                        packetBuf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH)
                );
                if (toModify != null) {
                    toModify.setLastModifiedBy(lastModifiedBy);
                }
                if (hasTimestamps && packetBuf.readableBytes() >= Long.BYTES) {
                    final long ts = packetBuf.readLong();
                    if (toModify != null) {
                        toModify.setLastModifiedAtMillis(ts);
                    }
                } else if (hasTimestamps && packetBuf.readableBytes() > 0) {
                    packetBuf.skipBytes(Math.min(packetBuf.readableBytes(), Long.BYTES));
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
            final Message.MessageType guiType = mapMessageType(MessageType.valueOf(packetBuf.readString(32)));
            final String text = packetBuf.readString(ProtocolLimits.MAX_MESSAGE_LENGTH);
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
