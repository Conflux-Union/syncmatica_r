package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.SubRegionData;
import cn.net.rms.syncmatica_r.material.*;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import cn.net.rms.syncmatica_r.schematic.SchematicPeeker;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileInputStream;
import java.util.UUID;

public class ServerPlacement {

    private final UUID id;

    private final String fileName;
    private String displayName;
    private int litematicVersion = SchematicPeek.UNKNOWN_VERSION;
    private int dataVersion = SchematicPeek.UNKNOWN_VERSION;
    private boolean metadataDirty;
    private final UUID hashValue;
    private final SyncmaticaMaterialList materialList = new SyncmaticaMaterialList();
    private final MaterialProgressState materialProgress = new MaterialProgressState();
    private PlayerIdentifier owner;
    private PlayerIdentifier lastModifiedBy;
    private long createdAtMillis;
    private long lastModifiedAtMillis;
    private ServerPosition origin;
    private BlockRotation rotation;
    private BlockMirror mirror;
    private SubRegionData subRegionData = new SubRegionData();
    private StockingAreaDefinition stockingArea;
    private MaterialAvailability materialAvailability = MaterialAvailability.AVAILABLE;

    public ServerPlacement(final UUID id, final String fileName, final UUID hashValue, final PlayerIdentifier owner) {
        this.id = id;
        this.fileName = fileName;
        this.hashValue = hashValue;
        this.owner = owner;
        lastModifiedBy = owner;
    }

    public ServerPlacement(final UUID id, final File file, final PlayerIdentifier owner) {
        this(id, removeExtension(file), generateHash(file), owner);
    }

    private static String removeExtension(final File file) {

        final String fileName = file.getName();
        final int pos = fileName.lastIndexOf(".");
        return fileName.substring(0, pos);
    }

