package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.exchange.ShareLitematicExchange;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.Message;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class ModifyExchangeClient extends AbstractExchange {

    private final ServerPlacement placement;
    private final SchematicPlacement litematic;
    private boolean expectRemove = false;

    public ModifyExchangeClient(final ServerPlacement placement, final ExchangeTarget partner, final Context con) {
        super(partner, con);
        this.placement = placement;
        litematic = LitematicManager.getInstance().schematicFromSyncmatic(placement);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        if (id.equals(PacketType.MODIFY_REQUEST_DENY.identifier)
                || id.equals(PacketType.MODIFY_REQUEST_ACCEPT.identifier)
                || (expectRemove && id.equals(PacketType.REMOVE_SYNCMATIC.identifier))) {
            return AbstractExchange.checkUUID(packetBuf, placement.getId());
        }
        return false;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        if (id.equals(PacketType.MODIFY_REQUEST_DENY.identifier)) {
            packetBuf.readUuid();
            close(false);
            if (!litematic.isLocked()) {
                litematic.setOrigin(placement.getPosition(), null);
                litematic.setRotation(placement.getRotation(), null);
                litematic.setMirror(placement.getMirror(), null);
                litematic.toggleLocked();
            }
            ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.SUCCESS, "syncmatica_r.error.modification_deny"));
        } else if (id.equals(PacketType.MODIFY_REQUEST_ACCEPT.identifier)) {
            packetBuf.readUuid();
            acceptModification();
        } else if (id.equals(PacketType.REMOVE_SYNCMATIC.identifier)) {
            packetBuf.readUuid();
            final ShareLitematicExchange legacyModify = new ShareLitematicExchange(litematic, getPartner(), getContext(), placement);
            getContext().getCommunicationManager().startExchange(legacyModify);
            succeed();
        }
    }

    @Override
    public void init() {
        if (getContext().getCommunicationManager().getModifier(placement) != null) {
            close(false);
            return;
        }
        getContext().getCommunicationManager().setModifier(placement, this);
        if (getPartner().getFeatureSet().hasFeature(Feature.MODIFY)) {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getPartner().sendPacket(PacketType.MODIFY_REQUEST.identifier, buf, getContext());
        } else {
            acceptModification();
        }
    }

    private void acceptModification() {
        if (litematic.isLocked()) {
            litematic.toggleLocked();
        }
        ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.SUCCESS, "syncmatica_r.success.modification_accepted"));
        getContext().getSyncmaticManager().updateServerPlacement(placement);
    }

    public void conclude() {
        LitematicManager.getInstance().updateServerPlacement(litematic, placement);
        sendFinish();
        if (!litematic.isLocked()) {
            litematic.toggleLocked();
        }
        getContext().getSyncmaticManager().updateServerPlacement(placement);
    }

    private void sendFinish() {
        if (getPartner().getFeatureSet().hasFeature(Feature.MODIFY)) {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getContext().getCommunicationManager().putPositionData(placement, buf, getPartner());
            getContext().getCommunicationManager().putMaterialData(placement, buf, getPartner());
            getPartner().sendPacket(PacketType.MODIFY_FINISH.identifier, buf, getContext());
            succeed();
            getContext().getCommunicationManager().notifyClose(this);
        } else {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getPartner().sendPacket(PacketType.REMOVE_SYNCMATIC.identifier, buf, getContext());
            expectRemove = true;
        }
    }

    @Override
    protected void sendCancelPacket() {
        if (getPartner().getFeatureSet().hasFeature(Feature.MODIFY)) {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getContext().getCommunicationManager().putPositionData(placement, buf, getPartner());
            getContext().getCommunicationManager().putMaterialData(placement, buf, getPartner());
            getPartner().sendPacket(PacketType.MODIFY_FINISH.identifier, buf, getContext());
        }
    }

    @Override
    protected void onClose() {
        if (getContext().getCommunicationManager().getModifier(placement) == this) {
            getContext().getCommunicationManager().setModifier(placement, null);
        }
    }

}
