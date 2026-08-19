package cn.net.rms.syncmatica_r.compat.tweakermore;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class TweakerMoreMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger(TweakerMoreMixinPlugin.class);
    private static final String TWEAKERMORE_MOD_ID = "tweakermore";

    private boolean enabled;

    @Override
    public void onLoad(final String mixinPackage) {
        final ModContainer container = FabricLoader.getInstance()
                .getModContainer(TWEAKERMORE_MOD_ID)
                .orElse(null);
        if (container == null) {
            enabled = false;
            return;
        }

        final String version = container.getMetadata().getVersion().getFriendlyString();
        enabled = isSupportedVersion(version);
        if (!enabled) {
            LOGGER.warn("Syncmatica_r TweakerMore integration is disabled for unsupported TweakerMore version {}",
                    version);
        }
    }

    static boolean isSupportedVersion(final String version) {
        return version != null && version.startsWith("3.33.");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return enabled;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName,
                         final ClassNode targetClass,
                         final String mixinClassName,
                         final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName,
                          final ClassNode targetClass,
                          final String mixinClassName,
                          final IMixinInfo mixinInfo) {
    }
}
