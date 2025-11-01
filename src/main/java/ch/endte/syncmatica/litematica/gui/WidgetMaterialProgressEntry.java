package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Feature;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.communication.ClientCommunicationManager;
import ch.endte.syncmatica.communication.PacketType;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.material.MaterialKey;
import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;

import java.util.Arrays;

/**
 * Renders a single material progress row and exposes claim/contribution actions.
 */
public class WidgetMaterialProgressEntry extends WidgetListEntryBase<SyncmaticaMaterialEntry> {
    private static final int BUTTON_WIDTH = 48;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_SPACING = 4;

    private final ServerPlacement placement;
    private final ButtonGeneric claimButton;
    private final ButtonGeneric contributionButton;

    public WidgetMaterialProgressEntry(final int x, final int y, final int width, final int height,
                                       final SyncmaticaMaterialEntry entry, final int listIndex,
                                       final ServerPlacement placement) {
        super(x, y, width, height, entry, listIndex);
        this.placement = placement;
        claimButton = new ButtonGeneric(x + width - (BUTTON_WIDTH * 2) - BUTTON_SPACING, y + 2, BUTTON_WIDTH, BUTTON_HEIGHT, getClaimLabel());
        contributionButton = new ButtonGeneric(x + width - BUTTON_WIDTH - BUTTON_SPACING, y + 2, BUTTON_WIDTH, BUTTON_HEIGHT,
                StringUtils.translate("syncmatica.gui.button.material.contribute"));
        contributionButton.setHoverStrings(Arrays.asList(
                StringUtils.translate("syncmatica.gui.tooltip.material.contribute.primary"),
                StringUtils.translate("syncmatica.gui.tooltip.material.contribute.secondary")
        ));
        addButton(claimButton, new ClaimButtonListener());
        addButton(contributionButton, new ContributionButtonListener());
    }

    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected, final MatrixStack matrixStack) {
        positionButtons();
        RenderUtils.drawRect(x, y, width, height, listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF);
        updateButtonState();
        final SyncmaticaMaterialEntry material = getEntry();
        final int textColor = 0xFFFFFFFF;
        final int secondaryColor = 0xC0FFFFFF;

        int posX = x + 6;
        drawString(posX, y + 6, textColor, material.getKey() != null ? material.getKey().toString() : "unknown", matrixStack);
        posX += 140;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getAmountRequired()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getPlayerSupplied()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getStockingSupplied()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, material.isFinished() ? 0x80FF80 : 0xFFFF80, String.valueOf(material.getAmountMissing()), matrixStack);
        final int claimColumnX = x + width - (BUTTON_WIDTH * 2) - BUTTON_SPACING - 8;
        final String claimed = material.isClaimed() ? material.getClaimedBy() : "-";
        drawString(claimColumnX, y + 6, secondaryColor, claimed, matrixStack);
    }

    private void positionButtons() {
        final int buttonY = y + 2;
        claimButton.setPosition(x + width - (BUTTON_WIDTH * 2) - BUTTON_SPACING, buttonY);
        contributionButton.setPosition(x + width - BUTTON_WIDTH - BUTTON_SPACING, buttonY);
    }

    private void updateButtonState() {
        final SyncmaticaMaterialEntry entry = getEntry();
        final boolean available = materialUpdatesSupported() && entry.getKey() != null;
        claimButton.setDisplayString(getClaimLabel());
        claimButton.setEnabled(available && (!entry.isClaimed() || isClaimedBySelf()));
        contributionButton.setEnabled(available);
    }

    private String getClaimLabel() {
        final SyncmaticaMaterialEntry entry = getEntry();
        if (!entry.isClaimed()) {
            return StringUtils.translate("syncmatica.gui.button.material.claim");
        }
        if (isClaimedBySelf()) {
            return StringUtils.translate("syncmatica.gui.button.material.unclaim");
        }
        return StringUtils.translate("syncmatica.gui.button.material.claimed");
    }

    private boolean isClaimedBySelf() {
        final SyncmaticaMaterialEntry entry = getEntry();
        if (!entry.isClaimed()) {
            return false;
        }
        final String localPlayer = MinecraftClient.getInstance().getSession().getProfile().getName();
        return localPlayer.equals(entry.getClaimedBy());
    }

    private boolean materialUpdatesSupported() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || context.isServer()) {
            return false;
        }
        if (!(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return false;
        }
        final ClientCommunicationManager manager = (ClientCommunicationManager) context.getCommunicationManager();
        return manager.getServer().getFeatureSet().hasFeature(Feature.MATERIAL_PROGRESS);
    }

    private void sendClaimRequest(final boolean claim) {
        final SyncmaticaMaterialEntry entry = getEntry();
        if (!materialUpdatesSupported() || entry.getKey() == null) {
            return;
        }
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ClientCommunicationManager manager = (ClientCommunicationManager) context.getCommunicationManager();
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(placement.getId());
        buf.writeString(entry.getKey().getItemId().toString());
        buf.writeString(entry.getKey().getVariant());
        buf.writeBoolean(claim);
        manager.getServer().sendPacket(PacketType.MATERIAL_CLAIM.identifier, buf, context);
    }

    private void sendContributionDelta(final int delta) {
        if (delta == 0) {
            return;
        }
        final SyncmaticaMaterialEntry entry = getEntry();
        if (!materialUpdatesSupported() || entry.getKey() == null) {
            return;
        }
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ClientCommunicationManager manager = (ClientCommunicationManager) context.getCommunicationManager();
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(placement.getId());
        final MaterialKey key = entry.getKey();
        buf.writeString(key.getItemId().toString());
        buf.writeString(key.getVariant());
        buf.writeInt(delta);
        manager.getServer().sendPacket(PacketType.MATERIAL_CONTRIBUTE.identifier, buf, context);
    }

    private int contributionStep() {
        if (GuiBase.isCtrlDown()) {
            return 64;
        }
        if (GuiBase.isShiftDown()) {
            return 16;
        }
        return 1;
    }

    private final class ClaimButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(final ButtonBase button, final int mouseButton) {
            if (!materialUpdatesSupported()) {
                return;
            }
            final SyncmaticaMaterialEntry entry = getEntry();
            if (entry.getKey() == null) {
                return;
            }
            if (!entry.isClaimed()) {
                sendClaimRequest(true);
                return;
            }
            if (isClaimedBySelf()) {
                sendClaimRequest(false);
            }
        }
    }

    private final class ContributionButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(final ButtonBase button, final int mouseButton) {
            if (!materialUpdatesSupported()) {
                return;
            }
            final SyncmaticaMaterialEntry entry = getEntry();
            if (entry.getKey() == null) {
                return;
            }
            int delta = contributionStep();
            if (mouseButton == 1) {
                delta = -delta;
            }
            sendContributionDelta(delta);
        }
    }
}
