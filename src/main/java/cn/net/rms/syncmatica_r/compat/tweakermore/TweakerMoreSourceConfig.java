package cn.net.rms.syncmatica_r.compat.tweakermore;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import me.fallenbreath.tweakermore.config.options.TweakerMoreConfigOptionList;

public final class TweakerMoreSourceConfig {
    public static final String CONFIG_NAME = "autoCollectMaterialListItemSource";
    public static final TweakerMoreConfigOptionList OPTION =
            new TweakerMoreConfigOptionList(CONFIG_NAME, TweakerMoreMaterialSource.DEFAULT);

    private static TweakerMoreConfigOptionList activeOption = OPTION;

    private TweakerMoreSourceConfig() {
    }

    public static void bind(final TweakerMoreConfigOptionList option) {
        activeOption = option;
    }

    public static boolean usesSyncmatica() {
        final IConfigOptionListEntry value = activeOption.getOptionListValue();
        return value != null && "syncmatica_r".equals(value.getStringValue());
    }
}
