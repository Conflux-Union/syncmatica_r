package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.LocalLitematicState;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.exchange.*;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.communication.exchange.FeatureExchange;
import cn.net.rms.syncmatica_r.communication.exchange.ShareLitematicExchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
//#if MC < 12001
import net.minecraft.text.LiteralText;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.text.Text;
//#endif
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class ServerCommunicationManager extends CommunicationManager {

    private static final Logger LOGGER = LogManager.getLogger(ServerCommunicationManager.class);

    private final Map<UUID, List<ServerPlacement>> downloadingFile = new HashMap<>();
    private final Map<ExchangeTarget, ServerPlayerEntity> playerMap = new HashMap<>();

    public ServerCommunicationManager() {
        super();
    }

    private void purgeStaleTargets() {
        final List<ExchangeTarget> stale = new ArrayList<>();
        for (final Map.Entry<ExchangeTarget, ServerPlayerEntity> e : playerMap.entrySet()) {
            if (e.getValue() == null || e.getValue().isDisconnected()) {
                stale.add(e.getKey());
            }
        }
        for (final ExchangeTarget target : stale) {
            LOGGER.debug("Purging stale ExchangeTarget: {}", target.getPersistentName());
            onPlayerLeave(target);
        }
    }

    public GameProfile getGameProfile(final ExchangeTarget exchangeTarget) {
        return playerMap.get(exchangeTarget).getGameProfile();
    }

    public void sendMessage(final ExchangeTarget client, final MessageType type, final String identifier) {
        final FeatureSet featureSet = client.getFeatureSet();
        if (featureSet != null && featureSet.hasFeature(Feature.MESSAGE)) {
            final PacketByteBuf newPacketBuf = new PacketByteBuf(Unpooled.buffer());
            newPacketBuf.writeString(type.toString());
            newPacketBuf.writeString(identifier);
            client.sendPacket(PacketType.MESSAGE.toIdentifier(client.getProtocolFlavor()), newPacketBuf, context);
        } else if (playerMap.containsKey(client)) {
            final ServerPlayerEntity player = playerMap.get(client);
            sendPlayerNotification(player, "Syncmatica_r " + type.toString() + " " + identifier);
        }
    }

    public void onPlayerJoin(final ExchangeTarget newPlayer, final ServerPlayerEntity player) {
        final VersionHandshakeServer hi = new VersionHandshakeServer(newPlayer, context);
        playerMap.put(newPlayer, player);
        final GameProfile profile = player.getGameProfile();
        context.getPlayerIdentifierProvider().updateName(SyncmaticaUtil.getProfileId(profile), SyncmaticaUtil.getProfileName(profile));
        startExchangeUnchecked(hi);
    }

    public void onPlayerLeave(final ExchangeTarget oldPlayer) {
        final Collection<Exchange> potentialMessageTarget = oldPlayer.getExchanges();
        if (potentialMessageTarget != null) {
            for (final Exchange target : potentialMessageTarget) {
                target.close(false);
                handleExchange(target);
            }
        }
        broadcastTargets.remove(oldPlayer);
        playerMap.remove(oldPlayer);
        if (context != null && context.getQuotaService() != null) {
            context.getQuotaService().clearProgressFor(oldPlayer);
        }
    }

    @Override
    protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        purgeStaleTargets();
        if (!playerMap.containsKey(source)) {
            return;
        }
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.REQUEST_LITEMATIC) {
            final UUID syncmaticaId = packetBuf.readUuid();
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(syncmaticaId);
            if (placement == null) {
                return;
            }
            final File toUpload = context.getFileStorage().getLocalLitematic(placement);
            final UploadExchange upload;
            try {
                upload = new UploadExchange(placement, toUpload, source, context);
            } catch (final FileNotFoundException e) {

                e.printStackTrace();
                return;
            }
            startExchange(upload);
            return;
        }
        if (type == PacketType.REGISTER_METADATA) {
            final ServerPlacement placement = receiveMetaData(packetBuf, source);
            if (context.getSyncmaticManager().getPlacement(placement.getId()) != null) {
                cancelShare(source, placement);

                return;
            }

            final GameProfile profile = playerMap.get(source).getGameProfile();
            final PlayerIdentifier playerIdentifier = context.getPlayerIdentifierProvider().createOrGet(profile);
            if (!placement.getOwner().equals(playerIdentifier)) {
                placement.setOwner(playerIdentifier);
                placement.setLastModifiedBy(playerIdentifier);
            }

            if (!context.getFileStorage().getLocalState(placement).isLocalFileReady()) {

                if (context.getFileStorage().getLocalState(placement) == LocalLitematicState.DOWNLOADING_LITEMATIC) {
                    downloadingFile.computeIfAbsent(placement.getHash(), key -> new ArrayList<>()).add(placement);
                    return;
                }
                try {
                    download(placement, source);
                } catch (final Exception e) {
                    e.printStackTrace();
                }

                return;
            }

            addPlacement(source, placement);

            return;
        }
        if (type == PacketType.REMOVE_SYNCMATIC) {
            final UUID placementId = packetBuf.readUuid();
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
                if (placement != null) {
                    final Exchange modifier = getModifier(placement);
                    if (modifier != null) {
                        modifier.close(true);
                        notifyClose(modifier);
                    }
                    context.getSyncmaticManager().removePlacement(placement);
                    for (final ExchangeTarget client : broadcastTargets) {
                        final PacketByteBuf newPacketBuf = new PacketByteBuf(Unpooled.buffer());
                        newPacketBuf.writeUuid(placement.getId());
                        client.sendPacket(PacketType.REMOVE_SYNCMATIC.toIdentifier(client.getProtocolFlavor()), newPacketBuf, context);
                    }
                }
        }
        if (type == PacketType.MODIFY_REQUEST) {
            final UUID placementId = packetBuf.readUuid();
            final ModifyExchangeServer modifier = new ModifyExchangeServer(placementId, source, context);
            startExchange(modifier);
        }
        if (type == PacketType.MATERIAL_CLAIM_TOGGLE) {
            final UUID placementId = packetBuf.readUuid();
            final String itemId = packetBuf.readString(32767);
            final String variant = packetBuf.readString(32767);
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
            if (placement == null) {
                return;
            }
//#if MC >= 12005
//$$             final cn.net.rms.syncmatica_r.material.MaterialKey key = new cn.net.rms.syncmatica_r.material.MaterialKey(net.minecraft.util.Identifier.of(itemId), variant);
//#else
            final cn.net.rms.syncmatica_r.material.MaterialKey key = new cn.net.rms.syncmatica_r.material.MaterialKey(new net.minecraft.util.Identifier(itemId), variant);
//#endif
            final cn.net.rms.syncmatica_r.material.MaterialProgressEntry entry = placement.getMaterialProgress().getOrCreate(key, 0);
            final net.minecraft.server.network.ServerPlayerEntity player = playerMap.get(source);
            if (player == null) {
                return;
            }
            final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier pid = context.getPlayerIdentifierProvider().createOrGet(player.getGameProfile());
            final java.util.Collection<cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier> current = entry.getClaimants();
            if (entry.hasClaimer(pid)) {

                entry.removeClaimer(pid);
            } else if (!current.isEmpty()) {

                final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier owner = current.iterator().next();
                sendMessage(source, MessageType.WARNING,
                        "Already claimed by " + owner.getName());
                return;
            } else {

                entry.addClaimer(pid);
            }
            placement.setLastModifiedBy(pid);
            placement.touchModified(System.currentTimeMillis());
            context.getSyncmaticManager().updateServerPlacement(placement);
            broadcastPlacementUpdate(placement);
        }
    }

    @Override
    protected void handleExchange(final Exchange exchange) {
        if (exchange instanceof DownloadExchange) {
            final ServerPlacement p = ((DownloadExchange) exchange).getPlacement();

            if (exchange.isSuccessful()) {
                addPlacement(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final ServerPlacement placement : downloadingFile.get(p.getHash())) {
                        addPlacement(exchange.getPartner(), placement);
                    }
                }
            } else {
                cancelShare(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final ServerPlacement placement : downloadingFile.get(p.getHash())) {
                        cancelShare(exchange.getPartner(), placement);
                    }
                }
            }

            downloadingFile.remove(p.getHash());
            return;
        }
        if (exchange instanceof VersionHandshakeServer && exchange.isSuccessful()) {
            broadcastTargets.add(exchange.getPartner());
        }
        if (exchange instanceof ModifyExchangeServer && exchange.isSuccessful()) {
            final ServerPlacement placement = ((ModifyExchangeServer) exchange).getPlacement();
            broadcastPlacementUpdate(placement);
        }
    }

    public void broadcastPlacementUpdate(final ServerPlacement placement) {
        purgeStaleTargets();
        for (final ExchangeTarget client : broadcastTargets) {
            final FeatureSet clientFeatures = client.getFeatureSet();
            if (clientFeatures != null && clientFeatures.hasFeature(Feature.MODIFY)) {
                final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeUuid(placement.getId());
                putPositionData(placement, buf, client);
                putMaterialData(placement, buf, client);
                if (clientFeatures.hasFeature(Feature.CORE_EX)) {
                    buf.writeUuid(placement.getLastModifiedBy().uuid);
                    buf.writeString(placement.getLastModifiedBy().getName());
                    if (supportsTimestamps(client)) {
                        buf.writeLong(placement.getLastModifiedAtMillis());
                    }
                }
                client.sendPacket(PacketType.MODIFY.toIdentifier(client.getProtocolFlavor()), buf, context);
                continue;
            }
            final PacketByteBuf removal = new PacketByteBuf(Unpooled.buffer());
            removal.writeUuid(placement.getId());
            client.sendPacket(PacketType.REMOVE_SYNCMATIC.toIdentifier(client.getProtocolFlavor()), removal, context);
            final PacketByteBuf registration = new PacketByteBuf(Unpooled.buffer());
            putMetaData(placement, registration, client);
            client.sendPacket(PacketType.REGISTER_METADATA.toIdentifier(client.getProtocolFlavor()), registration, context);
        }
    }

    private void addPlacement(final ExchangeTarget t, final ServerPlacement placement) {
        if (context.getSyncmaticManager().getPlacement(placement.getId()) != null) {
            cancelShare(t, placement);
            return;
        }
        context.getSyncmaticManager().addPlacement(placement);
        for (final ExchangeTarget target : broadcastTargets) {
            sendMetaData(placement, target);
        }
        broadcastPlacementUpdate(placement);
    }

    private void cancelShare(final ExchangeTarget source, final ServerPlacement placement) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(placement.getId());
        source.sendPacket(PacketType.CANCEL_SHARE.toIdentifier(source.getProtocolFlavor()), packetByteBuf, context);
    }

    private void sendPlayerNotification(final ServerPlayerEntity player, final String message) {
//#if MC >= 12001
//$$         player.sendMessageToClient(Text.literal(message), false);
//#else
        player.sendSystemMessage(new LiteralText(message), Util.NIL_UUID);
//#endif
    }
}
