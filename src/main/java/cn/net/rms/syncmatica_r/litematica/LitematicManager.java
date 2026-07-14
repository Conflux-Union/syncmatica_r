package cn.net.rms.syncmatica_r.litematica;

import cn.net.rms.syncmatica_r.*;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.SubRegionData;
import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import cn.net.rms.syncmatica_r.litematica.MovingFinisher;
import cn.net.rms.syncmatica_r.litematica_mixin.MixinSchematicPlacementManager;
import cn.net.rms.syncmatica_r.litematica_mixin.MixinSubregionPlacement;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import cn.net.rms.syncmatica_r.schematic.SchematicPeeker;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.gui.Message;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12005
//$$ import com.mojang.authlib.GameProfile;
//$$ import net.minecraft.client.session.Session;
//#endif
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.File;
//#if MC >= 12106
//$$ import java.nio.file.Path;
//#endif
import java.util.*;

public class LitematicManager {
    private static LitematicManager instance = null;

    private final Map<ServerPlacement, SchematicPlacement> rendering;
    private Collection<SchematicPlacement> preLoadList = new ArrayList<>();
    private Context context;

    private LitematicManager() {
        rendering = new HashMap<>();
    }

    public static LitematicManager getInstance() {
        if (instance == null) {
            instance = new LitematicManager();
        }
        return instance;
    }

    public static void clear() {
        instance = null;
    }

    public Context getActiveContext() {
        return context;
    }

    public void setActiveContext(final Context con) {
        if (con.isServer()) {
            throw new Context.ContextMismatchException("Applied server context where client context was expected");
        }
        context = con;
        ScreenHelper.ifPresent(s -> s.setActiveContext(con));
    }

    public void renderSyncmatic(final ServerPlacement placement) {
        final String dimension = getPlayerDimension();
        if (!dimension.equals(placement.getDimension())) {
            ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.ERROR, "syncmatica_r.error.player_dimension_mismatch"));
            context.getSyncmaticManager().updateServerPlacement(placement);
            return;
        }
        if (rendering.containsKey(placement)) {
            return;
        }
        final File file = context.getFileStorage().getLocalLitematic(placement);

        //#if MC >= 12106
        //$$ final LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file.toPath());
        //#else
        final LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file);
        //#endif

        if (schematic == null) {
            ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.ERROR, "syncmatica_r.error.failed_to_load", file.getAbsolutePath()));
            return;
        }

        final BlockPos origin = placement.getPosition();

        final SchematicPlacement litematicaPlacement = SchematicPlacement.createFor(
                schematic,
                origin,
                placement.getName(),
                true,
                true
        );
        rendering.put(placement, litematicaPlacement);
        ((IIDContainer) litematicaPlacement).setServerId(placement.getId());
        if (litematicaPlacement.isLocked()) {
            litematicaPlacement.toggleLocked();
        }
        litematicaPlacement.setRotation(placement.getRotation(), null);
        litematicaPlacement.setMirror(placement.getMirror(), null);
        transferSubregionDataToClientPlacement(placement, litematicaPlacement);
        litematicaPlacement.toggleLocked();

        DataManager.getSchematicPlacementManager().addSchematicPlacement(litematicaPlacement, true);
        context.getSyncmaticManager().updateServerPlacement(placement);
    }

    public ServerPlacement syncmaticFromSchematic(final SchematicPlacement schem) {
        if (rendering.containsValue(schem)) {

            for (final ServerPlacement checkPlacement : rendering.keySet()) {
                if (rendering.get(checkPlacement) == schem) {
                    return checkPlacement;
                }
            }

            return null;
        }
        try {
            //#if MC >= 12106
            //$$ final Path placementPath = schem.getSchematicFile();
            //$$ final File placementFile = placementPath != null ? placementPath.toFile() : null;
            //#else
            final File placementFile = schem.getSchematicFile();
            //#endif
            final FileType fileType = FileType.fromFile(placementFile);
            if (fileType == FileType.VANILLA_STRUCTURE || fileType == FileType.SCHEMATICA_SCHEMATIC) {
                ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.ERROR, "syncmatica_r.error.share_incompatible_schematic"));
                return null;
            } else if (fileType != FileType.LITEMATICA_SCHEMATIC) {
                ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.ERROR, "syncmatica_r.error.invalid_file"));
                return null;
            }

//#if MC >= 12005
//$$             final Session session = MinecraftClient.getInstance().getSession();
//$$             final UUID rawUuid = session == null ? null : session.getUuidOrNull();
//$$             final String username = session == null ? "unknown" : session.getUsername();
//$$             final UUID resolvedUuid = rawUuid != null
//$$                     ? rawUuid
//$$                     : UUID.nameUUIDFromBytes(("syncmatica:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$             final PlayerIdentifier owner = context.getPlayerIdentifierProvider().createOrGet(
//$$                     new GameProfile(resolvedUuid, username)
//$$             );
//#else
            final PlayerIdentifier owner = context.getPlayerIdentifierProvider().createOrGet(
                    MinecraftClient.getInstance().getSession().getProfile()
            );
