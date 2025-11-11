package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

public abstract class AbstractExchange implements Exchange {

    private final ExchangeTarget partner;
    private final Context context;
    private boolean success = false;
    private boolean finished = false;

    protected AbstractExchange(final ExchangeTarget partner, final Context con) {
        this.partner = partner;
        context = con;
    }

    protected static boolean checkUUID(final PacketByteBuf sourceBuf, final UUID targetId) {
        final int r = sourceBuf.readerIndex();
        final UUID sourceId = sourceBuf.readUuid();
        sourceBuf.readerIndex(r);
        return sourceId.equals(targetId);
    }

    @Override
    public ExchangeTarget getPartner() {
        return partner;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isSuccessful() {
        return success;
    }

    @Override
    public void close(final boolean notifyPartner) {
        finished = true;
        success = false;
        onClose();
        if (notifyPartner) {
            sendCancelPacket();
        }
    }

    public CommunicationManager getManager() {
        return context.getCommunicationManager();
    }

    protected void sendCancelPacket() {
    }

    protected void onClose() {
    }

    protected void succeed() {
        finished = true;
        success = true;

        onClose();
    }

}
