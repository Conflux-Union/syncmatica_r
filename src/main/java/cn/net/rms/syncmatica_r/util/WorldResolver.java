package cn.net.rms.syncmatica_r.util;

import cn.net.rms.syncmatica_r.ServerPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
//#if MC >= 12001
//$$ import net.minecraft.registry.RegistryKeys;
//#else
import net.minecraft.util.registry.Registry;
//#endif
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

/** Turns a stored dimension identifier back into a loaded world. */
public final class WorldResolver {

    private static final String END_DIMENSION_ID = "minecraft:the_end";

    private WorldResolver() {
    }

    /** @return the world for that dimension, or null when the server has none */
    public static ServerWorld resolve(final MinecraftServer server, final String dimensionId) {
        if (server == null || dimensionId == null) {
            return null;
        }
        if (ServerPosition.OVERWORLD_DIMENSION_ID.equals(dimensionId)) {
            return server.getOverworld();
        }
        if (ServerPosition.NETHER_DIMENSION_ID.equals(dimensionId)) {
            return server.getWorld(World.NETHER);
        }
        if (END_DIMENSION_ID.equals(dimensionId)) {
            return server.getWorld(World.END);
        }
        final RegistryKey<World> key = RegistryKey.of(
//#if MC >= 12001
//$$                 RegistryKeys.WORLD,
//#else
                Registry.WORLD_KEY,
//#endif
                IdentifierUtil.require(dimensionId));
        return server.getWorld(key);
    }
}
