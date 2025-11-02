package ch.endte.syncmatica;

import ch.endte.syncmatica.util.SyncmaticaUtil;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedirectFileStorage implements IFileStorage {

    private final IFileStorage fs;

    private final Map<UUID, RedirectData> redirect = new HashMap<>();

    public RedirectFileStorage() {
        fs = new FileStorage();
    }

    public void addRedirect(final File file) {
        final RedirectData red = new RedirectData(file);
        redirect.put(red.getHash(), red);
    }

    @Override
    public LocalLitematicState getLocalState(final ServerPlacement placement) {
        final UUID hashId = placement.getHash();
        if (redirect.containsKey(hashId) && hashId.equals(redirect.get(hashId).getHash())) {
            return LocalLitematicState.LOCAL_LITEMATIC_PRESENT;
        } else {
            return fs.getLocalState(placement);
        }
    }

    @Override
    public File createLocalLitematic(final ServerPlacement placement) {
        return fs.createLocalLitematic(placement);
    }

    @Override
    public File getLocalLitematic(final ServerPlacement placement) {
        final UUID hashId = placement.getHash();
        if (redirect.containsKey(hashId)) {
            final RedirectData red = redirect.get(hashId);
            if (red.exists() && hashId.equals(red.getHash())) {
                return red.redirect;
            } else {
                redirect.remove(hashId);
            }
        }
        return fs.getLocalLitematic(placement);
    }

    @Override
    public void setContext(final Context con) {
        fs.setContext(con);
    }

    private class RedirectData {
        File redirect = null;
        UUID hash = null;
        long hashTimeStamp;

        RedirectData(File file) {
            redirect = file;
            getHash();
            if (hash == null) {
                file = null;
            }
        }

        UUID getHash() {
            if (hashTimeStamp == redirect.lastModified()) {
                return hash;
            }
            try {
                hash = SyncmaticaUtil.createChecksum(new FileInputStream(redirect));
            } catch (final Exception e) {
                e.printStackTrace();
                return null;
            }
            hashTimeStamp = redirect.lastModified();
            return hash;
        }

        boolean exists() {
            return redirect.exists() && redirect.canRead();
        }
    }

}
