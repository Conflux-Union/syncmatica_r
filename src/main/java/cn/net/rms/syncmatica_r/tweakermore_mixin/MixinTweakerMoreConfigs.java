package cn.net.rms.syncmatica_r.tweakermore_mixin;

import cn.net.rms.syncmatica_r.compat.tweakermore.TweakerMoreSourceConfig;
import fi.dy.masa.malilib.config.IConfigBase;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import me.fallenbreath.tweakermore.config.Config;
import me.fallenbreath.tweakermore.config.TweakerMoreConfigs;
import me.fallenbreath.tweakermore.config.TweakerMoreOption;
import me.fallenbreath.tweakermore.config.options.TweakerMoreConfigOptionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TweakerMoreConfigs.class, remap = false)
public abstract class MixinTweakerMoreConfigs {
    @Shadow(remap = false)
    @Final
    private static List<TweakerMoreOption> OPTIONS;

    @Shadow(remap = false)
    @Final
    private static Map<Config.Category, List<TweakerMoreOption>> CATEGORY_TO_OPTION;

    @Shadow(remap = false)
    @Final
    private static Map<Config.Type, List<TweakerMoreOption>> TYPE_TO_OPTION;

    @Shadow(remap = false)
    @Final
    private static Map<IConfigBase, TweakerMoreOption> CONFIG_TO_OPTION;

    @Shadow(remap = false)
    @Final
    private static Map<String, TweakerMoreOption> NAME_TO_OPTION;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void registerSyncmaticaMaterialSource(final CallbackInfo callbackInfo) {
        final TweakerMoreOption existing = NAME_TO_OPTION.get(TweakerMoreSourceConfig.CONFIG_NAME);
        if (existing != null) {
            TweakerMoreSourceConfig.bind((TweakerMoreConfigOptionList) existing.getConfig());
            return;
        }

        try {
            final Field siblingField = TweakerMoreConfigs.class.getDeclaredField(
                    "AUTO_COLLECT_MATERIAL_LIST_ITEM_MESSAGE_TYPE");
            final Config annotation = siblingField.getAnnotation(Config.class);
            if (annotation == null) {
                throw new IllegalStateException("TweakerMore source config sibling has no Config annotation");
            }

            final TweakerMoreOption option = new TweakerMoreOption(
                    "AUTO_COLLECT_MATERIAL_LIST_ITEM_SOURCE",
                    annotation,
                    TweakerMoreSourceConfig.OPTION);
            OPTIONS.add(option);
            CATEGORY_TO_OPTION.computeIfAbsent(option.getCategory(), key -> new java.util.ArrayList<>()).add(option);
            TYPE_TO_OPTION.computeIfAbsent(option.getType(), key -> new java.util.ArrayList<>()).add(option);
            CONFIG_TO_OPTION.put(option.getConfig(), option);
            NAME_TO_OPTION.put(option.getConfig().getName(), option);
            TweakerMoreSourceConfig.bind(TweakerMoreSourceConfig.OPTION);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register Syncmatica_r material source in TweakerMore", exception);
        }
    }
}
