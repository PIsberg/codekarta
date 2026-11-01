package se.deversity.codekarta.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.input.parser.SourceTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a directory walk picks up when {@code --input} is a whole repository rather than a
 * source root: production sources yes; tests, build output, VCS metadata and vendored
 * dependencies no.
 */
class SourceTreeTest {

    /** A two-module Maven/Gradle checkout with every kind of directory that should be pruned. */
    private static Path repo(Path root) throws IOException {
        write(root, "pom.xml", "<project/>");
        write(root, "src/main/java/app/App.java", "package app; public class App {}");
        write(root, "src/test/java/app/AppTest.java", "package app; class AppTest {}");
        write(root, "src/integrationTest/java/app/AppIT.java", "package app; class AppIT {}");
        write(root, "src/testFixtures/java/app/Fixture.java", "package app; class Fixture {}");
        write(root, "src/it/simple/src/main/java/it/Probe.java", "package it; class Probe {}");
        write(root, "target/generated-sources/gen/Generated.java", "package gen; class Generated {}");
        write(root, ".git/objects/Stray.java", "class Stray {}");
        write(root, "node_modules/pkg/Vendored.java", "class Vendored {}");

        write(root, "lib/build.gradle.kts", "");
        write(root, "lib/src/main/java/lib/Lib.java", "package lib; public class Lib {}");
        write(root, "lib/build/generated/lib/Stub.java", "package lib; class Stub {}");
        // A package that happens to be called "build" is source, not build output: no build
        // file sits beside it.
        write(root, "lib/src/main/java/lib/build/Builder.java", "package lib.build; class Builder {}");
        return root;
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * The files as root-relative names, after checking they arrive in {@link Path} order.
     *
     * <p>Compared as a set because {@code Path} order is platform-defined: case-insensitive on
     * Windows, so {@code build/} sorts before {@code Lib.java} there and after it on Linux. The
     * walker's contract is "sorted by Path", the same order {@code Files.walk().sorted()} gave
     * before it, so diagrams stay byte-identical on each platform.
     */
    private static Set<String> names(Path root, List<Path> files) {
        assertEquals(files.stream().sorted().toList(), files, "files must arrive in Path order");
        return files.stream()
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                .collect(Collectors.toSet());
    }

    @Test
    void repositoryRootYieldsProductionSourcesOnly(@TempDir Path dir) throws IOException {
        Path root = repo(dir);

        List<Path> files = SourceTree.javaFiles(root, false);

        assertEquals(Set.of(
                "lib/src/main/java/lib/Lib.java",
                "lib/src/main/java/lib/build/Builder.java",
                "src/main/java/app/App.java"), names(root, files));
    }

    @Test
    void includeTestsAddsTestSourceSetsButStillPrunesBuildOutput(@TempDir Path dir) throws IOException {
        Path root = repo(dir);

        List<Path> files = SourceTree.javaFiles(root, true);

        assertEquals(Set.of(
                "lib/src/main/java/lib/Lib.java",
                "lib/src/main/java/lib/build/Builder.java",
                "src/integrationTest/java/app/AppIT.java",
                "src/it/simple/src/main/java/it/Probe.java",
                "src/main/java/app/App.java",
                "src/test/java/app/AppTest.java",
                "src/testFixtures/java/app/Fixture.java"), names(root, files));
    }

    @Test
    void pointingAtATestRootDirectlyStillReadsIt(@TempDir Path dir) throws IOException {
        Path root = repo(dir);
        Path testRoot = root.resolve("src/test/java");

        List<Path> files = SourceTree.javaFiles(testRoot, false);

        assertEquals(Set.of("app/AppTest.java"), names(testRoot, files));
    }

    @Test
    void aMissingRootIsAnIOExceptionForTheCallerToLog(@TempDir Path dir) {
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> SourceTree.javaFiles(dir.resolve("nope"), false));
    }
}
