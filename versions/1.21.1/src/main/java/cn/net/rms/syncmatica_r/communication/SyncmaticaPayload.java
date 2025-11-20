package cn.net.rms.syncmatica_r.communication;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncmaticaPayload(Identifier id, PacketByteBuf data) implements CustomPayload {

    public static final Id<SyncmaticaPayload> PACKET_ID = new Id<>(Identifier.of("syncmatica_r", "payload"));
    public static final PacketCodec<PacketByteBuf, SyncmaticaPayload> CODEC = PacketCodec.of(SyncmaticaPayload::write, SyncmaticaPayload::new);

    public SyncmaticaPayload(final PacketByteBuf buf) {
        this(buf.readIdentifier(), new PacketByteBuf(buf.readBytes(buf.readableBytes())));
    }

    private static void write(final SyncmaticaPayload payload, final PacketByteBuf buf) {
        buf.writeIdentifier(payload.id());
        buf.writeBytes(payload.data().copy());
    }

    @Override
    public Id<SyncmaticaPayload> getId() {
        return PACKET_ID;
    }
}
