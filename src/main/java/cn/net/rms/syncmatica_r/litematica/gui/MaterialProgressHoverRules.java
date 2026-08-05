package cn.net.rms.syncmatica_r.litematica.gui;

final class MaterialProgressHoverRules {
    private MaterialProgressHoverRules() {
    }

    static boolean shouldShowClaimTooltip(final boolean claimTooltipEnabled,
                                          final boolean rowHovered,
                                          final boolean missingAmountHovered) {
        return claimTooltipEnabled && rowHovered && !missingAmountHovered;
    }
}
