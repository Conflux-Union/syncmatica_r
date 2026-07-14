package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileStorage implements IFileStorage {
    private static final Logger LOGGER = LogManager.getLogger(FileStorage.class);

    private final Map<ServerPlacement, Long> buffer = new WeakHashMap<>();
    private Context context = null;

    @Override
    public void setContext(final Context con) {
        if (context == null) {
            context = con;
        } else {
            throw new Context.DuplicateContextAssignmentException("Duplicate Context assignment");
        }
    }

    @Override
    public LocalLitematicState getLocalState(final ServerPlacement placement) {
        final File localFile = getSchematicPath(placement);
        if (localFile.isFile()) {
            if (isDownloading(placement)) {
                return LocalLitematicState.DOWNLOADING_LITEMATIC;
            }
            if ((buffer.containsKey(placement) && buffer.get(placement) == localFile.lastModified()) || hashCompare(localFile, placement)) {
                return LocalLitematicState.LOCAL_LITEMATIC_PRESENT;
            }
            return LocalLitematicState.LOCAL_LITEMATIC_DESYNC;
        }
        return LocalLitematicState.NO_LOCAL_LITEMATIC;
    }

    private boolean isDownloading(final ServerPlacement placement) {
        if (context == null) {
            throw new RuntimeException("No CommunicationManager has been set yet - cannot get litematic state");
        }
        return context.getCommunicationManager().getDownloadState(placement);
    }

    @Override
    public File getLocalLitematic(final ServerPlacement placement) {
        if (getLocalState(placement).isLocalFileReady()) {
            return getSchematicPath(placement);
        } else {
            return null;
        }
    }

    @Override
    public File createLocalLitematic(final ServerPlacement placement) {
        if (getLocalState(placement).isLocalFileReady()) {
            throw new IllegalArgumentException("");
        }
        final File file = getSchematicPath(placement);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IllegalStateException("Failed to replace litematic file " + file.getAbsolutePath());
            }
        }
        try {
            if (!file.createNewFile()) {
                throw new IOException("Failed to create litematic file " + file.getAbsolutePath());
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create litematic file " + file.getAbsolutePath(), e);
        }
        return file;
    }

    private boolean hashCompare(final File localFile, final ServerPlacement placement) {
        UUID hash = null;
        try {
            hash = SyncmaticaUtil.createChecksum(new FileInputStream(localFile));
        } catch (final Exception e) {

            e.printStackTrace();
        }

        if (hash == null) {
            return false;
        }
        if (hash.equals(placement.getHash())) {
            buffer.put(placement, localFile.lastModified());
            return true;
        }
        return false;
    }

    private File getSchematicPath(final ServerPlacement placement) {
        final File litematicPath = context.getLitematicFolder();
        if (context.isServer()) {
            return new File(litematicPath, placement.getHash().toString() + ".litematic");
        }
        final File preferred = new File(litematicPath, placement.getId().toString() + ".litematic");
        if (preferred.exists()) {
            return preferred;
        }
        final String legacyName = SyncmaticaUtil.sanitizeFileName(placement.getName());
        final File legacy = new File(litematicPath, legacyName + ".litematic");
        if (!legacy.isFile() || !placement.getHash().equals(checksum(legacy))) {
            return preferred;
        }
        try {
            Files.move(legacy.toPath(), preferred.toPath());
            return preferred;
        } catch (final IOException exception) {
            LOGGER.warn("Failed to migrate legacy litematic file {}", legacy.getAbsolutePath(), exception);
            return legacy;
        }
    }

    private UUID checksum(final File file) {
        try {
            return SyncmaticaUtil.createChecksum(new FileInputStream(file));
        } catch (final Exception exception) {
            LOGGER.warn("Failed to checksum litematic file {}", file.getAbsolutePath(), exception);
            return null;
        }
    }
}
