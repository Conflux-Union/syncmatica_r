package cn.net.rms.syncmatica_r.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SyncmaticaCommandPermissionContractTest {
    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void privilegedCommandsKeepTheirPermissionsWithoutBlockingTheRoot() throws IOException {
        final String commandSource = read("src/main/java/cn/net/rms/syncmatica_r/command/SyncmaticaCommand.java");

        assertTrue(
                commandSource.contains("me.lucko.fabric.api.permissions.v0.Permissions"),
                "SyncmaticaCommand must use Fabric Permissions API");
        assertTrue(
                commandSource.contains("syncmatica_r.command"),
                "SyncmaticaCommand must expose the syncmatica_r.command permission node");
        assertTrue(
                commandSource.matches("(?s).*COMMAND_PERMISSION_LEVEL\\s*=\\s*2.*"),
                "SyncmaticaCommand must keep permission level 2 as the fallback");
        assertFalse(
                commandSource.contains(".requires(Permissions.require(COMMAND_PERMISSION, COMMAND_PERMISSION_LEVEL))"),
                "the command root must remain visible to placement owners");
        assertTrue(
                commandSource.contains(".requires(SyncmaticaCommand::hasCommandPermission)"),
                "privileged project commands must retain the general command permission");
        assertTrue(
                commandSource.contains(".requires(SyncmaticaCommand::hasManagePermission)"),
                "default stocking-area commands must require elevated management permission");
    }

    @Test
    void loadSubcommandUsesDedicatedPermissionNode() throws IOException {
        final String commandSource = read("src/main/java/cn/net/rms/syncmatica_r/command/SyncmaticaCommand.java");

        assertTrue(
                commandSource.contains("\"syncmatica_r.command.load\""),
                "load subcommand must expose the syncmatica_r.command.load permission node");
        assertTrue(
                commandSource.contains(".requires(SyncmaticaCommand::hasLoadPermission)"),
                "load subcommand must use its composed permission check");
        assertTrue(
                commandSource.contains("hasCommandPermission(source)")
                        && commandSource.contains(
                                "Permissions.check(source, LOAD_PERMISSION, COMMAND_PERMISSION_LEVEL)"),
                "load subcommand must retain both the general and dedicated permission checks");
    }

    @Test
    void stockingAreaCommandUsesOwnerAwareAccessPolicy() throws IOException {
        final String commandSource = read("src/main/java/cn/net/rms/syncmatica_r/command/SyncmaticaCommand.java");

        assertTrue(
                commandSource.contains("PlacementAccessPolicy.canManageStockingArea"),
                "stocking-area commands must use the shared owner-aware access policy");
        assertTrue(
                commandSource.contains("materialService.isOwnerStockingAreaManagementEnabled()"),
                "stocking-area commands must honor the materials owner-management setting");
    }

    @Test
    void buildIncludesFabricPermissionsApi() throws IOException {
        final String gradleSource = read("common.gradle");

        assertTrue(
                gradleSource.contains(
                        "include(autoImpl(\"me.lucko:fabric-permissions-api:${fabricPermissionsApiVersion}\"))"),
                "common.gradle must include fabric-permissions-api");
    }

    @Test
    void buildUsesMinecraftCompatibleFabricPermissionsApi() throws IOException {
        final String gradleSource = read("common.gradle");

        assertTrue(
                gradleSource.contains("def fabricPermissionsApiVersion = '0.3.1'"),
                "Minecraft 1.21.1 and older must not embed fabric-permissions-api 0.3.2+");
        assertTrue(
                gradleSource.contains("mcVersionNumber >= 12103"),
                "fabric-permissions-api 0.3.3 must be guarded to Minecraft 1.21.3+");
        assertTrue(
                gradleSource.contains("mcVersionNumber >= 12102"),
                "fabric-permissions-api 0.3.2 must be guarded to Minecraft 1.21.2+");
    }

    @Test
    void modMetadataDeclaresFabricPermissionsApi() throws IOException {
        final String modJson = read("src/main/resources/fabric.mod.json");

        assertTrue(
                modJson.contains("\"fabric-permissions-api-v0\""),
                "fabric.mod.json must declare fabric-permissions-api-v0");
    }

    private String read(final String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
