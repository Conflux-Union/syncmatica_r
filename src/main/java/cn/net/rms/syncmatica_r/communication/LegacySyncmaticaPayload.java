//#if MC >= 12005
//$$ package cn.net.rms.syncmatica_r.communication;
//$$
//$$ import io.netty.buffer.Unpooled;
//$$ import net.minecraft.network.PacketByteBuf;
//$$ import net.minecraft.network.codec.PacketCodec;
//$$ import net.minecraft.network.packet.CustomPayload;
//$$ import net.minecraft.util.Identifier;
//$$
//$$ import java.util.Objects;
//$$
//$$ /**
//$$  * Upstream (1.20.6+) peers only exchange payloads on syncmatica:main;
//$$  * the inner [Identifier][bytes] body is identical to SyncmaticaPayload.
//$$  */
//$$ public final class LegacySyncmaticaPayload implements CustomPayload {
//$$
//$$     public static final Id<LegacySyncmaticaPayload> PACKET_ID = new Id<>(Identifier.of("syncmatica", "main"));
//$$     public static final PacketCodec<PacketByteBuf, LegacySyncmaticaPayload> CODEC =
//$$             PacketCodec.of(LegacySyncmaticaPayload::write, LegacySyncmaticaPayload::decode);
//$$
//$$     private final Identifier id;
//$$     private final byte[] data;
//$$
//$$     public LegacySyncmaticaPayload(final Identifier id, final PacketByteBuf source) {
//$$         this(id, copyRemaining(source));
//$$     }
//$$
//$$     private LegacySyncmaticaPayload(final Identifier id, final byte[] data) {
//$$         this.id = Objects.requireNonNull(id, "id");
//$$         this.data = Objects.requireNonNull(data, "data");
//$$     }
//$$
//$$     public Identifier id() {
//$$         return id;
//$$     }
//$$
//$$     public PacketByteBuf asPacketByteBuf() {
//$$         return new PacketByteBuf(Unpooled.wrappedBuffer(data));
//$$     }
//$$
//$$     @Override
//$$     public Id<LegacySyncmaticaPayload> getId() {
//$$         return PACKET_ID;
//$$     }
//$$
//$$     private static void write(final LegacySyncmaticaPayload payload, final PacketByteBuf buf) {
//$$         buf.writeIdentifier(payload.id);
//$$         buf.writeBytes(payload.data);
//$$     }
//$$
//$$     private static LegacySyncmaticaPayload decode(final PacketByteBuf buf) {
//$$         ProtocolLimits.requirePacketSize(buf.readableBytes());
//$$         final Identifier identifier = buf.readIdentifier();
//$$         final byte[] bytes = new byte[buf.readableBytes()];
//$$         buf.readBytes(bytes);
//$$         return new LegacySyncmaticaPayload(identifier, bytes);
//$$     }
//$$
//$$     private static byte[] copyRemaining(final PacketByteBuf source) {
//$$         final byte[] bytes = new byte[ProtocolLimits.requirePacketSize(source.readableBytes())];
//$$         source.getBytes(source.readerIndex(), bytes);
//$$         return bytes;
//$$     }
//$$ }
//#endif
