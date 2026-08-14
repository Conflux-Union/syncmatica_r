package cn.net.rms.syncmatica_r.util;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.PacketType;
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
 * Turns the player's current Litematica area selection into a stocking area on
 * the server, so an area can be picked with the schematic tool item instead of
 * typing six coordinates into {@code /syncmatica_r ... setStockingarea}.
 *
 * <p>Litematica keeps ownership of the selection and its in-world rendering;
 * this class only reads the resulting corners.
 */
public final class StockingAreaSelectionHelper {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Placeholder id for the default area, which is not tied to a placement. */
    private static final UUID NIL_PLACEMENT = new UUID(0L, 0L);

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

    public static Result sendForPlacement(final ServerPlacement placement) {
        if (placement == null) {
            return Result.FAILED;
        }
        return send(false, placement.getId());
    }

    public static Result sendAsDefault() {
        return send(true, NIL_PLACEMENT);
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

    private static Result send(final boolean isDefault, final UUID placementId) {
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
        if (serverFeatures == null || !serverFeatures.hasFeature(Feature.STOCKING_AREA_SETUP)) {
            return Result.UNSUPPORTED;
        }

        final BlockPos first = box.getPos1();
        final BlockPos second = box.getPos2();
        try {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBoolean(isDefault);
            buf.writeUuid(placementId);
            buf.writeBlockPos(first);
            buf.writeBlockPos(second);
            server.sendPacket(PacketType.SET_STOCKING_AREA.toIdentifier(server.getProtocolFlavor()), buf, context);
            return Result.SENT;
        } catch (final RuntimeException exception) {
            LOGGER.error("Failed to send stocking area selection", exception);
            return Result.FAILED;
        }
    }
}
