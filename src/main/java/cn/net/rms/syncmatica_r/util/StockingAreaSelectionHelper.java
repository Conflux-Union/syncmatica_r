package cn.net.rms.syncmatica_r.util;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.StockingAreaManageOpcodes;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.selection.SelectionManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Turns the player's current Litematica area selection into a named stocking
 * area managed through {@link PacketType#STOCKING_AREA_MANAGE}, so an area can
 * be picked with the schematic tool item instead of typing coordinates into
 * {@code /syncmatica_r ... stockingArea}.
 *
 * <p>Litematica keeps ownership of the selection and its in-world rendering;
 * this class only reads the resulting corners.
 */
public final class StockingAreaSelectionHelper {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Outcome of a send attempt, so callers can pick the right user-facing message. */
    public enum Result {
        SENT,
        NO_SELECTION,
        NO_SERVER,
        UNSUPPORTED,
        FAILED
    }

    private StockingAreaSelectionHelper() {
        // Utility class
    }

    /**
     * @return the translation key explaining why a send attempt did not reach the
     *         server. {@link Result#SENT} has no key because the server answers
     *         with its own authoritative message.
     */
    public static String getFailureMessageKey(final Result result) {
        switch (result) {
            case NO_SELECTION:
                return "syncmatica_r.error.stocking_area.no_selection";
            case NO_SERVER:
                return "syncmatica_r.error.stocking_area.no_server";
            case UNSUPPORTED:
                return "syncmatica_r.error.stocking_area.unsupported";
            default:
                return "syncmatica_r.error.stocking_area.failed";
        }
    }

    /**
     * Uploads the selection as the placement's stocking area: UPDATE when the
     * placement already references an area, otherwise CREATE with a name derived
     * from the placement and an immediate bind so the two never drift apart.
     */
    public static Result sendSelectionAsBoundArea(final ServerPlacement placement) {
        if (placement == null) {
            return Result.FAILED;
        }
        final UUID existingRef = placement.getStockingAreaRef();
        return send((buf, first, second) -> {
            if (existingRef != null) {
                buf.writeByte(StockingAreaManageOpcodes.OP_UPDATE);
                buf.writeUuid(existingRef);
            } else {
                buf.writeByte(StockingAreaManageOpcodes.OP_CREATE);
                buf.writeString(placement.getName(), ProtocolLimits.MAX_STOCKING_AREA_NAME_LENGTH);
                buf.writeBoolean(true);
                buf.writeUuid(placement.getId());
            }
            buf.writeBlockPos(first);
            buf.writeBlockPos(second);
        });
    }

    /**
     * Uploads the selection as new corners for the reserved default area, which
     * every placement without its own binding falls back to.
     */
    public static Result sendSelectionAsDefaultUpdate() {
        return send((buf, first, second) -> {
            buf.writeByte(StockingAreaManageOpcodes.OP_UPDATE_DEFAULT);
            buf.writeBlockPos(first);
            buf.writeBlockPos(second);
        });
    }

    /**
     * @return the box the player currently has selected, or null when Litematica
     *         has no usable selection. A selection holding exactly one sub-region
     *         box counts as selected even when no box is explicitly highlighted,
     *         which is the common case right after framing an area.
     */
    public static Box getSelectedBox() {
        final SelectionManager selectionManager = DataManager.getSelectionManager();
        if (selectionManager == null) {
            return null;
        }
        final AreaSelection selection = selectionManager.getCurrentSelection();
        if (selection == null) {
            return null;
        }
        Box box = selection.getSelectedSubRegionBox();
        if (box == null) {
            final List<Box> boxes = selection.getAllSubRegionBoxes();
            if (boxes != null && boxes.size() == 1) {
                box = boxes.get(0);
            }
        }
        if (box == null || box.getPos1() == null || box.getPos2() == null) {
            return null;
        }
        return box;
    }

    /** Writes an opcode payload between the selection's two corners. */
    private interface PayloadWriter {
        void write(PacketByteBuf buf, BlockPos first, BlockPos second);
    }

    private static Result send(final PayloadWriter payload) {
        final Box box = getSelectedBox();
        if (box == null) {
            return Result.NO_SELECTION;
        }
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || !(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return Result.NO_SERVER;
        }
        final ExchangeTarget server = ((ClientCommunicationManager) context.getCommunicationManager()).getServer();
        if (server == null) {
            return Result.NO_SERVER;
        }
        final FeatureSet serverFeatures = server.getFeatureSet();
        if (serverFeatures == null || !serverFeatures.hasFeature(Feature.NAMED_STOCKING_AREAS)) {
            return Result.UNSUPPORTED;
        }
        try {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            payload.write(buf, box.getPos1(), box.getPos2());
            server.sendPacket(PacketType.STOCKING_AREA_MANAGE.toIdentifier(server.getProtocolFlavor()),
                    buf, context);
            return Result.SENT;
        } catch (final RuntimeException exception) {
            LOGGER.error("Failed to send stocking area selection", exception);
            return Result.FAILED;
        }
    }
}