//#endif

            final ServerPlacement placement = new ServerPlacement(UUID.randomUUID(), placementFile, owner);
            final SchematicPeek peek = SchematicPeeker.peek(placementFile);
            if (peek != null) {
                if (peek.hasName()) {
                    placement.setDisplayName(peek.getName());
                }
                placement.setVersion(peek.getLitematicVersion(), peek.getDataVersion());
            }

            final String dimension = getPlayerDimension();
            placement.move(dimension, schem.getOrigin(), schem.getRotation(), schem.getMirror());
            transferSubregionDataToServerPlacement(schem, placement);
            return placement;
        } catch (final Exception e) {
            ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.ERROR, "syncmatica_r.error.create_from_schematic", e.getMessage()));
        }
        return null;
    }

    private void transferSubregionDataToServerPlacement(final SchematicPlacement schem, final ServerPlacement placement) {
        final Collection<SubRegionPlacement> subLitematica = schem.getAllSubRegionsPlacements();
        final Map<String, BlockPos> defaultPositionMap = schem.getSchematic().getAreaPositions();
        final SubRegionData subRegionData = placement.getSubRegionData();
        subRegionData.reset();
        for (final SubRegionPlacement subRegionPlacement : subLitematica) {
            final BlockPos defaultPos = defaultPositionMap.get(subRegionPlacement.getName());
            if (isSubregionModified(subRegionPlacement, defaultPos)) {
                subRegionData.modify(
                        subRegionPlacement.getName(),
                        subRegionPlacement.getPos(),
                        subRegionPlacement.getRotation(),
                        subRegionPlacement.getMirror()
                );
            }
        }
    }

    private void transferSubregionDataToClientPlacement(final ServerPlacement placement, final SchematicPlacement schem) {
        final Collection<SubRegionPlacement> subLitematica = schem.getAllSubRegionsPlacements();
        final Map<String, BlockPos> defaultPositionMap = schem.getSchematic().getAreaPositions();
        final Map<String, SubRegionPlacementModification> modificationData = placement.getSubRegionData().getModificationData();
        for (final SubRegionPlacement subRegionPlacement : subLitematica) {
            final SubRegionPlacementModification modification = modificationData != null ?
                    modificationData.get(subRegionPlacement.getName()) :
                    null;
            final BlockPos defaultPos = defaultPositionMap.get(subRegionPlacement.getName());
            final SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            final MixinSchematicPlacementManager mixinManager = (MixinSchematicPlacementManager) manager;
            final MixinSubregionPlacement mutable = (MixinSubregionPlacement) subRegionPlacement;
            if (modification != null) {
                mixinManager.preSubregionChange(schem);
                mutable.setBlockPosition(modification.position);
                mutable.setBlockRotation(modification.rotation);
                mutable.setBlockMirror(modification.mirror);
                ((MovingFinisher) schem).onFinishedMoving(subRegionPlacement.getName(), manager);
            } else {
                if (isSubregionModified(subRegionPlacement, defaultPos)) {
                    mixinManager.preSubregionChange(schem);
                    resetSubRegion(subRegionPlacement, defaultPos);
                    ((MovingFinisher) schem).onFinishedMoving(subRegionPlacement.getName(), manager);
                }
            }
        }
    }

    public SchematicPlacement schematicFromSyncmatic(final ServerPlacement p) {
        return rendering.get(p);
    }

    public void renderSyncmatic(final ServerPlacement placement, final SchematicPlacement litematicaPlacement, final boolean addToRendering) {
        if (rendering.containsKey(placement)) {
            return;
        }
        final IIDContainer modPlacement = (IIDContainer) litematicaPlacement;
        if (modPlacement.getServerId() != null && !modPlacement.getServerId().equals(placement.getId())) {
            return;
        }
        rendering.put(placement, litematicaPlacement);
        modPlacement.setServerId(placement.getId());

        if (litematicaPlacement.isLocked()) {
            litematicaPlacement.toggleLocked();
        }
        litematicaPlacement.setOrigin(placement.getPosition(), null);
        litematicaPlacement.setRotation(placement.getRotation(), null);
        litematicaPlacement.setMirror(placement.getMirror(), null);
        transferSubregionDataToClientPlacement(placement, litematicaPlacement);
        litematicaPlacement.toggleLocked();
        context.getSyncmaticManager().updateServerPlacement(placement);
        if (addToRendering) {
            DataManager.getSchematicPlacementManager().addSchematicPlacement(litematicaPlacement, false);
        }
    }

    public void unrenderSyncmatic(final ServerPlacement placement) {
        if (!isRendered(placement)) {
            return;
        }
        DataManager.getSchematicPlacementManager().removeSchematicPlacement(rendering.get(placement));
        rendering.remove(placement);
        context.getSyncmaticManager().updateServerPlacement(placement);
    }

    public void updateRendered(final ServerPlacement placement) {
        if (!isRendered(placement)) {
            return;
        }
        final SchematicPlacement litematicaPlacement = rendering.get(placement);
        final boolean wasLocked = litematicaPlacement.isLocked();
        if (wasLocked) {
            litematicaPlacement.toggleLocked();
        }
        litematicaPlacement.setOrigin(placement.getPosition(), null);
        litematicaPlacement.setRotation(placement.getRotation(), null);
        litematicaPlacement.setMirror(placement.getMirror(), null);
        transferSubregionDataToClientPlacement(placement, litematicaPlacement);
        if (wasLocked) {
            litematicaPlacement.toggleLocked();
        }
    }

    public void updateServerPlacement(final SchematicPlacement placement, final ServerPlacement serverPlacement) {
        serverPlacement.move(
                serverPlacement.getDimension(),
                placement.getOrigin(),
                placement.getRotation(),
                placement.getMirror()
        );
        transferSubregionDataToServerPlacement(placement, serverPlacement);
    }

    public boolean isRendered(final ServerPlacement placement) {
        return rendering.containsKey(placement);
    }

    public boolean isSyncmatic(final SchematicPlacement schem) {
        return rendering.containsValue(schem);
    }

    public boolean isSubregionModified(final SubRegionPlacement subRegionPlacement, final BlockPos defaultPos) {
        return subRegionPlacement.getMirror() != BlockMirror.NONE || subRegionPlacement.getRotation() != BlockRotation.NONE ||
                !subRegionPlacement.getPos().equals(defaultPos);
    }

    public void resetSubRegion(final SubRegionPlacement subRegionPlacement, final BlockPos defaultPos) {
        final MixinSubregionPlacement mutable = (MixinSubregionPlacement) subRegionPlacement;
        mutable.setBlockMirror(BlockMirror.NONE);
        mutable.setBlockRotation(BlockRotation.NONE);
        mutable.setBlockPosition(defaultPos);
    }

    public void preLoad(final SchematicPlacement schem) {
        if (context != null && context.isStarted()) {
            final UUID id = ((IIDContainer) schem).getServerId();
            final ServerPlacement p = context.getSyncmaticManager().getPlacement(id);
            if (isRendered(p)) {
                rendering.put(p, schem);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(schem, false);
            }
        } else if (preLoadList != null) {
            preLoadList.add(schem);
        }
    }

    public void commitLoad() {
        final SyncmaticManager man = context.getSyncmaticManager();
        for (final SchematicPlacement schem : preLoadList) {
            final UUID id = ((IIDContainer) schem).getServerId();
            final ServerPlacement p = man.getPlacement(id);
            if (p != null) {
                //#if MC >= 12106
                //$$ final Path schematicPath = schem.getSchematicFile();
                //$$ final File schematicFile = schematicPath != null ? schematicPath.toFile() : null;
                //#else
                final File schematicFile = schem.getSchematicFile();
                //#endif
                if (context.getFileStorage().getLocalLitematic(p) != schematicFile) {
                    ((RedirectFileStorage) context.getFileStorage()).addRedirect(schematicFile);
                }
                renderSyncmatic(p, schem, true);
            }
        }
        preLoadList = null;
    }

    public void unrenderSchematic(final LitematicaSchematic l) {
        rendering.entrySet().removeIf(e -> {
            if (e.getValue().getSchematic() == l) {
                context.getSyncmaticManager().updateServerPlacement(e.getKey());
                return true;
            }
            return false;
        });
    }

    public void unrenderSchematicPlacement(final SchematicPlacement placement) {
        final UUID id = ((IIDContainer) placement).getServerId();
        final ServerPlacement p = context.getSyncmaticManager().getPlacement(id);
        if (p != null) {
            unrenderSyncmatic(p);
        }
    }

    public ServerPosition getPlayerPosition() {
        if (MinecraftClient.getInstance().getCameraEntity() != null) {
            final BlockPos blockPos = MinecraftClient.getInstance().getCameraEntity().getBlockPos();
            final String dimensionId = getPlayerDimension();
            return new ServerPosition(blockPos, dimensionId);
        }
        return new ServerPosition(new BlockPos(0, 0, 0), ServerPosition.OVERWORLD_DIMENSION_ID);
    }

    public String getPlayerDimension() {
        if (MinecraftClient.getInstance().getCameraEntity() != null) {
            //#if MC >= 12110
            //$$ return MinecraftClient.getInstance().getCameraEntity().getEntityWorld().getRegistryKey().getValue().toString();
            //#elseif MC >= 12106
            //$$ return MinecraftClient.getInstance().getCameraEntity().getWorld().getRegistryKey().getValue().toString();
            //#else
            return MinecraftClient.getInstance().getCameraEntity().getEntityWorld().getRegistryKey().getValue().toString();
            //#endif
        } else {
            return ServerPosition.OVERWORLD_DIMENSION_ID;
        }
    }
}
