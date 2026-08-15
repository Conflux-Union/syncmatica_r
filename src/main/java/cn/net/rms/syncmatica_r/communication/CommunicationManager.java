package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.communication.exchange.DownloadExchange;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider;
import cn.net.rms.syncmatica_r.extended_core.SubRegionData;
import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import cn.net.rms.syncmatica_r.material.MaterialAvailability;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class CommunicationManager {
    private static final Logger LOGGER = LogManager.getLogger(CommunicationManager.class);
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
        if (source == null || id == null || packetBuf == null || !handlePacket(id)) {
            return;
        }
        if (packetBuf.readableBytes() > ProtocolLimits.MAX_PACKET_BYTES) {
            LOGGER.debug("Rejected oversized Syncmatica_r packet {} with {} bytes", id, packetBuf.readableBytes());
            return;
        }
        context.getDebugService().logReceivePacket(id);
        try {
            Exchange handler = null;
            final Collection<Exchange> potentialMessageTarget = source.getExchanges();
            if (potentialMessageTarget != null) {
                for (final Exchange target : potentialMessageTarget) {
                    if (target.checkPacket(id, packetBuf)) {
                        target.handle(id, packetBuf);
                        target.markActivity();
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
        } catch (final RuntimeException exception) {
            LOGGER.debug("Rejected malformed Syncmatica_r packet {} from {}", id, source.getPersistentName(), exception);
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

        buf.writeString(SyncmaticaUtil.sanitizeFileName(metaData.getFileName()), ProtocolLimits.MAX_FILE_NAME_LENGTH);
        buf.writeUuid(metaData.getHash());

        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.DISPLAY_NAME)) {
            buf.writeString(sanitizeDisplayName(metaData.getName()), ProtocolLimits.MAX_DISPLAY_NAME_LENGTH);
        }
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX)) {
            buf.writeUuid(metaData.getOwner().uuid);
            buf.writeString(metaData.getOwner().getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            buf.writeUuid(metaData.getLastModifiedBy().uuid);
            buf.writeString(metaData.getLastModifiedBy().getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            if (supportsTimestamps(exchangeTarget)) {
                buf.writeLong(metaData.getCreatedAtMillis());
                buf.writeLong(metaData.getLastModifiedAtMillis());
            }
        }
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.VERSION)) {
            buf.writeVarInt(metaData.getLitematicVersion());
            buf.writeVarInt(metaData.getDataVersion());
        }

        putPositionData(metaData, buf, exchangeTarget);
        putMaterialProgress(metaData, buf, exchangeTarget);
        putBuildRegions(metaData, buf, exchangeTarget);
    }

    public void putPositionData(final ServerPlacement metaData, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        buf.writeBlockPos(metaData.getPosition());
        buf.writeString(metaData.getDimension(), ProtocolLimits.MAX_DIMENSION_ID_LENGTH);

        buf.writeInt(metaData.getRotation().ordinal());
        buf.writeInt(metaData.getMirror().ordinal());

        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX)) {
            if (metaData.getSubRegionData().getModificationData() == null) {
                buf.writeInt(0);

                return;
            }

            final Collection<SubRegionPlacementModification> regionData = metaData.getSubRegionData().getModificationData().values();
            final int regionCount = Math.min(regionData.size(), ProtocolLimits.MAX_SUBREGIONS);
            buf.writeInt(regionCount);

            int written = 0;
            for (final SubRegionPlacementModification subPlacement : regionData) {
                if (written++ >= regionCount) {
                    break;
                }
                buf.writeString(subPlacement.name, ProtocolLimits.MAX_SUBREGION_NAME_LENGTH);
                buf.writeBlockPos(subPlacement.position);
                buf.writeInt(subPlacement.rotation.ordinal());
                buf.writeInt(subPlacement.mirror.ordinal());
            }
        }
    }

    public void putMaterialData(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        putMaterialProgress(placement, buf, exchangeTarget);
        putBuildRegions(placement, buf, exchangeTarget);
    }

    public ServerPlacement receiveMetaData(final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        final UUID id = buf.readUuid();

        final String fileName = SyncmaticaUtil.sanitizeFileName(buf.readString(ProtocolLimits.MAX_FILE_NAME_LENGTH));
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("Placement file name is empty");
        }
        final UUID hash = buf.readUuid();

        PlayerIdentifier owner = PlayerIdentifier.MISSING_PLAYER;
        PlayerIdentifier lastModifiedBy = PlayerIdentifier.MISSING_PLAYER;
        final FeatureSet targetFeatures = exchangeTarget.getFeatureSet();
        final boolean hasCoreEx = targetFeatures != null && targetFeatures.hasFeature(Feature.CORE_EX);
        final boolean hasTimestamps = hasCoreEx && supportsTimestamps(exchangeTarget);

        String displayName = null;
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.DISPLAY_NAME)) {
            displayName = sanitizeDisplayName(buf.readString());
        }

        if (hasCoreEx) {
            final UUID ownerId = buf.readUuid();
            final String ownerName = buf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            final UUID modifierId = buf.readUuid();
            final String modifierName = buf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            if (!context.isServer()) {
                final PlayerIdentifierProvider provider = context.getPlayerIdentifierProvider();
                owner = provider.createOrGet(ownerId, ownerName);
                lastModifiedBy = provider.createOrGet(modifierId, modifierName);
            }
        }

        final ServerPlacement placement = new ServerPlacement(id, fileName, hash, owner);
        placement.setLastModifiedBy(lastModifiedBy);
        if (displayName != null && !displayName.isEmpty()) {
            placement.setDisplayName(displayName);
        }
        if (hasTimestamps && buf.readableBytes() >= Long.BYTES * 2) {
            placement.setCreatedAtMillis(buf.readLong());
            placement.setLastModifiedAtMillis(buf.readLong());
        }
        if (targetFeatures != null && targetFeatures.hasFeature(Feature.VERSION)) {
            placement.setVersion(buf.readVarInt(), buf.readVarInt());
        }

        receivePositionData(placement, buf, exchangeTarget);
        receiveMaterialProgress(placement, buf, exchangeTarget);
        receiveBuildRegions(placement, buf, exchangeTarget);

        return placement;
    }

    public void receivePositionData(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        applyPositionData(placement, readPositionData(buf, exchangeTarget));
    }

    public void receiveModificationData(final ServerPlacement placement, final PacketByteBuf buf,
                                        final ExchangeTarget exchangeTarget) {
        final PositionData positionData = readPositionData(buf, exchangeTarget);
        receiveMaterialProgress(placement, buf, exchangeTarget);
        receiveBuildRegions(placement, buf, exchangeTarget);
        if (context.isServer() && buf.isReadable()) {
            throw new IllegalArgumentException("Unexpected trailing modification data");
        }
        applyPositionData(placement, positionData);
    }

    private PositionData readPositionData(final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {

        final BlockPos pos = buf.readBlockPos();
        final String dimensionId = buf.readString(ProtocolLimits.MAX_DIMENSION_ID_LENGTH);
        final BlockRotation rot = rotOrdinals[ProtocolLimits.requireIndex(buf.readInt(), rotOrdinals.length, "rotation")];
        final BlockMirror mir = mirOrdinals[ProtocolLimits.requireIndex(buf.readInt(), mirOrdinals.length, "mirror")];

        final FeatureSet featureSet = exchangeTarget.getFeatureSet();
        final boolean hasSubRegionData = featureSet != null && featureSet.hasFeature(Feature.CORE_EX);
        final List<SubRegionPlacementModification> modifications = new ArrayList<>();
        if (hasSubRegionData) {

            final int limit = ProtocolLimits.requireCount(buf.readInt(), ProtocolLimits.MAX_SUBREGIONS, "subregion count");
            for (int i = 0; i < limit; i++) {
                modifications.add(new SubRegionPlacementModification(
                        buf.readString(ProtocolLimits.MAX_SUBREGION_NAME_LENGTH),
                        buf.readBlockPos(),
                        rotOrdinals[ProtocolLimits.requireIndex(buf.readInt(), rotOrdinals.length, "subregion rotation")],
                        mirOrdinals[ProtocolLimits.requireIndex(buf.readInt(), mirOrdinals.length, "subregion mirror")]
                ));
            }
        }
        return new PositionData(pos, dimensionId, rot, mir, modifications, hasSubRegionData);
    }

    private void applyPositionData(final ServerPlacement placement, final PositionData positionData) {
        if (placement == null) {
            return;
        }
        placement.move(
                positionData.dimensionId,
                positionData.position,
                positionData.rotation,
                positionData.mirror
        );
        if (positionData.hasSubRegionData) {
            final SubRegionData subRegionData = placement.getSubRegionData();
            subRegionData.reset();
            for (final SubRegionPlacementModification modification : positionData.modifications) {
                subRegionData.modify(modification);
            }
        }
    }

    private void putMaterialProgress(final ServerPlacement placement, final PacketByteBuf buf, final ExchangeTarget exchangeTarget) {
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        if (partnerFeatures == null || !partnerFeatures.hasFeature(Feature.MATERIAL_PROGRESS)) {
            return;
        }
        if (partnerFeatures.hasFeature(Feature.LIMIT_REPORT)) {
            buf.writeByte(resolveMaterialAvailability(placement).getCode());
        }
        final FeatureSet localFeatures = context.getFeatureSet();
        final boolean sendClaims = partnerFeatures.hasFeature(Feature.MATERIAL_CLAIMS)
                && localFeatures != null
                && localFeatures.hasFeature(Feature.MATERIAL_CLAIMS);
        final boolean canSend = context.isServer()
                && context.getMaterialService() != null
                && context.getMaterialService().isEnabled();
        final Collection<MaterialProgressEntry> entries = canSend ? placement.getMaterialProgress().getEntries() : Collections.emptyList();
        final int entryCount = Math.min(entries.size(), ProtocolLimits.MAX_MATERIAL_ENTRIES);
        buf.writeInt(entryCount);
        if (!canSend) {
            return;
        }
        int written = 0;
        for (final MaterialProgressEntry entry : entries) {
            if (written++ >= entryCount) {
                break;
            }
            buf.writeString(entry.getKey().itemId().toString(), ProtocolLimits.MAX_ITEM_ID_LENGTH);
            buf.writeString(entry.getKey().variant(), ProtocolLimits.MAX_VARIANT_LENGTH);
            buf.writeInt(entry.getRequiredAmount());
            buf.writeInt(entry.getStockingSupplied());
            if (sendClaims) {
                final java.util.Collection<cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier> claimers = entry.getClaimants();
                final int claimantCount = Math.min(claimers.size(), ProtocolLimits.MAX_CLAIMANTS_PER_MATERIAL);
                buf.writeInt(claimantCount);
                int claimantsWritten = 0;
                for (final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier p : claimers) {
                    if (claimantsWritten++ >= claimantCount) {
                        break;
                    }
                    buf.writeUuid(p.uuid);
                    buf.writeString(p.getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
                }
            }
        }
    }

    /**
     * Only the server owns this verdict; a client sharing a placement has no
     * authoritative view of the server's material limits.
     */
    private MaterialAvailability resolveMaterialAvailability(final ServerPlacement placement) {
        if (!context.isServer()) {
            return MaterialAvailability.AVAILABLE;
        }
        if (context.getMaterialService() == null || !context.getMaterialService().isEnabled()) {
            return MaterialAvailability.DISABLED;
        }
        return placement.getMaterialAvailability();
    }

    private void skipMaterialEntry(final PacketByteBuf buf, final boolean readClaims) {
        buf.readString(ProtocolLimits.MAX_ITEM_ID_LENGTH);
        buf.readString(ProtocolLimits.MAX_VARIANT_LENGTH);
        buf.readInt();
        buf.readInt();
        if (readClaims) {
            final int claimantCount = ProtocolLimits.requireCount(buf.readInt(), ProtocolLimits.MAX_CLAIMANTS_PER_MATERIAL, "claimant count");
            for (int i = 0; i < claimantCount; i++) {
                buf.readUuid();
                buf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
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
        if (partnerFeatures.hasFeature(Feature.LIMIT_REPORT) && buf.readableBytes() >= Byte.BYTES) {
            final MaterialAvailability availability = MaterialAvailability.fromCode(buf.readByte());
            if (!context.isServer() && placement != null) {
                placement.setMaterialAvailability(availability);
            }
        }
        if (buf.readableBytes() < Integer.BYTES) {
            if (!context.isServer() && placement != null) {
                placement.getMaterialProgress().clear();
            }
            return;
        }
        final int total = ProtocolLimits.requireCount(buf.readInt(), ProtocolLimits.MAX_MATERIAL_ENTRIES, "material count");
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
//$$             final MaterialKey key = new MaterialKey(Identifier.of(buf.readString(ProtocolLimits.MAX_ITEM_ID_LENGTH)), buf.readString(ProtocolLimits.MAX_VARIANT_LENGTH));
//#else
            final MaterialKey key = new MaterialKey(new Identifier(buf.readString(ProtocolLimits.MAX_ITEM_ID_LENGTH)), buf.readString(ProtocolLimits.MAX_VARIANT_LENGTH));
//#endif
            final int required = buf.readInt();
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, required);
            entry.setStockingSupplied(buf.readInt());
            if (readClaims) {
                entry.clearClaimants();
                final int cc = ProtocolLimits.requireCount(buf.readInt(), ProtocolLimits.MAX_CLAIMANTS_PER_MATERIAL, "claimant count");
                final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider provider = context.getPlayerIdentifierProvider();
                for (int c = 0; c < cc; c++) {
                    final java.util.UUID id = buf.readUuid();
                    final String name = buf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
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
        if (b) {
            downloadState.put(syncmatic.getHash(), true);
        } else {
            downloadState.remove(syncmatic.getHash());
        }
    }

    public boolean getDownloadState(final ServerPlacement syncmatic) {
        return downloadState.getOrDefault(syncmatic.getHash(), false);
    }

    public void setModifier(final ServerPlacement syncmatic, final Exchange exchange) {
        if (syncmatic == null) {
            return;
        }
        if (exchange == null) {
            modifyState.remove(syncmatic.getId());
        } else {
            modifyState.put(syncmatic.getId(), exchange);
        }
    }

    public Exchange getModifier(final ServerPlacement syncmatic) {
        return syncmatic == null ? null : modifyState.get(syncmatic.getId());
    }

    public void startExchange(final Exchange newExchange) {
        if (!broadcastTargets.contains(newExchange.getPartner())) {
            throw new IllegalArgumentException(newExchange.getPartner().toString() + " is not a valid ExchangeTarget");
        }
        startExchangeUnchecked(newExchange);
    }

    protected void startExchangeUnchecked(final Exchange newExchange) {
        if (newExchange.getPartner().getExchanges().size() >= ProtocolLimits.MAX_ACTIVE_EXCHANGES) {
            newExchange.close(true);
            handleExchange(newExchange);
            return;
        }
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

    public void tick() {
        final long now = System.currentTimeMillis();
        for (final ExchangeTarget target : new ArrayList<>(getTickTargets())) {
            final Collection<Exchange> exchanges = target.getExchanges();
            if (exchanges == null || exchanges.isEmpty()) {
                continue;
            }
            final List<Exchange> expired = new ArrayList<>();
            for (final Exchange exchange : exchanges) {
                if (exchange.isTimedOut(now)) {
                    expired.add(exchange);
                }
            }
            for (final Exchange exchange : expired) {
                exchange.close(true);
                notifyClose(exchange);
            }
        }
    }

    protected Collection<ExchangeTarget> getTickTargets() {
        return broadcastTargets;
    }

    /**
     * Display names travel unbounded from legacy syncmatica peers; strip
     * control characters and clamp to the local storage limit.
     */
    protected static String sanitizeDisplayName(final String rawName) {
        if (rawName == null) {
            return "";
        }
        final StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < rawName.length();) {
            final int c = rawName.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isISOControl(c)) {
                continue;
            }
            if (cleaned.length() + Character.charCount(c) > ProtocolLimits.MAX_DISPLAY_NAME_LENGTH) {
                break;
            }
            cleaned.appendCodePoint(c);
        }
        return cleaned.toString().trim();
    }

    /**
     * Build regions are appended after the material section, and every new
     * section has to keep being appended at the end: a peer whose feature set
     * stops earlier stops reading there too, so anything inserted in front of a
     * section it does know about would desynchronise the stream. Only the server
     * writes real data; a client echoing metadata back writes an empty section so
     * the byte shape stays the same in both directions.
     */
    private void putBuildRegions(final ServerPlacement placement, final PacketByteBuf buf,
                                 final ExchangeTarget exchangeTarget) {
        if (!supportsBuildManagement(exchangeTarget)) {
            return;
        }
        final Collection<BuildRegion> regions = context.isServer()
                ? placement.getBuildRegions().getRegions()
                : Collections.emptyList();
        final int regionCount = Math.min(regions.size(), ProtocolLimits.MAX_REGION_ENTRIES);
        buf.writeInt(regionCount);
        int written = 0;
        for (final BuildRegion region : regions) {
            if (written++ >= regionCount) {
                break;
            }
            buf.writeString(region.getRegionName(), ProtocolLimits.MAX_SUBREGION_NAME_LENGTH);
            buf.writeLong(region.getRequiredBlocks());
            final Collection<PlayerIdentifier> claimers = region.getClaimants();
            final int claimantCount = Math.min(claimers.size(), ProtocolLimits.MAX_CLAIMANTS_PER_REGION);
            buf.writeInt(claimantCount);
            int claimantsWritten = 0;
            for (final PlayerIdentifier claimer : claimers) {
                if (claimantsWritten++ >= claimantCount) {
                    break;
                }
                buf.writeUuid(claimer.uuid);
                buf.writeString(claimer.getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            }
        }
    }

    private void receiveBuildRegions(final ServerPlacement placement, final PacketByteBuf buf,
                                     final ExchangeTarget exchangeTarget) {
        if (!supportsBuildManagement(exchangeTarget) || buf.readableBytes() < Integer.BYTES) {
            return;
        }
        final int total = ProtocolLimits.requireCount(buf.readInt(), ProtocolLimits.MAX_REGION_ENTRIES, "region count");
        // The server owns this state, so a client's copy is read only to be
        // discarded — but it still has to be consumed to leave the buffer aligned.
        final boolean apply = !context.isServer() && placement != null;
        final BuildRegionState snapshot = apply ? new BuildRegionState() : null;
        final PlayerIdentifierProvider provider = context.getPlayerIdentifierProvider();
        for (int i = 0; i < total; i++) {
            final String regionName = buf.readString(ProtocolLimits.MAX_SUBREGION_NAME_LENGTH);
            final long requiredBlocks = buf.readLong();
            final int claimantCount = ProtocolLimits.requireCount(
                    buf.readInt(), ProtocolLimits.MAX_CLAIMANTS_PER_REGION, "region claimant count");
            final BuildRegion region = apply ? snapshot.getOrCreate(regionName, requiredBlocks) : null;
            for (int claimant = 0; claimant < claimantCount; claimant++) {
                final UUID claimerId = buf.readUuid();
                final String claimerName = buf.readString(ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
                if (region != null) {
                    region.addClaimer(provider.createOrGet(claimerId, claimerName));
                }
            }
        }
        if (apply) {
            placement.applyBuildRegionSnapshot(snapshot);
        }
    }

    private boolean supportsBuildManagement(final ExchangeTarget exchangeTarget) {
        final FeatureSet localFeatures = context != null ? context.getFeatureSet() : null;
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        return localFeatures != null
                && partnerFeatures != null
                && localFeatures.hasFeature(Feature.BUILD_MANAGEMENT)
                && partnerFeatures.hasFeature(Feature.BUILD_MANAGEMENT);
    }

    protected boolean supportsTimestamps(final ExchangeTarget exchangeTarget) {
        final FeatureSet localFeatures = context != null ? context.getFeatureSet() : null;
        final FeatureSet partnerFeatures = exchangeTarget.getFeatureSet();
        return localFeatures != null
                && partnerFeatures != null
                && localFeatures.hasFeature(Feature.TIMESTAMPS)
                && partnerFeatures.hasFeature(Feature.TIMESTAMPS);
    }

    private static final class PositionData {
        private final BlockPos position;
        private final String dimensionId;
        private final BlockRotation rotation;
        private final BlockMirror mirror;
        private final List<SubRegionPlacementModification> modifications;
        private final boolean hasSubRegionData;

        private PositionData(final BlockPos position, final String dimensionId,
                             final BlockRotation rotation, final BlockMirror mirror,
                             final List<SubRegionPlacementModification> modifications,
                             final boolean hasSubRegionData) {
            this.position = position;
            this.dimensionId = dimensionId;
            this.rotation = rotation;
            this.mirror = mirror;
            this.modifications = modifications;
            this.hasSubRegionData = hasSubRegionData;
        }
    }
}
