package cn.net.rms.syncmatica_r.communication;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.service.MaterialService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Packet-boundary behavior separated from player/network lookup so its visible
 * replies and broadcasts can be tested without constructing a Minecraft player.
 */
final class MaterialClaimPacketAdapter {
    interface Feedback {
        void send(MessageType type, String identifier);
    }

    private MaterialClaimPacketAdapter() {
    }

    static void handleToggle(final MaterialService service,
                             final ServerPlacement placement,
                             final MaterialKey key,
                             final Supplier<PlayerIdentifier> player,
                             final BooleanSupplier permitted,
                             final Feedback feedback) {
        if (!canAttempt(service, placement)) {
            return;
        }
        if (!permitted.getAsBoolean()) {
            feedback.send(MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        final PlayerIdentifier resolvedPlayer = player.get();
        handleDesiredState(
                service,
                placement,
                key,
                resolvedPlayer,
                !service.isClaimedBy(placement, key, resolvedPlayer),
                true,
                feedback
        );
    }

    static void handleDesiredState(final MaterialService service,
                                   final ServerPlacement placement,
                                   final MaterialKey key,
                                   final PlayerIdentifier player,
                                   final boolean claimed,
                                   final boolean permitted,
                                   final Feedback feedback) {
        if (!canAttempt(service, placement)) {
            return;
        }
        if (!permitted) {
            feedback.send(MessageType.ERROR, "syncmatica_r.error.permission_denied");
            return;
        }
        reportConflict(service, placement, key, service.setClaim(placement, key, player, claimed), feedback);
    }

    private static boolean canAttempt(final MaterialService service, final ServerPlacement placement) {
        return service != null && service.isEnabled() && placement != null;
    }

    private static void reportConflict(final MaterialService service,
                                       final ServerPlacement placement,
                                       final MaterialKey key,
                                       final MaterialService.ClaimOutcome outcome,
                                       final Feedback feedback) {
        if (outcome != MaterialService.ClaimOutcome.CLAIMED_BY_OTHER) {
            return;
        }
        final PlayerIdentifier owner = service.getClaimant(placement, key);
        feedback.send(MessageType.WARNING, "Already claimed by " + (owner == null ? "" : owner.getName()));
    }
}
