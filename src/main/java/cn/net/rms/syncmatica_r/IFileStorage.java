package cn.net.rms.syncmatica_r;

import java.io.File;

public interface IFileStorage {
    LocalLitematicState getLocalState(ServerPlacement placement);

    File createLocalLitematic(ServerPlacement placement);

    File getLocalLitematic(ServerPlacement placement);

    void setContext(Context con);
}
