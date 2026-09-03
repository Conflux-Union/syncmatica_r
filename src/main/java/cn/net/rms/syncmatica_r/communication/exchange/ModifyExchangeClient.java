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
    private final boolean concludeImmediately;
    private boolean expectRemove = false;

    public ModifyExchangeClient(final ServerPlacement placement, final ExchangeTarget partner, final Context con) {
        this(placement, partner, con, false);
    }

    public ModifyExchangeClient(final ServerPlacement placement, final ExchangeTarget partner, final Context con,
                                final boolean concludeImmediately) {
        super(partner, con);
        this.placement = placement;
        this.concludeImmediately = concludeImmediately;
        litematic = LitematicManager.getInstance().schematicFromSyncmatic(placement);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.MODIFY_REQUEST_DENY
                || type == PacketType.MODIFY_REQUEST_ACCEPT
                || (expectRemove && type == PacketType.REMOVE_SYNCMATIC)) {
            return AbstractExchange.checkUUID(packetBuf, placement.getId());
        }
        return false;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.MODIFY_REQUEST_DENY) {
            packetBuf.readUuid();
            if (!litematic.isLocked()) {
                litematic.setOrigin(placement.getPosition(), null);
                litematic.setRotation(placement.getRotation(), null);
                litematic.setMirror(placement.getMirror(), null);
                litematic.toggleLocked();
            }
            if (!litematic.getName().equals(placement.getName())) {
                litematic.setName(placement.getName());
            }
            close(false);
            ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.SUCCESS, "syncmatica_r.error.modification_deny"));
        } else if (type == PacketType.MODIFY_REQUEST_ACCEPT) {
            packetBuf.readUuid();
            acceptModification();
        } else if (type == PacketType.REMOVE_SYNCMATIC) {
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
            getPartner().sendPacket(PacketType.MODIFY_REQUEST.toIdentifier(getPartner().getProtocolFlavor()), buf, getContext());
        } else {
            acceptModification();
        }
    }

    private void acceptModification() {
        if (litematic.isLocked()) {
            litematic.toggleLocked();
        }
        if (concludeImmediately) {
            conclude(false);
            return;
        }
        ScreenHelper.ifPresent(s -> s.addMessage(Message.MessageType.SUCCESS, "syncmatica_r.success.modification_accepted"));
        getContext().getSyncmaticManager().updateServerPlacement(placement);
    }

    public void conclude() {
        conclude(true);
    }

    private void conclude(final boolean notifyClose) {
        if (getPartner().getFeatureSet().hasFeature(Feature.PLACEMENT_RENAME)) {
            placement.setDisplayName(litematic.getName());
        }
        LitematicManager.getInstance().updateServerPlacement(litematic, placement);
        sendFinish(notifyClose);
        if (!litematic.isLocked()) {
            litematic.toggleLocked();
        }
        getContext().getSyncmaticManager().updateServerPlacement(placement);
    }

    private void sendFinish(final boolean notifyClose) {
        if (getPartner().getFeatureSet().hasFeature(Feature.MODIFY)) {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getContext().getCommunicationManager().putModificationData(placement, buf, getPartner());
            getPartner().sendPacket(PacketType.MODIFY_FINISH.toIdentifier(getPartner().getProtocolFlavor()), buf, getContext());
            succeed();
            if (notifyClose) {
                getContext().getCommunicationManager().notifyClose(this);
            }
        } else {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getPartner().sendPacket(PacketType.REMOVE_SYNCMATIC.toIdentifier(getPartner().getProtocolFlavor()), buf, getContext());
            expectRemove = true;
        }
    }

    @Override
    protected void sendCancelPacket() {
        if (getPartner().getFeatureSet().hasFeature(Feature.MODIFY)) {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            getContext().getCommunicationManager().putModificationData(placement, buf, getPartner());
            getPartner().sendPacket(PacketType.MODIFY_FINISH.toIdentifier(getPartner().getProtocolFlavor()), buf, getContext());
        }
    }

    @Override
    protected void onClose() {
        if (getContext().getCommunicationManager().getModifier(placement) == this) {
            getContext().getCommunicationManager().setModifier(placement, null);
        }
    }

}
