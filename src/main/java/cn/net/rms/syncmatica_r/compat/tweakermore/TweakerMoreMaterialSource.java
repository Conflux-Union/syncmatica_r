package cn.net.rms.syncmatica_r.compat.tweakermore;

import me.fallenbreath.tweakermore.config.options.listentries.EnumOptionEntry;

public enum TweakerMoreMaterialSource implements EnumOptionEntry {
    LITEMATICA,
    SYNCMATICA_R;

    public static final TweakerMoreMaterialSource DEFAULT = LITEMATICA;

    @Override
    public EnumOptionEntry[] getAllValues() {
        return values();
    }

    @Override
    public EnumOptionEntry getDefault() {
        return DEFAULT;
    }

    @Override
    public String getTranslationPrefix() {
        return "tweakermore.list_entry.autoCollectMaterialListItemSource.";
    }
}
