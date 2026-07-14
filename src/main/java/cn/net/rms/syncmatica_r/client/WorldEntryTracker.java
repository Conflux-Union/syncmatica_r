package cn.net.rms.syncmatica_r.client;

final class WorldEntryTracker {
    private boolean inGame;

    boolean update(final boolean currentlyInGame) {
        if (!currentlyInGame) {
            inGame = false;
            return false;
        }
        if (inGame) {
            return false;
        }
        inGame = true;
        return true;
    }
}
