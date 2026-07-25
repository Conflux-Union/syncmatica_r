package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.LocalLitematicState;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.gui.BaseButtonType;
import cn.net.rms.syncmatica_r.material.MaterialAvailability;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
import net.minecraft.network.PacketByteBuf;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class WidgetSyncmaticaServerPlacementEntry extends WidgetListEntryBase<ServerPlacement> {

    private final ServerPlacement placement;
    private final boolean isOdd;

    public WidgetSyncmaticaServerPlacementEntry(final int x, int y, final int width, final int height, final ServerPlacement entry,
                                                final int listIndex) {
        super(x, y, width, height, entry, listIndex);
        placement = entry;
        isOdd = (listIndex % 2 == 1);
        y += 1;

        int posX = x + width;
        int len;
        ButtonListener listener;
        String text;

        text = StringUtils.translate("syncmatica_r.gui.button.remove");
        len = getStringWidth(text) + 10;
        posX -= (len + 2);
        listener = new ButtonListener(ButtonListener.Type.REMOVE, this);
        addButton(new ButtonGeneric(posX, y, len, 20, text), listener);

        text = StringUtils.translate("syncmatica_r.gui.button.material_gathering_placement");
        len = getStringWidth(text) + 10;
        posX -= (len + 2);
        listener = new ButtonListener(ButtonListener.Type.MATERIAL_GATHERING, this);
        final ButtonGeneric matGathering = new ButtonGeneric(posX, y, len, 20, text);
        final Context context = LitematicManager.getInstance().getActiveContext();
        final boolean materialFeature = context.getCommunicationManager() instanceof ClientCommunicationManager
                && ((ClientCommunicationManager) context.getCommunicationManager()).getServer().getFeatureSet().hasFeature(Feature.MATERIAL_PROGRESS);
        matGathering.setEnabled(materialFeature);
        final MaterialAvailability availability = placement.getMaterialAvailability();
        if (materialFeature && availability.isBlocked()) {
            matGathering.setHoverStrings(StringUtils.translate(availability.getTranslationKey()));
        }
        addButton(matGathering, listener);

        final ArrayList<IButtonType> multi = new ArrayList<>();
        multi.add(new BaseButtonType("syncmatica_r.gui.button.downloading"
                , () -> LitematicManager.getInstance().getActiveContext().getCommunicationManager().getDownloadState(placement)
                , null));
        multi.add(new BaseButtonType("syncmatica_r.gui.button.download"
                , () -> {
            final Context con = LitematicManager.getInstance().getActiveContext();
            final LocalLitematicState state = con.getFileStorage().getLocalState(placement);
            return !state.isLocalFileReady() && state.isReadyForDownload();
        }, new ButtonListener(ButtonListener.Type.DOWNLOAD, this)));
        multi.add(new BaseButtonType("syncmatica_r.gui.button.load",
                () -> !LitematicManager.getInstance().isRendered(placement),
                new ButtonListener(ButtonListener.Type.LOAD, this)));
        multi.add(new BaseButtonType("syncmatica_r.gui.button.unload",
                () -> LitematicManager.getInstance().isRendered(placement),
                new ButtonListener(ButtonListener.Type.UNLOAD, this)));

        final ButtonGeneric button = new MultiTypeButton(posX, y, true, multi);
        addButton(button, null);
    }

//#if MC >= 12111
//$$     @Override
//$$     public void render(final GuiContext guiContext, final int mouseX, final int mouseY, final boolean selected) {
//$$         final DrawContext drawContext = guiContext;
//#elseif MC >= 12106
//$$     @Override
//$$     public void render(final DrawContext drawContext, final int mouseX, final int mouseY, final boolean selected) {
//#else
    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected,
//#if MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrixStack
//#endif
    ) {
//#endif

        //#if MC < 12106
        RenderUtils.color(1f, 1f, 1f, 1f);
        //#endif

        if (selected || isMouseOver(mouseX, mouseY)) {
//#if MC >= 12111
//$$             RenderUtils.drawRect(guiContext, x, y, width, height, 0x70FFFFFF);
//#elseif MC >= 12106
//$$             RenderUtils.drawRect(drawContext, x, y, width, height, 0x70FFFFFF);
//#else
            RenderUtils.drawRect(x, y, width, height, 0x70FFFFFF);
//#endif
        } else if (isOdd) {
//#if MC >= 12111
//$$             RenderUtils.drawRect(guiContext, x, y, width, height, 0x20FFFFFF);
//#elseif MC >= 12106
//$$             RenderUtils.drawRect(drawContext, x, y, width, height, 0x20FFFFFF);
//#else
            RenderUtils.drawRect(x, y, width, height, 0x20FFFFFF);
//#endif
        } else {
//#if MC >= 12111
//$$             RenderUtils.drawRect(guiContext, x, y, width, height, 0x50FFFFFF);
//#elseif MC >= 12106
//$$             RenderUtils.drawRect(drawContext, x, y, width, height, 0x50FFFFFF);
//#else
            RenderUtils.drawRect(x, y, width, height, 0x50FFFFFF);
//#endif
        }

        final String schematicName = placement.getName();
//#if MC >= 12111
//$$         drawString(guiContext, x + 20, y + 7, 0xFFFFFFFF, schematicName);
//$$         drawSubWidgets(guiContext, mouseX, mouseY);
//#elseif MC >= 12106
//$$         drawString(drawContext, x + 20, y + 7, 0xFFFFFFFF, schematicName);
//$$         drawSubWidgets(drawContext, mouseX, mouseY);
//#elseif MC >= 12001
//$$         drawString(x + 20, y + 7, 0xFFFFFFFF, schematicName, drawContext);
//$$         drawSubWidgets(mouseX, mouseY, drawContext);
//#else
        drawString(x + 20, y + 7, 0xFFFFFFFF, schematicName, matrixStack);
        drawSubWidgets(mouseX, mouseY, matrixStack);
//#endif
    }

    private static class ButtonListener implements IButtonActionListener {

        Type type;
        WidgetSyncmaticaServerPlacementEntry placement;

        public ButtonListener(final Type type, final WidgetSyncmaticaServerPlacementEntry placement) {
            this.type = type;
            this.placement = placement;
        }

        @Override
        public void actionPerformedWithButton(final ButtonBase button, final int arg1) {
            if (type == null) {
                return;
            }
            button.setEnabled(false);
            type.onAction(placement);
        }

        public enum Type {
            LOAD() {
                @Override
                void onAction(final WidgetSyncmaticaServerPlacementEntry placement) {
                    LitematicManager.getInstance().renderSyncmatic(placement.placement);
                }
            },
            UNLOAD() {
                @Override
                void onAction(final WidgetSyncmaticaServerPlacementEntry placement) {
                    LitematicManager.getInstance().unrenderSyncmatic(placement.placement);
                }
            },
            DOWNLOAD() {
                @Override
                void onAction(final WidgetSyncmaticaServerPlacementEntry placement) {
                    final Context con = LitematicManager.getInstance().getActiveContext();
                    final ExchangeTarget server = ((ClientCommunicationManager) con.getCommunicationManager()).getServer();
                    if (con.getCommunicationManager().getDownloadState(placement.placement)) {
                        return;
                    }
                    try {
                        con.getCommunicationManager().download(placement.placement, server);
                    } catch (final NoSuchAlgorithmException | IOException e) {
                        e.printStackTrace();
                    }
                }
            },
            REMOVE() {
                @Override
                void onAction(final WidgetSyncmaticaServerPlacementEntry placement) {
                    final Context con = LitematicManager.getInstance().getActiveContext();
                    final ExchangeTarget server = ((ClientCommunicationManager) con.getCommunicationManager()).getServer();
                    final PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
                    packetBuf.writeUuid(placement.placement.getId());
                    server.sendPacket(PacketType.REMOVE_SYNCMATIC.toIdentifier(server.getProtocolFlavor()), packetBuf, LitematicManager.getInstance().getActiveContext());
                }
            },
            MATERIAL_GATHERING() {
                @Override
                void onAction(final WidgetSyncmaticaServerPlacementEntry placement) {
                    final GuiSyncmaticaMaterialProgress gui = new GuiSyncmaticaMaterialProgress(placement.placement);
                    GuiBase.openGui(gui);
                }
            };

            abstract void onAction(WidgetSyncmaticaServerPlacementEntry placement);
        }

    }

}
