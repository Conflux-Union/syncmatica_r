package cn.net.rms.syncmatica_r.litematica;

import java.util.UUID;

public interface IIDContainer {
    UUID getServerId();

    void setServerId(UUID i);
}
