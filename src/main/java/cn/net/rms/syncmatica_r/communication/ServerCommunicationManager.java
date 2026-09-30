package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.LocalLitematicState;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.exchange.*;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.communication.exchange.FeatureExchange;
import cn.net.rms.syncmatica_r.communication.exchange.ShareLitematicExchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.material.StockingAreaRegistry;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import cn.net.rms.syncmatica_r.schematic.SchematicPeeker;
import cn.net.rms.syncmatica_r.service.BuildService;
import cn.net.rms.syncmatica_r.service.MaterialService;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
//#if MC < 12001
import net.minecraft.text.LiteralText;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.text.Text;
//#endif
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import me.lucko.fabric.api.permissions.v0.Permissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ServerCommunicationManager extends CommunicationManager {

    private static final Logger LOGGER = LogManager.getLogger(ServerCommunicationManager.class);

    private final Map<UUID, List<PendingShare>> downloadingFile = new HashMap<>();
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
        final ServerPlayerEntity player = playerMap.get(exchangeTarget);
        return player == null ? null : player.getGameProfile();
    }

    public void sendMessage(final ExchangeTarget client, final MessageType type, final String identifier) {
        sendMessage(client, type, identifier, "");
    }

    /**
     * The detail carries language-neutral diagnostics (byte counts, limits) that
     * cannot be expressed by the translation key alone. {@link MessageCodec}
     * decides whether the peer can read it.
     */
    public void sendMessage(final ExchangeTarget client, final MessageType type, final String identifier,
                            final String detail) {
        final FeatureSet featureSet = client.getFeatureSet();
        final String trimmedDetail = detail == null ? "" : detail;
        if (featureSet != null && featureSet.hasFeature(Feature.MESSAGE)) {
            final PacketByteBuf newPacketBuf = MessageCodec.encode(featureSet, type, identifier, trimmedDetail);
            client.sendPacket(PacketType.MESSAGE.toIdentifier(client.getProtocolFlavor()), newPacketBuf, context);
        } else if (playerMap.containsKey(client)) {
            final ServerPlayerEntity player = playerMap.get(client);
            final String suffix = trimmedDetail.isEmpty() ? "" : " (" + trimmedDetail + ")";
            sendPlayerNotification(player, "Syncmatica_r " + type.toString() + " " + identifier + suffix);
        }
    }

    /**
     * Reaches the player behind a placement (usually its owner) without the
     * caller having to hold on to an {@link ExchangeTarget}.
     */
    public void sendMessageToPlayer(final UUID playerId, final MessageType type, final String identifier,
                                    final String detail) {
        if (playerId == null) {
            return;
        }
        for (final Map.Entry<ExchangeTarget, ServerPlayerEntity> entry : playerMap.entrySet()) {
            final ServerPlayerEntity player = entry.getValue();
            if (player != null && playerId.equals(SyncmaticaUtil.getProfileId(player.getGameProfile()))) {
                sendMessage(entry.getKey(), type, identifier, detail);
                return;
            }
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
            for (final Exchange target : new ArrayList<>(potentialMessageTarget)) {
                target.close(false);
                notifyClose(target);
            }
        }
        broadcastTargets.remove(oldPlayer);
        playerMap.remove(oldPlayer);
    }

    @Override
    protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        purgeStaleTargets();
        if (!playerMap.containsKey(source)) {
            return;
        }
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == null || !broadcastTargets.contains(source)) {
            return;
        }
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
            } catch (final UploadExchange.TransferLimitExceededException tooLarge) {
                LOGGER.warn("Refusing to serve '{}' to {}: {}",
                        placement.getName(), source.getPersistentName(), tooLarge.getMessage());
                cancelDownload(source, placement);
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.download_exceeds_size_limit",
                        SyncmaticaUtil.formatMegabytes(tooLarge.getFileBytes())
                                + " > " + SyncmaticaUtil.formatMegabytes(tooLarge.getLimitBytes()));
                return;
            } catch (final IOException e) {
                LOGGER.warn("Failed to serve litematic '{}' to {}", placement.getName(), source.getPersistentName(), e);
                cancelDownload(source, placement);
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.file_unavailable");
                return;
            }
            startExchange(upload);
            return;
        }
        if (type == PacketType.REGISTER_METADATA) {
            if (!canShare(source)) {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
                return;
            }
            if (context.getSyncmaticManager().getAll().size() >= ProtocolLimits.MAX_SERVER_PLACEMENTS) {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.too_many_placements");
                return;
            }
            final ServerPlacement placement = receiveMetaData(packetBuf, source);
            if (context.getSyncmaticManager().getPlacement(placement.getId()) != null) {
                cancelShare(source, placement);

                return;
            }

            final GameProfile profile = playerMap.get(source).getGameProfile();
            final PlayerIdentifier playerIdentifier = context.getPlayerIdentifierProvider().createOrGet(profile);
            placement.setOwner(playerIdentifier);
            placement.setLastModifiedBy(playerIdentifier);
            final long now = System.currentTimeMillis();
            placement.setCreatedAtMillis(now);
            placement.setLastModifiedAtMillis(now);

            final LocalLitematicState localState = context.getFileStorage().getLocalState(placement);
            if (!localState.isLocalFileReady()) {

                if (localState == LocalLitematicState.DOWNLOADING_LITEMATIC) {
                    final List<PendingShare> pending = downloadingFile.computeIfAbsent(
                            placement.getHash(),
                            key -> new ArrayList<>()
                    );
                    if (pending.size() >= ProtocolLimits.MAX_ACTIVE_EXCHANGES) {
                        cancelShare(source, placement);
                        return;
                    }
                    pending.add(new PendingShare(source, placement));
                    return;
                }
                try {
                    download(placement, source);
                } catch (final Exception e) {
                    LOGGER.warn("Failed to start litematic download from {}", source.getPersistentName(), e);
                    cancelShare(source, placement);
                }

                return;
            }

            addPlacement(source, placement);

            return;
        }
        if (type == PacketType.REMOVE_SYNCMATIC) {
            final UUID placementId = packetBuf.readUuid();
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
            if (placement != null && canManage(source, placement)) {
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
            } else if (placement != null) {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            }
            return;
        }
        if (type == PacketType.MODIFY_REQUEST) {
            final UUID placementId = packetBuf.readUuid();
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
            if (placement == null || !canManage(source, placement)) {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
                denyModification(source, placementId);
                return;
            }
            final ModifyExchangeServer modifier = new ModifyExchangeServer(placementId, source, context);
            startExchange(modifier);
            return;
        }
        if (type == PacketType.MATERIAL_CLAIM_TOGGLE) {
            final UUID placementId = packetBuf.readUuid();
            final String itemId = packetBuf.readString(ProtocolLimits.MAX_ITEM_ID_LENGTH);
            final String variant = packetBuf.readString(ProtocolLimits.MAX_VARIANT_LENGTH);
            final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
            final net.minecraft.server.network.ServerPlayerEntity player = playerMap.get(source);
            final java.util.Optional<net.minecraft.util.Identifier> parsedItemId = IdentifierUtil.tryParse(itemId);
            if (!parsedItemId.isPresent()) {
                return;
            }
            final cn.net.rms.syncmatica_r.material.MaterialKey key =
                    new cn.net.rms.syncmatica_r.material.MaterialKey(parsedItemId.get(), variant);
            MaterialClaimPacketAdapter.handleToggle(
                    context.getMaterialService(),
                    placement,
                    key,
                    () -> player == null
                            ? null
                            : context.getPlayerIdentifierProvider().createOrGet(player.getGameProfile()),
                    () -> player != null
                            && Permissions.check(player, PlacementAccessPolicy.CLAIM_PERMISSION, true),
                    (messageType, identifier) -> sendMessage(source, messageType, identifier)
            );
            return;
        }
        if (type == PacketType.BUILD_REGION_CLAIM) {
            handleBuildRegionClaim(source, packetBuf);
            return;
        }
        if (type == PacketType.SET_STOCKING_AREA) {
            handleSetStockingArea(source, packetBuf);
            return;
        }
        if (type == PacketType.STOCKING_AREA_MANAGE) {
            handleStockingAreaManage(source, packetBuf);
            return;
        }
    }

    /**
     * Claiming a sub-region records who is responsible for building it. The rule
     * about who may hold one lives in {@link BuildService}; this only resolves the
     * actors and turns the outcome into a reply.
     */
    private void handleBuildRegionClaim(final ExchangeTarget source, final PacketByteBuf packetBuf) {
        final UUID placementId = packetBuf.readUuid();
        final String regionName = packetBuf.readString(ProtocolLimits.MAX_SUBREGION_NAME_LENGTH);

        final BuildService buildService = context.getBuildService();
        if (buildService == null || !buildService.isEnabled()) {
            return;
        }
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return;
        }
        final ServerPlayerEntity player = playerMap.get(source);
        if (player == null || !Permissions.check(player, PlacementAccessPolicy.BUILD_CLAIM_PERMISSION, true)) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        final PlayerIdentifier claimer = context.getPlayerIdentifierProvider().createOrGet(player.getGameProfile());
        if (buildService.toggleClaim(placement, regionName, claimer) == BuildService.ClaimOutcome.ALREADY_CLAIMED) {
            final PlayerIdentifier owner = buildService.getClaimant(placement, regionName);
            sendMessage(source, MessageType.WARNING, "syncmatica_r.error.build.region_taken",
                    owner == null ? "" : owner.getName());
        }
    }

    /**
     * Pre-registry clients push raw coordinates for the stocking area; the
     * named-registry server no longer accepts anonymous areas, so the sender
     * is told to upgrade instead of silently losing the selection.
     */
    private void handleSetStockingArea(final ExchangeTarget source, final PacketByteBuf packetBuf) {
        packetBuf.readerIndex(packetBuf.writerIndex());
        final ServerPlayerEntity player = playerMap.get(source);
        if (player != null) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area.unsupported");
        }
    }

    /**
     * GUI-driven registry management carrying the same operations as the
     * {@code /syncmatica_r stockingArea} commands, with the same permission
     * matrix: CREATE needs the command permission, UPDATE/UPDATE_DEFAULT/
     * DELETE need the area creator (the server-owned default entry has none,
     * so it stays elevated-only) and BIND needs the placement's stocking-area
     * manager. Dimensions always come from the sending player's world, so a
     * crafted payload cannot aim an area at a dimension the player is not in.
     */
    private void handleStockingAreaManage(final ExchangeTarget source, final PacketByteBuf packetBuf) {
        final ServerPlayerEntity player = playerMap.get(source);
        final MaterialService materialService = context.getMaterialService();
        if (player == null || materialService == null || !materialService.isEnabled()) {
            return;
        }
        final byte opcode = packetBuf.readByte();
        switch (opcode) {
            case StockingAreaManageOpcodes.OP_CREATE:
                handleStockingAreaCreate(source, player, materialService, packetBuf);
                return;
            case StockingAreaManageOpcodes.OP_UPDATE:
                handleStockingAreaUpdate(source, player, materialService, packetBuf);
                return;
            case StockingAreaManageOpcodes.OP_DELETE:
                handleStockingAreaDelete(source, player, materialService, packetBuf);
                return;
            case StockingAreaManageOpcodes.OP_BIND:
                handleStockingAreaBind(source, player, materialService, packetBuf);
                return;
            case StockingAreaManageOpcodes.OP_UPDATE_DEFAULT:
                handleStockingAreaUpdateDefault(source, player, materialService, packetBuf);
                return;
            default:
        }
    }

    private void handleStockingAreaCreate(final ExchangeTarget source, final ServerPlayerEntity player,
                                          final MaterialService materialService, final PacketByteBuf buf) {
        if (!hasCommandPermission(player)) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        final String name = buf.readString(ProtocolLimits.MAX_STOCKING_AREA_NAME_LENGTH);
        final boolean hasBind = buf.readBoolean();
        final UUID bindPlacementId = hasBind ? buf.readUuid() : null;
        final BlockPos first = buf.readBlockPos();
        final BlockPos second = buf.readBlockPos();
        final UUID ownerPlayerId = SyncmaticaUtil.getProfileId(player.getGameProfile());
        final StockingAreaDefinition definition = new StockingAreaDefinition(playerDimension(player), first, second);
        final StockingAreaRegistry.CreateOutcome outcome = materialService.createStockingArea(name, definition, ownerPlayerId);
        String createdName = outcome == StockingAreaRegistry.CreateOutcome.CREATED ? name : null;
        if (createdName == null && hasBind
                && (outcome == StockingAreaRegistry.CreateOutcome.INVALID_NAME
                || outcome == StockingAreaRegistry.CreateOutcome.RESERVED_NAME)) {
            createdName = createUnderPlacementSlug(materialService, definition, ownerPlayerId, bindPlacementId);
        }
        if (createdName == null) {
            switch (outcome) {
                case INVALID_NAME:
                    sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_invalid_name", name);
                    return;
                case DUPLICATE_NAME:
                case RESERVED_NAME:
                    // The reserved default name is taken by definition, so the
                    // name-taken reply covers both outcomes.
                    sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_name_taken", name);
                    return;
                case TOO_LARGE:
                    sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_too_large", name);
                    return;
                default:
                    return;
            }
        }
        final StockingAreaRegistry.Entry entry = materialService.getStockingAreaRegistry().getByName(createdName);
        sendMessage(source, MessageType.SUCCESS, "syncmatica_r.success.stocking_area_created", createdName);
        if (hasBind) {
            bindStockingArea(source, player, materialService, bindPlacementId, entry.getId());
        }
    }

    /**
     * GUI creates are named after the placement, whose display name may not be
     * a valid area name (mainstream zh_cn names, spaces, the reserved default
     * name); those retry under a placement-id slug — the same fallback the
     * registry migration uses — so the "from selection" button still works.
     * Pure creates never call this, keeping strict validation for typo'd
     * command-style names.
     *
     * @return the name the area was created under, or null when the retry
     *         itself failed (e.g. the area is oversized).
     */
    static String createUnderPlacementSlug(final MaterialService materialService,
                                           final StockingAreaDefinition definition,
                                           final UUID ownerPlayerId,
                                           final UUID placementId) {
        String name = "area-" + placementId.toString().replace("-", "").substring(0, 8);
        final String taken = materialService.getStockingAreaRegistry().findUniqueIdFor(name);
        if (taken != null) {
            name = taken;
        }
        return materialService.createStockingArea(name, definition, ownerPlayerId)
                == StockingAreaRegistry.CreateOutcome.CREATED ? name : null;
    }

    private void handleStockingAreaUpdate(final ExchangeTarget source, final ServerPlayerEntity player,
                                          final MaterialService materialService, final PacketByteBuf buf) {
        updateStockingAreaEntry(source, player, materialService,
                materialService.getStockingAreaRegistry().getById(buf.readUuid()),
                buf.readBlockPos(), buf.readBlockPos());
    }

    private void handleStockingAreaUpdateDefault(final ExchangeTarget source, final ServerPlayerEntity player,
                                                 final MaterialService materialService, final PacketByteBuf buf) {
        updateStockingAreaEntry(source, player, materialService,
                materialService.getStockingAreaRegistry().getDefaultArea(),
                buf.readBlockPos(), buf.readBlockPos());
    }

    private void updateStockingAreaEntry(final ExchangeTarget source, final ServerPlayerEntity player,
                                         final MaterialService materialService,
                                         final StockingAreaRegistry.Entry entry,
                                         final BlockPos first, final BlockPos second) {
        if (entry == null) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
            return;
        }
        if (!canManageStockingAreaEntry(player, entry)) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        final StockingAreaRegistry.UpdateOutcome outcome = materialService.updateStockingArea(
                entry.getId(), new StockingAreaDefinition(playerDimension(player), first, second));
        if (outcome != StockingAreaRegistry.UpdateOutcome.UPDATED) {
            if (outcome == StockingAreaRegistry.UpdateOutcome.TOO_LARGE) {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_too_large", entry.getName());
            } else {
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
            }
            return;
        }
        // Update keeps the references intact, so the affected placements are
        // queried after the mutation.
        materialService.rescanPlacements(
                serverOf(player), materialService.getPlacementsReferencing(entry.getId()));
        sendMessage(source, MessageType.SUCCESS, "syncmatica_r.success.stocking_area_updated", entry.getName());
    }

    private void handleStockingAreaDelete(final ExchangeTarget source, final ServerPlayerEntity player,
                                          final MaterialService materialService, final PacketByteBuf buf) {
        final UUID areaId = buf.readUuid();
        final boolean force = buf.readBoolean();
        final StockingAreaRegistry.Entry entry = materialService.getStockingAreaRegistry().getById(areaId);
        if (entry == null) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
            return;
        }
        if (!canManageStockingAreaEntry(player, entry)) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        // Captured before the delete: a force delete unbinds the referencing
        // placements, so asking afterwards would always yield an empty list.
        final List<ServerPlacement> referencing = materialService.getPlacementsReferencing(areaId);
        final MaterialService.StockingAreaDeleteOutcome outcome =
                materialService.deleteStockingArea(areaId, force);
        switch (outcome) {
            case DELETED:
                materialService.rescanPlacements(serverOf(player), referencing);
                sendMessage(source, MessageType.SUCCESS, "syncmatica_r.success.stocking_area_deleted", entry.getName());
                return;
            case IN_USE:
                final String projects = referencing.stream()
                        .map(ServerPlacement::getName)
                        .collect(Collectors.joining(", "));
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_in_use",
                        clampDetail(entry.getName() + ": " + projects));
                return;
            case RESERVED_NAME:
                // The reserved default entry is not deletable through this
                // packet; the unknown-area reply keeps the key set small.
            case NOT_FOUND:
            default:
                sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
        }
    }

    private void handleStockingAreaBind(final ExchangeTarget source, final ServerPlayerEntity player,
                                        final MaterialService materialService, final PacketByteBuf buf) {
        final UUID placementId = buf.readUuid();
        final boolean hasArea = buf.readBoolean();
        bindStockingArea(source, player, materialService, placementId,
                hasArea ? buf.readUuid() : null);
    }

    /** null areaId clears the binding; the placement then falls back to the default area. */
    private void bindStockingArea(final ExchangeTarget source, final ServerPlayerEntity player,
                                  final MaterialService materialService,
                                  final UUID placementId, final UUID areaId) {
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_placement");
            return;
        }
        if (!canManageStockingArea(source, placement, materialService)) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        final StockingAreaRegistry.Entry entry = areaId == null
                ? null
                : materialService.getStockingAreaRegistry().getById(areaId);
        if (areaId != null && entry == null) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
            return;
        }
        final MaterialService.StockingAreaBindOutcome outcome =
                materialService.bindStockingArea(placement, areaId);
        if (outcome == MaterialService.StockingAreaBindOutcome.UNKNOWN_AREA) {
            sendMessage(source, MessageType.ERROR, "syncmatica_r.error.stocking_area_unknown_area");
            return;
        }
        materialService.scanNow(serverOf(player), placement);
        if (entry == null) {
            sendMessage(source, MessageType.SUCCESS, "syncmatica_r.success.stocking_area_cleared",
                    clampDetail(placement.getName()));
        } else {
            sendMessage(source, MessageType.SUCCESS, "syncmatica_r.success.stocking_area_bound",
                    clampDetail(placement.getName() + ", " + entry.getName()));
        }
    }

    private boolean hasCommandPermission(final ServerPlayerEntity player) {
        return Permissions.check(
                player,
                PlacementAccessPolicy.COMMAND_PERMISSION,
                PlacementAccessPolicy.COMMAND_PERMISSION_LEVEL
        );
    }

    private boolean canManageStockingAreaEntry(final ServerPlayerEntity player,
                                               final StockingAreaRegistry.Entry entry) {
        final boolean elevated = Permissions.check(
                player,
                PlacementAccessPolicy.MANAGE_PERMISSION,
                PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL
        );
        return PlacementAccessPolicy.canManageStockingAreaEntry(
                SyncmaticaUtil.getProfileId(player.getGameProfile()),
                entry.getOwnerPlayerId(),
                elevated
        );
    }

    private static String playerDimension(final ServerPlayerEntity player) {
        return player.getServerWorld().getRegistryKey().getValue().toString();
    }

    private static MinecraftServer serverOf(final ServerPlayerEntity player) {
        // ServerPlayerEntity#getServer is gone from the newest mappings; the
        // player's world keeps a stable reference to the MinecraftServer.
        return player.getServerWorld().getServer();
    }

    /**
     * Details ride a length-capped string field measured in UTF-8 bytes, and
     * long CJK placement names or project lists would otherwise make the
     * whole reply fail to encode.
     */
    private static String clampDetail(final String detail) {
        final StringBuilder clamped = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < detail.length();) {
            final int codePoint = detail.codePointAt(i);
            final int codePointSize = codePoint <= 0x7F ? 1
                    : codePoint <= 0x7FF ? 2
                    : Character.isSupplementaryCodePoint(codePoint) ? 4
                    : 3;
            if (bytes + codePointSize > ProtocolLimits.MAX_MESSAGE_DETAIL_LENGTH) {
                break;
            }
            clamped.appendCodePoint(codePoint);
            bytes += codePointSize;
            i += Character.charCount(codePoint);
        }
        return clamped.toString();
    }

    @Override
    protected void handleExchange(final Exchange exchange) {
        if (exchange instanceof DownloadExchange) {
            final ServerPlacement p = ((DownloadExchange) exchange).getPlacement();

            if (exchange.isSuccessful()) {
                addPlacement(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final PendingShare pending : downloadingFile.get(p.getHash())) {
                        addPlacement(pending.source, pending.placement);
                    }
                }
            } else {
                cancelShare(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final PendingShare pending : downloadingFile.get(p.getHash())) {
                        cancelShare(pending.source, pending.placement);
                    }
                }
            }

            downloadingFile.remove(p.getHash());
            return;
        }
        if (exchange instanceof VersionHandshakeServer && exchange.isSuccessful()) {
            if (!broadcastTargets.contains(exchange.getPartner())) {
                broadcastTargets.add(exchange.getPartner());
            }
        }
        if (exchange instanceof ModifyExchangeServer && exchange.isSuccessful()) {
            final ServerPlacement placement = ((ModifyExchangeServer) exchange).getPlacement();
            broadcastPlacementUpdate(placement);
        }
    }

    public void reloadFeatureState() {
        purgeStaleTargets();
        for (final ExchangeTarget client : new ArrayList<>(broadcastTargets)) {
            startExchange(new VersionHandshakeServer(client, context));
        }
    }

    public void broadcastPlacementUpdate(final ServerPlacement placement) {
        purgeStaleTargets();
        final Exchange activeModifier = getModifier(placement);
        for (final ExchangeTarget client : broadcastTargets) {
            if (activeModifier != null && activeModifier.getPartner() == client) {
                // This client is mid-modification on the placement and holds a newer
                // pose than the server does. Its MODIFY_FINISH will carry the new
                // state and trigger the broadcast that follows it.
                continue;
            }
            final FeatureSet clientFeatures = client.getFeatureSet();
            if (clientFeatures != null && clientFeatures.hasFeature(Feature.MODIFY)) {
                final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeUuid(placement.getId());
                putModificationData(placement, buf, client);
                if (clientFeatures.hasFeature(Feature.CORE_EX)) {
                    buf.writeUuid(placement.getLastModifiedBy().uuid);
                    buf.writeString(placement.getLastModifiedBy().getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
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
        if (!registerNewPlacement(placement)) {
            cancelShare(t, placement);
        }
    }

    public boolean registerNewPlacement(final ServerPlacement placement) {
        if (context.getSyncmaticManager().getPlacement(placement.getId()) != null
                || context.getSyncmaticManager().getAll().size() >= ProtocolLimits.MAX_SERVER_PLACEMENTS) {
            return false;
        }
        applyAuthoritativeMetadata(placement);
        context.getSyncmaticManager().addPlacement(placement);
        for (final ExchangeTarget target : broadcastTargets) {
            sendMetaData(placement, target);
        }
        return true;
    }

    /**
     * Placement names come from compatible clients; the stored litematic only
     * fills names missing from legacy peers and remains authoritative for
     * schematic versions.
     */
    private void applyAuthoritativeMetadata(final ServerPlacement placement) {
        final File litematic = context.getFileStorage().getLocalLitematic(placement);
        final SchematicPeek peek = SchematicPeeker.peek(litematic);
        if (peek == null) {
            return;
        }
        if (!placement.hasDisplayName() && peek.hasName()) {
            placement.setDisplayName(peek.getName());
        }
        placement.setVersion(peek.getLitematicVersion(), peek.getDataVersion());
    }

    private void cancelShare(final ExchangeTarget source, final ServerPlacement placement) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(placement.getId());
        source.sendPacket(PacketType.CANCEL_SHARE.toIdentifier(source.getProtocolFlavor()), packetByteBuf, context);
    }

    /**
     * Releases the requester's pending download exchange instead of leaving it
     * to expire on the exchange timeout.
     */
    private void cancelDownload(final ExchangeTarget source, final ServerPlacement placement) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(placement.getId());
        source.sendPacket(PacketType.CANCEL_LITEMATIC.toIdentifier(source.getProtocolFlavor()), packetByteBuf, context);
    }

    private void denyModification(final ExchangeTarget source, final UUID placementId) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(placementId);
        source.sendPacket(PacketType.MODIFY_REQUEST_DENY.toIdentifier(source.getProtocolFlavor()), packetByteBuf, context);
    }

    private boolean canShare(final ExchangeTarget source) {
        final ServerPlayerEntity player = playerMap.get(source);
        return player != null && Permissions.check(player, PlacementAccessPolicy.SHARE_PERMISSION, true);
    }

    private boolean canManage(final ExchangeTarget source, final ServerPlacement placement) {
        final ServerPlayerEntity player = playerMap.get(source);
        if (player == null || placement == null || placement.getOwner() == null) {
            return false;
        }
        final UUID playerId = SyncmaticaUtil.getProfileId(player.getGameProfile());
        final boolean elevated = Permissions.check(
                player,
                PlacementAccessPolicy.MANAGE_PERMISSION,
                PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL
        );
        return PlacementAccessPolicy.canManage(playerId, placement.getOwner().uuid, elevated);
    }

    private boolean canManageStockingArea(final ExchangeTarget source,
                                           final ServerPlacement placement,
                                           final MaterialService materialService) {
        final ServerPlayerEntity player = playerMap.get(source);
        if (player == null || placement == null) {
            return false;
        }
        final UUID playerId = SyncmaticaUtil.getProfileId(player.getGameProfile());
        final UUID ownerId = placement.getOwner() == null ? null : placement.getOwner().uuid;
        final boolean elevated = Permissions.check(
                player,
                PlacementAccessPolicy.MANAGE_PERMISSION,
                PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL
        );
        return PlacementAccessPolicy.canManageStockingArea(
                playerId,
                ownerId,
                elevated,
                materialService.isOwnerStockingAreaManagementEnabled()
        );
    }

    @Override
    protected Collection<ExchangeTarget> getTickTargets() {
        return playerMap.keySet();
    }

    private void sendPlayerNotification(final ServerPlayerEntity player, final String message) {
//#if MC >= 12001
//$$         player.sendMessageToClient(Text.literal(message), false);
//#else
        player.sendSystemMessage(new LiteralText(message), Util.NIL_UUID);
//#endif
    }

    private static final class PendingShare {
        private final ExchangeTarget source;
        private final ServerPlacement placement;

        private PendingShare(final ExchangeTarget source, final ServerPlacement placement) {
            this.source = source;
            this.placement = placement;
        }
    }
}
