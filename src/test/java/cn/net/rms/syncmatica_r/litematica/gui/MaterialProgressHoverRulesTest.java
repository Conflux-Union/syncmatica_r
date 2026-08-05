package cn.net.rms.syncmatica_r.litematica.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MaterialProgressHoverRulesTest {
    @Test
    void missingAmountUsesBreakdownTooltipInsteadOfClaimTooltip() {
        assertFalse(MaterialProgressHoverRules.shouldShowClaimTooltip(true, true, true));
    }

    @Test
    void blankRowAreaStillUsesClaimTooltip() {
        assertTrue(MaterialProgressHoverRules.shouldShowClaimTooltip(true, true, false));
    }

    @Test
    void disabledClaimTooltipStaysHidden() {
        assertFalse(MaterialProgressHoverRules.shouldShowClaimTooltip(false, true, false));
    }

    @Test
    void claimTooltipStaysHiddenOutsideRow() {
        assertFalse(MaterialProgressHoverRules.shouldShowClaimTooltip(true, false, false));
    }
}
