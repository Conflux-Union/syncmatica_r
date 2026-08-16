package cn.net.rms.syncmatica_r.mixin;

import cn.net.rms.syncmatica_r.build_management.BuildScanTracker;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells build management which chunk columns changed, so a completion scan can
 * look at those instead of at everything.
 *
 * <p>This is the one place every server-side block change passes through with
 * both states in hand, and its shape has held from 1.17 to now — only the name
 * moved with the mappings. The alternative hooks are worse: {@code World} lost
 * {@code onBlockChanged} to a rename, and {@code WorldChunk.setBlockState}
 * changed its third parameter's type.
 *
 * <p>This runs on every redstone tick and every flowing water update on the
 * server, so it does as little as it possibly can: the tracker's first act is a
 * volatile read that costs nothing when no schematic is being tracked.
 */
@Mixin(ServerWorld.class)
public class MixinServerWorld {

//#if MC >= 260100
//$$     @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
//#else
    @Inject(method = "updateListeners", at = @At("HEAD"))
//#endif
    private void recordBuildScanChange(final BlockPos pos, final BlockState oldState, final BlockState newState,
                                       final int flags, final CallbackInfo ci) {
        // Block states are interned, so identity is the cheap form of this test.
        if (oldState != newState) {
            BuildScanTracker.onBlockChanged((ServerWorld) (Object) this, pos.getX(), pos.getZ());
        }
    }
}
