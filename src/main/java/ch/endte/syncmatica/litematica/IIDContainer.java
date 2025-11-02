package ch.endte.syncmatica.litematica;

import java.util.UUID;

public interface IIDContainer {
    UUID getServerId();

    void setServerId(UUID i);
}
