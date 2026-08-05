package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class MixinRegistrationContractTest {
    private static final Pattern ENTRY = Pattern.compile("\"(Mixin[A-Za-z0-9_$]+)\"");

    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void minecraftMixinsAreAllRegistered() throws IOException {
        assertRegistrationMatchesSources(
                "src/main/resources/syncmatica_r.mixin.json",
                "src/main/java/cn/net/rms/syncmatica_r/mixin");
    }

    @Test
    void litematicaMixinsAreAllRegistered() throws IOException {
        assertRegistrationMatchesSources(
                "src/main/resources/syncmatica_r.litematica_mixin.json",
                "src/main/java/cn/net/rms/syncmatica_r/litematica_mixin");
    }

    /**
     * Per-version builds may only rewrite the mixin compatibility level. Adding or dropping
     * individual mixin entries there silently disables features on single Minecraft targets
     * while every other target keeps working, which is undetectable from the source tree.
     */
    @Test
    void buildDoesNotRewriteIndividualMixinEntries() throws IOException {
        final String gradleSource = read("common.gradle");
        final Set<String> registered = new TreeSet<>();
        registered.addAll(registeredMixins("src/main/resources/syncmatica_r.mixin.json"));
        registered.addAll(registeredMixins("src/main/resources/syncmatica_r.litematica_mixin.json"));

        for (final String mixinClass : registered) {
            assertFalse(
                    gradleSource.contains(mixinClass),
                    "common.gradle must not reference the individual mixin " + mixinClass);
        }
    }

    private void assertRegistrationMatchesSources(final String configPath, final String sourceDir)
            throws IOException {
        final Set<String> declared = new TreeSet<>();
        try (Stream<Path> sources = Files.list(projectRoot.resolve(sourceDir))) {
            sources.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("Mixin") && name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .forEach(declared::add);
        }

        assertEquals(
                declared,
                registeredMixins(configPath),
                configPath + " must register exactly the mixin classes present in " + sourceDir);
    }

    private Set<String> registeredMixins(final String configPath) throws IOException {
        final String config = read(configPath);
        final Set<String> registered = new TreeSet<>();
        final Matcher matcher = ENTRY.matcher(config);
        while (matcher.find()) {
            registered.add(matcher.group(1));
        }
        assertFalse(registered.isEmpty(), configPath + " must register at least one mixin");
        return registered;
    }

    private String read(final String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
