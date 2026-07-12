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
//$$ public final class SyncmaticaPayload implements CustomPayload {
//$$
//$$     public static final Id<SyncmaticaPayload> PACKET_ID = new Id<>(Identifier.of("syncmatica_r", "payload"));
//$$     public static final PacketCodec<PacketByteBuf, SyncmaticaPayload> CODEC =
//$$             PacketCodec.of(SyncmaticaPayload::write, SyncmaticaPayload::decode);
//$$
//$$     private final Identifier id;
//$$     private final byte[] data;
//$$
//$$     public SyncmaticaPayload(final Identifier id, final PacketByteBuf source) {
//$$         this(id, copyRemaining(source));
//$$     }
//$$
//$$     private SyncmaticaPayload(final Identifier id, final byte[] data) {
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
//$$     public Id<SyncmaticaPayload> getId() {
//$$         return PACKET_ID;
//$$     }
//$$
//#if MC >= 260100
//$$     private static void write(final PacketByteBuf buf, final SyncmaticaPayload payload) {
//#else
//$$     private static void write(final SyncmaticaPayload payload, final PacketByteBuf buf) {
//#endif
//$$         buf.writeIdentifier(payload.id);
//$$         buf.writeBytes(payload.data);
//$$     }
//$$
//$$     private static SyncmaticaPayload decode(final PacketByteBuf buf) {
//$$         final Identifier identifier = buf.readIdentifier();
//$$         final byte[] bytes = new byte[buf.readableBytes()];
//$$         buf.readBytes(bytes);
//$$         return new SyncmaticaPayload(identifier, bytes);
//$$     }
//$$
//$$     private static byte[] copyRemaining(final PacketByteBuf source) {
//$$         final byte[] bytes = new byte[source.readableBytes()];
//$$         source.getBytes(source.readerIndex(), bytes);
//$$         return bytes;
//$$     }
//$$ }
//#endif
