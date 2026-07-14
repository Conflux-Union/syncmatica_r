package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

public abstract class AbstractExchange implements Exchange {

    private final ExchangeTarget partner;
    private final Context context;
    private boolean success = false;
    private boolean finished = false;
    private long lastActivityMillis = System.currentTimeMillis();

    protected AbstractExchange(final ExchangeTarget partner, final Context con) {
        this.partner = partner;
        context = con;
    }

    protected static boolean checkUUID(final PacketByteBuf sourceBuf, final UUID targetId) {
        if (sourceBuf.readableBytes() < Long.BYTES * 2 || targetId == null) {
            return false;
        }
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
    public void markActivity() {
        lastActivityMillis = System.currentTimeMillis();
    }

    @Override
    public boolean isTimedOut(final long nowMillis) {
        return !finished && nowMillis - lastActivityMillis >= ProtocolLimits.EXCHANGE_TIMEOUT_MILLIS;
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
