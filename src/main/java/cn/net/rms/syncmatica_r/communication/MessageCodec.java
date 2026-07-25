package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.Feature;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/**
 * Wire layout of {@link PacketType#MESSAGE}: a message type, a translation key
 * and an optional language-neutral detail such as {@code 96.0 MB > 64.0 MB}.
 *
 * <p>The detail is only written for peers that announced
 * {@link Feature#LIMIT_REPORT}; everyone else keeps reading the historic
 * two-field layout. Writer and reader live together so the optional trailing
 * field cannot drift apart.</p>
 */
final class MessageCodec {

    private static final int TYPE_LENGTH = 32;

    private MessageCodec() {
    }

    static PacketByteBuf encode(final FeatureSet partnerFeatures, final MessageType type,
                                final String identifier, final String detail) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(type.toString(), TYPE_LENGTH);
        buf.writeString(identifier, ProtocolLimits.MAX_MESSAGE_LENGTH);
        if (detail != null && !detail.isEmpty()
                && partnerFeatures != null && partnerFeatures.hasFeature(Feature.LIMIT_REPORT)) {
            buf.writeString(detail, ProtocolLimits.MAX_MESSAGE_DETAIL_LENGTH);
        }
        return buf;
    }

    static MessageType readType(final PacketByteBuf buf) {
        return MessageType.valueOf(buf.readString(TYPE_LENGTH));
    }

    static String readIdentifier(final PacketByteBuf buf) {
        return buf.readString(ProtocolLimits.MAX_MESSAGE_LENGTH);
    }

    static String readDetail(final PacketByteBuf buf) {
        return buf.readableBytes() > 0 ? buf.readString(ProtocolLimits.MAX_MESSAGE_DETAIL_LENGTH) : "";
    }
}
