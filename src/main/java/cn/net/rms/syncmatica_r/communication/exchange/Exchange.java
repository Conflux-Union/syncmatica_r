package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public interface Exchange {

    ExchangeTarget getPartner();

    Context getContext();

    boolean checkPacket(Identifier id, PacketByteBuf packetBuf);

    void handle(Identifier id, PacketByteBuf packetBuf);

    boolean isFinished();

    boolean isSuccessful();

    void close(boolean notifyPartner);

    void init();

}
