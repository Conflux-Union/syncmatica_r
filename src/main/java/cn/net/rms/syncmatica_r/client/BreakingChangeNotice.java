package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
//#if MC >= 260100
//$$ import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//$$ import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//#elseif MC >= 12001
//$$ import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
//$$ import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//#else
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
//#endif
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
//#if MC < 12001
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
//#endif
import net.minecraft.util.Formatting;

import java.net.URI;

public final class BreakingChangeNotice {
    // Last release that introduced breaking changes. Bump only when a new
    // breaking change lands, together with matching docs/BREAKING_CHANGES_<version>*.md files.
    static final String BREAKING_CHANGE_VERSION = "0.4.0";
    private static final String DOCUMENTATION_BASE_URL =
            "https://github.com/Conflux-Union/syncmatica_r/blob/for-rms/docs/BREAKING_CHANGES_";
    private static final String DISMISS_COMMAND = "syncmatica_r_dismiss_breaking_notice";
    private static final ClientNoticePreferences PREFERENCES = new ClientNoticePreferences();

    private BreakingChangeNotice() {
    }

    public static void initialize() {
        PREFERENCES.load();
        registerDismissCommand();
    }

    public static void showIfNeeded(final MinecraftClient client) {
        final String version = BREAKING_CHANGE_VERSION;
        if (client == null || client.player == null
                || PREFERENCES.isDismissed(noticeIdForVersion(version))) {
            return;
        }
        sendChatMessage(client, createMessage(version));
    }

    private static MutableText createMessage(final String version) {
        final MutableText message = translatable("syncmatica_r.breaking_change.notice", version)
                .formatted(Formatting.YELLOW);
        final MutableText chineseDocumentation = documentationButton(
                "[中文]",
                documentationUrlForVersion(version, true)
        );
        final MutableText englishDocumentation = documentationButton(
                "[English]",
                documentationUrlForVersion(version, false)
        );
        final MutableText dismiss = translatable("syncmatica_r.breaking_change.dismiss")
                .styled(style -> style
                        .withColor(Formatting.YELLOW)
                        .withUnderline(true)
                        .withClickEvent(createRunCommandEvent()));
        return message
                .append(literal(" "))
                .append(chineseDocumentation)
                .append(literal(" "))
                .append(englishDocumentation)
                .append(literal(" "))
                .append(dismiss);
    }

    static String noticeIdForVersion(final String version) {
        return Syncmatica.MOD_ID + "-" + version + "-breaking-changes";
    }

    static String documentationUrlForVersion(final String version, final boolean chinese) {
        final String fileVersion = version.replaceAll("[^A-Za-z0-9._-]", "_");
        return DOCUMENTATION_BASE_URL + fileVersion + (chinese ? "_CN.md" : ".md");
    }

    private static MutableText documentationButton(final String label, final String documentationUrl) {
        return literal(label).styled(style -> style
                .withColor(Formatting.YELLOW)
                .withUnderline(true)
                .withClickEvent(createOpenUrlEvent(documentationUrl)));
    }

    private static ClickEvent createOpenUrlEvent(final String documentationUrl) {
//#if MC >= 12106
//$$         return new ClickEvent.OpenUrl(URI.create(documentationUrl));
//#else
        return new ClickEvent(ClickEvent.Action.OPEN_URL, documentationUrl);
//#endif
    }

    private static ClickEvent createRunCommandEvent() {
//#if MC >= 12106
//$$         return new ClickEvent.RunCommand("/" + DISMISS_COMMAND);
//#else
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + DISMISS_COMMAND);
//#endif
    }

    private static MutableText literal(final String value) {
//#if MC >= 12001
//$$         return Text.literal(value);
//#else
        return new LiteralText(value);
//#endif
    }

    private static MutableText translatable(final String key, final Object... arguments) {
//#if MC >= 12001
//$$         return Text.translatable(key, arguments);
//#else
        return new TranslatableText(key, arguments);
//#endif
    }

    private static void registerDismissCommand() {
//#if MC >= 12001
//$$         ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
//$$                 dispatcher.register(
//#if MC >= 260100
//$$                         ClientCommands.literal(DISMISS_COMMAND)
//#else
//$$                         ClientCommandManager.literal(DISMISS_COMMAND)
//#endif
//$$                                 .executes(context -> dismiss())
//$$                 )
//$$         );
//#else
        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal(DISMISS_COMMAND).executes(context -> dismiss())
        );
//#endif
    }

    private static int dismiss() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (PREFERENCES.dismiss(noticeIdForVersion(BREAKING_CHANGE_VERSION))) {
            sendChatMessage(
                    client,
                    translatable("syncmatica_r.breaking_change.dismissed").formatted(Formatting.YELLOW)
            );
        } else {
            sendChatMessage(
                    client,
                    translatable("syncmatica_r.breaking_change.dismiss_failed").formatted(Formatting.RED)
            );
        }
        return 1;
    }

    private static void sendChatMessage(final MinecraftClient client, final Text message) {
        if (client == null || client.player == null) {
            return;
        }
//#if MC >= 260100
//$$         client.player.sendSystemMessage(message);
//#else
        client.inGameHud.getChatHud().addMessage(message);
//#endif
    }
}
