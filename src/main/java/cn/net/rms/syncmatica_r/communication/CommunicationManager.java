package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.exchange.DownloadExchange;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider;
import cn.net.rms.syncmatica_r.extended_core.SubRegionData;
import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.MaterialProgressEntry;
import cn.net.rms.syncmatica_r.material.MaterialProgressState;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public abstract class CommunicationManager {
    protected static final BlockRotation[] rotOrdinals = BlockRotation.values();
    protected static final BlockMirror[] mirOrdinals = BlockMirror.values();
    protected final Collection<ExchangeTarget> broadcastTargets;
    protected final Map<UUID, Boolean> downloadState;
    protected final Map<UUID, Exchange> modifyState;
    protected Context context;

    protected CommunicationManager() {
        broadcastTargets = new ArrayList<>();
        downloadState = new HashMap<>();
        modifyState = new HashMap<>();
    }

    public boolean handlePacket(final Identifier id) {
        return PacketType.containsIdentifier(id);
    }

    public void onPacket(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        context.getDebugService().logReceivePacket(id);
        Exchange handler = null;
        final Collection<Exchange> potentialMessageTarget = source.getExchanges();
        if (potentialMessageTarget != null) {
            for (final Exchange target : potentialMessageTarget) {
                if (target.checkPacket(id, packetBuf)) {
                    target.handle(id, packetBuf);
                    handler = target;
                    break;
                }
            }
        }
        if (handler == null) {
            handle(source, id, packetBuf);
        } else if (handler.isFinished()) {
            notifyClose(handler);
        }
    }

    protected abstract void handle(ExchangeTarget source, Identifier id, PacketByteBuf packetBuf);

    protected abstract void handleExchange(Exchange exchange);

    public void sendMetaData(final ServerPlacement metaData, final ExchangeTarget target) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        putMetaData(metaData, buf, target);
        target.sendPacket(PacketType.REGISTER_METADATA.toIdentifier(target.getProtocolFlavor()), buf, context);
    }

    public void putMetaData(final ServerPlacement metaData, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        buf.writeUuid(metaData.getId());

        buf.writeString(SyncmaticaUtil.sanitizeFileName(metaData.getName()));
        buf.writeUuid(metaData.getHash());

        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX)) {
            buf.writeUuid(metaData.getOwner().uuid);
            buf.writeString(metaData.getOwner().getName());
            buf.writeUuid(metaData.getLastModifiedBy().uuid);
            buf.writeString(metaData.getLastModifiedBy().getName());
            if (supportsTimestamps(exchangeTarget)) {
                buf.writeLong(metaData.getCreatedAtMillis());
                buf.writeLong(metaData.getLastModifiedAtMillis());
            }
        }

        putPositionData(metaData, buf, exchangeTarget);
        putMaterialProgress(metaData, buf, exchangeTarget);
    }

    public void putPositionData(final ServerPlacement metaData, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        buf.writeBlockPos(metaData.getPosition());
        buf.writeString(metaData.getDimension());

        buf.writeInt(metaData.getRotation().ordinal());
        buf.writeInt(metaData.getMirror().ordinal());

        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX)) {
            if (metaData.getSubRegionData().getModificationData() == null) {
                buf.writeInt(0);

                return;
            }

            final Collection<SubRegionPlacementModification> regionData = metaData.getSubRegionData().getModificationData().values();
            buf.writeInt(regionData.size());

            for (final SubRegionPlacementModification subPlacement : regionData) {
                buf.writeString(subPlacement.name);
                buf.writeBlockPos(subPlacement.position);
                buf.writeInt(subPlacement.rotation.ordinal());
                buf.writeInt(subPlacement.mirror.ordinal());
            }
        }
    }

    public void putMaterialData(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        putMaterialProgress(placement, buf, exchangeTarget);
    }

    public ServerPlacement receiveMetaData(final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        final UUID id = buf.readUuid();

        final String fileName = SyncmaticaUtil.sanitizeFileName(buf.readString(32767));
        final UUID hash = buf.readUuid();

        PlayerIdentifier owner = PlayerIdentifier.MISSING_PLAYER;
        PlayerIdentifier lastModifiedBy = PlayerIdentifier.MISSING_PLAYER;
        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        final boolean hasCoreEx = targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX);
        final boolean hasTimestamps = hasCoreEx && supportsTimestamps(exchangeTarget);

        if (hasCoreEx) {
            final PlayerIdentifierProvider provider = context.getPlayerIdentifierProvider();
            owner = provider.createOrGet(
                    buf.readUuid(),
                    buf.readString(32767)
            );
            lastModifiedBy = provider.createOrGet(
                    buf.readUuid(),
                    buf.readString(32767)
            );
        }

        final ServerPlacement placement = new ServerPlacement(id, fileName, hash, owner);
        placement.setLastModifiedBy(lastModifiedBy);
        if (hasTimestamps && buf.readableBytes() >= Long.BYTES * 2) {
            placement.setCreatedAtMillis(buf.readLong());
            placement.setLastModifiedAtMillis(buf.readLong());
        }

        receivePositionData(placement, buf, exchangeTarget);
        receiveMaterialProgress(placement, buf, exchangeTarget);

        return placement;
    }

    public void receivePositionData(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {

        final BlockPos pos = buf.readBlockPos();
        final String dimensionId = buf.readString(32767);
        final BlockRotation rot = rotOrdinals[buf.readInt()];
        final BlockMirror mir = mirOrdinals[buf.readInt()];

        if (placement != null) {
            placement.move(dimensionId, pos, rot, mir);
        }

        if (exchangeTarget.getFeatureSet().hasFeature(Feature.CORE_EX)) {

            final int limit = buf.readInt();
            if (placement != null) {
                final SubRegionData subRegionData = placement.getSubRegionData();
                subRegionData.reset();
                for (int i = 0; i < limit; i++) {
                    subRegionData.modify(
                            buf.readString(32767),
                            buf.readBlockPos(),
                            rotOrdinals[buf.readInt()],
                            mirOrdinals[buf.readInt()]
                    );
                }
            } else {
                for (int i = 0; i < limit; i++) {

                    buf.readString(32767);
                    buf.readBlockPos();
                    buf.readInt();
                    buf.readInt();
                }
            }
        }
    }

    private void putMaterialProgress(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        if (partnerFeatures == null || !partnerFeatures.hasFeature(Feature.MATERIAL_PROGRESS)) {
            return;
        }
        final FeatureSet localFeatures = context.getFeatureSet();
        final boolean sendClaims = partnerFeatures.hasFeature(Feature.MATERIAL_CLAIMS)
                && localFeatures != null
                && localFeatures.hasFeature(Feature.MATERIAL_CLAIMS);
        final boolean canSend = context.isServer()
                && context.getMaterialService() != null
                && context.getMaterialService().isEnabled();
        final Collection<MaterialProgressEntry> entries = canSend ? placement.getMaterialProgress().getEntries() : Collections.emptyList();
        buf.writeInt(entries.size());
        if (!canSend) {
            return;
        }
        for (final MaterialProgressEntry entry : entries) {
            buf.writeString(entry.getKey().itemId().toString());
            buf.writeString(entry.getKey().variant());
            buf.writeInt(entry.getRequiredAmount());
            buf.writeInt(entry.getStockingSupplied());
            if (sendClaims) {
                final java.util.Collection<cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier> claimers = entry.getClaimants();
                buf.writeInt(claimers.size());
                for (final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier p : claimers) {
                    buf.writeUuid(p.uuid);
                    buf.writeString(p.getName());
                }
            }
        }
    }

    private void skipMaterialEntry(final PacketByteBuf buf, final boolean readClaims) {
        buf.readString(32767);
        buf.readString(32767);
        buf.readInt();
        buf.readInt();
        if (readClaims) {
            final int claimantCount = buf.readInt();
            for (int i = 0; i < claimantCount; i++) {
                buf.readUuid();
                buf.readString(32767);
            }
        }
    }

    private void receiveMaterialProgress(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        if (partnerFeatures == null || !partnerFeatures.hasFeature(Feature.MATERIAL_PROGRESS)) {
            return;
        }
        final FeatureSet localFeatures = context.getFeatureSet();
        final boolean readClaims = partnerFeatures.hasFeature(Feature.MATERIAL_CLAIMS)
                && localFeatures != null
                && localFeatures.hasFeature(Feature.MATERIAL_CLAIMS);
        if (buf.readableBytes() < Integer.BYTES) {
            if (!context.isServer() && placement != null) {
                placement.getMaterialProgress().clear();
            }
            return;
        }
        final int total = buf.readInt();
        if (total <= 0) {
            if (!context.isServer() && placement != null) {
                placement.getMaterialProgress().clear();
            }
            return;
        }
        if (context.isServer() && context.getMaterialService() != null) {
            for (int i = 0; i < total; i++) {
                skipMaterialEntry(buf, readClaims);
            }
            return;
        }

        if (placement == null) {

            for (int i = 0; i < total; i++) {
                skipMaterialEntry(buf, readClaims);
            }
            return;
        }
        final MaterialProgressState snapshot = new MaterialProgressState();
        for (int i = 0; i < total; i++) {
//#if MC >= 12005
//$$             final MaterialKey key = new MaterialKey(Identifier.of(buf.readString(32767)), buf.readString(32767));
//#else
            final MaterialKey key = new MaterialKey(new Identifier(buf.readString(32767)), buf.readString(32767));
//#endif
            final int required = buf.readInt();
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, required);
            entry.setStockingSupplied(buf.readInt());
            if (readClaims) {
                entry.clearClaimants();
                final int cc = buf.readInt();
                final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider provider = context.getPlayerIdentifierProvider();
                for (int c = 0; c < cc; c++) {
                    final java.util.UUID id = buf.readUuid();
                    final String name = buf.readString(32767);
                    entry.addClaimer(provider.createOrGet(id, name));
                }
            }
        }
        placement.applyMaterialProgressSnapshot(snapshot);
    }

    public void receiveMaterialData(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        receiveMaterialProgress(placement, buf, exchangeTarget);
    }

    public void download(final ServerPlacement syncmatic, final ExchangeTarget source) throws NoSuchAlgorithmException, IOException {
        if (!context.getFileStorage().getLocalState(syncmatic).isReadyForDownload()) {

            throw new IllegalArgumentException(syncmatic.toString() + " is not ready for download local state is: " + context.getFileStorage().getLocalState(syncmatic).toString());
        }
        final File toDownload = context.getFileStorage().createLocalLitematic(syncmatic);
        final Exchange downloadExchange = new DownloadExchange(syncmatic, toDownload, source, context);
        setDownloadState(syncmatic, true);
        startExchange(downloadExchange);
    }

    public void setDownloadState(final ServerPlacement syncmatic, final boolean b) {
        downloadState.put(syncmatic.getHash(), b);
    }

    public boolean getDownloadState(final ServerPlacement syncmatic) {
        return downloadState.getOrDefault(syncmatic.getHash(), false);
    }

    public void setModifier(final ServerPlacement syncmatic, final Exchange exchange) {
        modifyState.put(syncmatic.getHash(), exchange);
    }

    public Exchange getModifier(final ServerPlacement syncmatic) {
        return modifyState.get(syncmatic.getHash());
    }

    public void startExchange(final Exchange newExchange) {
        if (!broadcastTargets.contains(newExchange.getPartner())) {
            throw new IllegalArgumentException(newExchange.getPartner().toString() + " is not a valid ExchangeTarget");
        }
        startExchangeUnchecked(newExchange);
    }

    protected void startExchangeUnchecked(final Exchange newExchange) {
        newExchange.getPartner().getExchanges().add(newExchange);
        newExchange.init();
        if (newExchange.isFinished()) {
            notifyClose(newExchange);
        }
    }

    public void setContext(final Context con) {
        if (context == null) {
            context = con;
        } else {
            throw new Context.DuplicateContextAssignmentException("Duplicate Context Assignment");
        }
    }

    public void notifyClose(final Exchange e) {
        e.getPartner().getExchanges().remove(e);
        handleExchange(e);
    }

    protected boolean supportsTimestamps(final ExchangeTarget exchangeTarget) {
        final FeatureSet localFeatures = context != null ? context.getFeatureSet() : null;
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        return localFeatures != null
                && partnerFeatures != null
                && localFeatures.hasFeature(Feature.TIMESTAMPS)
                && partnerFeatures.hasFeature(Feature.TIMESTAMPS);
    }
}
