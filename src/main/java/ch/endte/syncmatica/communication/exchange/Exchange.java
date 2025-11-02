package ch.endte.syncmatica.communication.exchange;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.communication.ExchangeTarget;
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
