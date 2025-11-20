package cn.net.rms.syncmatica_r.mixin;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
//$$ import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
//#else
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
//#endif
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CustomPayloadC2SPacket.class)
public interface MixinCustomPayloadC2SPacket {
//#if MC < 12005
    @Accessor("channel")
    Identifier getChannel();

    @Accessor("data")
    PacketByteBuf getData();
//#endif
}