    private static UUID generateHash(final File file) {
        UUID hash = null;
        try {
            hash = SyncmaticaUtil.createChecksum(new FileInputStream(file));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return hash;
    }

    public static ServerPlacement fromJson(final JsonObject obj, final Context context) {
        if (obj.has("id")
                && obj.has("file_name")
                && obj.has("hash")
                && obj.has("origin")
                && obj.has("rotation")
                && obj.has("mirror")) {
            final UUID id = UUID.fromString(obj.get("id").getAsString());
            final String name = obj.get("file_name").getAsString();
            final UUID hashValue = UUID.fromString(obj.get("hash").getAsString());

            PlayerIdentifier owner = PlayerIdentifier.MISSING_PLAYER;
            if (obj.has("owner")) {
                owner = context.getPlayerIdentifierProvider().fromJson(obj.get("owner").getAsJsonObject());
            }

            final ServerPlacement newPlacement = new ServerPlacement(id, name, hashValue, owner);

            if (obj.has("display_name")) {
                newPlacement.displayName = obj.get("display_name").getAsString();
            }
            if (obj.has("litematicVersion")) {
                newPlacement.litematicVersion = obj.get("litematicVersion").getAsInt();
            }
            if (obj.has("dataVersion")) {
                newPlacement.dataVersion = obj.get("dataVersion").getAsInt();
            }
            enrichFromLocalFile(newPlacement, context);

            final ServerPosition pos = ServerPosition.fromJson(obj.get("origin").getAsJsonObject());
            if (pos == null) {
                return null;
            }
            newPlacement.origin = pos;
            newPlacement.rotation = BlockRotation.valueOf(obj.get("rotation").getAsString());
            newPlacement.mirror = BlockMirror.valueOf(obj.get("mirror").getAsString());

            if (obj.has("lastModifiedBy")) {
                newPlacement.lastModifiedBy = context.getPlayerIdentifierProvider()
                        .fromJson(obj.get("lastModifiedBy").getAsJsonObject());
            } else {
                newPlacement.lastModifiedBy = owner;
            }

            if (obj.has("createdAt")) {
                newPlacement.createdAtMillis = obj.get("createdAt").getAsLong();
            }
            if (obj.has("lastModifiedAt")) {
                newPlacement.lastModifiedAtMillis = obj.get("lastModifiedAt").getAsLong();
            }

            if (obj.has("subregionData")) {
                newPlacement.subRegionData = SubRegionData.fromJson(obj.get("subregionData"));
            }

            if (obj.has("materials")) {
                MaterialProgressSerializer.fromJson(
                        obj.get("materials").getAsJsonObject(),
                        newPlacement.materialProgress,
                        context.getPlayerIdentifierProvider()
                );
            }

            if (obj.has("stockingArea")) {
                newPlacement.stockingArea = StockingAreaDefinition.fromJson(obj.getAsJsonObject("stockingArea"));
            }

            return newPlacement;
        }

        return null;
    }

    /**
     * Server placements loaded from disk may predate the display-name and
     * schematic-version features; fill the missing metadata from the stored
     * litematic file so old data is upgraded without a re-share.
     */
    private static void enrichFromLocalFile(final ServerPlacement placement, final Context context) {
        if (context == null || !context.isServer()) {
            return;
        }
        final boolean missingName = placement.displayName == null || placement.displayName.isEmpty();
        final boolean missingVersion = placement.litematicVersion <= SchematicPeek.UNKNOWN_VERSION
                || placement.dataVersion <= SchematicPeek.UNKNOWN_VERSION;
        if (!missingName && !missingVersion) {
            return;
        }
        final File litematic = new File(context.getLitematicFolder(), placement.hashValue.toString() + ".litematic");
        final SchematicPeek peek = SchematicPeeker.peek(litematic);
        if (peek == null) {
            return;
        }
        if (missingName && peek.hasName()) {
            placement.displayName = peek.getName();
            placement.metadataDirty = true;
        }
        if (missingVersion) {
            placement.litematicVersion = peek.getLitematicVersion();
            placement.dataVersion = peek.getDataVersion();
            placement.metadataDirty = true;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return displayName == null || displayName.isEmpty() ? fileName : displayName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public int getLitematicVersion() {
        return litematicVersion;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void setVersion(final int litematicVersion, final int dataVersion) {
        this.litematicVersion = litematicVersion;
        this.dataVersion = dataVersion;
    }

    /**
     * Returns whether metadata was corrected during deserialization and clears
     * the marker; used by SyncmaticManager to persist the corrected state.
     */
    public boolean consumeMetadataDirty() {
        final boolean dirty = metadataDirty;
        metadataDirty = false;
        return dirty;
    }

    public UUID getHash() {
        return hashValue;
    }

    public String getDimension() {
        return origin.getDimensionId();
    }

    public BlockPos getPosition() {
        return origin.getBlockPosition();
    }

    public ServerPosition getOrigin() {
        return origin;
    }

    public BlockRotation getRotation() {
        return rotation;
    }

    public BlockMirror getMirror() {
        return mirror;
    }

    public ServerPlacement move(final String dimensionId, final BlockPos origin, final BlockRotation rotation, final BlockMirror mirror) {
        move(new ServerPosition(origin, dimensionId), rotation, mirror);
        return this;
    }

    public ServerPlacement move(final ServerPosition origin, final BlockRotation rotation, final BlockMirror mirror) {
        this.origin = origin;
        this.rotation = rotation;
        this.mirror = mirror;
        return this;
    }

    public PlayerIdentifier getOwner() {
        return owner;
    }

    public void setOwner(final PlayerIdentifier playerIdentifier) {
        owner = playerIdentifier;
    }

    public PlayerIdentifier getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(final PlayerIdentifier lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(final long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public long getLastModifiedAtMillis() {
        return lastModifiedAtMillis;
    }

    public void setLastModifiedAtMillis(final long lastModifiedAtMillis) {
        this.lastModifiedAtMillis = lastModifiedAtMillis;
    }

    public void touchCreated(final long timestamp) {
        if (createdAtMillis == 0L) {
            createdAtMillis = timestamp;
        }
        if (lastModifiedAtMillis == 0L) {
            lastModifiedAtMillis = timestamp;
        }
    }

    public void touchModified(final long timestamp) {
        lastModifiedAtMillis = timestamp;
        if (createdAtMillis == 0L) {
            createdAtMillis = timestamp;
        }
    }

    public SubRegionData getSubRegionData() {
        return subRegionData;
    }

    public MaterialProgressState getMaterialProgress() {
        return materialProgress;
    }

    public SyncmaticaMaterialList getMaterialList() {
        materialList.updateFrom(materialProgress);
        if (origin != null) {
            materialList.setDeliveryPoint(origin);
        }
        return materialList;
    }

    public MaterialAvailability getMaterialAvailability() {
        return materialAvailability;
    }

    /**
     * Derived from the stored litematic on every attach, so it is deliberately
     * not persisted with the placement.
     *
     * @return whether the value changed and therefore needs to be broadcast
     */
    public boolean setMaterialAvailability(final MaterialAvailability availability) {
        final MaterialAvailability resolved = availability == null ? MaterialAvailability.AVAILABLE : availability;
        if (materialAvailability == resolved) {
            return false;
        }
        materialAvailability = resolved;
        return true;
    }

    public StockingAreaDefinition getStockingArea() {
        return stockingArea;
    }

    public void setStockingArea(final StockingAreaDefinition stockingArea) {
        this.stockingArea = stockingArea;
    }

    public void applyMaterialProgressSnapshot(final MaterialProgressState snapshot) {
        materialProgress.clear();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        snapshot.getEntries().forEach(entry -> {
            final MaterialProgressEntry target = materialProgress.getOrCreate(entry.getKey(), entry.getRequiredAmount());
            target.setStockingSupplied(entry.getStockingSupplied());
            target.clearClaimants();
            for (final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier p : entry.getClaimants()) {
                target.addClaimer(p);
            }
        });
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();
        obj.add("id", new JsonPrimitive(id.toString()));

        obj.add("file_name", new JsonPrimitive(fileName));
        obj.add("display_name", new JsonPrimitive(getName()));
        obj.add("hash", new JsonPrimitive(hashValue.toString()));

        if (litematicVersion > SchematicPeek.UNKNOWN_VERSION) {
            obj.add("litematicVersion", new JsonPrimitive(litematicVersion));
        }
        if (dataVersion > SchematicPeek.UNKNOWN_VERSION) {
            obj.add("dataVersion", new JsonPrimitive(dataVersion));
        }

        obj.add("origin", origin.toJson());
        obj.add("rotation", new JsonPrimitive(rotation.name()));
        obj.add("mirror", new JsonPrimitive(mirror.name()));

        obj.add("owner", owner.toJson());
        if (!owner.equals(lastModifiedBy)) {
            obj.add("lastModifiedBy", lastModifiedBy.toJson());
        }

        if (createdAtMillis > 0L) {
            obj.add("createdAt", new JsonPrimitive(createdAtMillis));
        }
        if (lastModifiedAtMillis > 0L) {
            obj.add("lastModifiedAt", new JsonPrimitive(lastModifiedAtMillis));
        }

        if (subRegionData.isModified()) {
            obj.add("subregionData", subRegionData.toJson());
        }

        if (!materialProgress.isEmpty()) {
            obj.add("materials", MaterialProgressSerializer.toJson(materialProgress));
        }

        if (stockingArea != null) {
            obj.add("stockingArea", stockingArea.toJson());
        }

        return obj;
    }
}
