package se.deversity.codekarta.input.parser;

import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds the {@code .java} files under a directory, the way a person pointing the tool at a
 * repository means it: the code that ships, not everything that happens to end in {@code .java}.
 *
 * <p>Every directory walk in the input tier goes through here, so a repository root works as
 * {@code --input} for each diagram type. Without it, pointing the CLI at a checkout drew tests,
 * test fixtures and generated code beside the production classes: run over this repository's own
 * root, 72 boxes of which roughly half were tests, fixtures, or {@code HelpMojo} from
 * {@code target/generated-sources}.
 *
 * <p>Pruned below the root (the root itself is always read, so pointing straight at
 * {@code src/test/java} still works):
 * <ul>
 *   <li>hidden directories ({@code .git}, {@code .gradle}, {@code .idea}, ...)</li>
 *   <li>{@code node_modules}</li>
 *   <li>{@code target}, {@code build} and {@code out}, but only beside a build file
 *       ({@code pom.xml}, {@code build.gradle[.kts]}, {@code build.xml}). A Java package that
 *       happens to be called {@code build} has no build file next to it and is kept.</li>
 *   <li>unless {@code includeTests}: test source sets, meaning a directory directly under
 *       {@code src} whose name contains {@code test} ({@code test}, {@code integrationTest},
 *       {@code testFixtures}, ...) or is {@code it}</li>
 * </ul>
 */
@AIContext(
    focus = "The one definition of which files under a directory count as source. Parsers call javaFiles() instead of Files.walk so that a repository root, a module, and a source root all behave the same for every diagram type.",
    avoids = "Pruning the root itself, or pruning by name alone without the build-file check: 'build' and 'target' are legal package names, and a user who points --input at src/test/java asked for tests.")
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public final class SourceTree {

    private static final Logger log = Logger.getLogger(SourceTree.class.getName());

    private static final Set<String> BUILD_FILES =
            Set.of("pom.xml", "build.gradle", "build.gradle.kts", "build.xml");

    private static final Set<String> BUILD_OUTPUT_DIRS = Set.of("target", "build", "out");

    private SourceTree() {
    }

    /**
     * Every {@code .java} file under {@code root} that is not pruned, sorted.
     *
     * <p>Sorted because walk order is filesystem-defined (NTFS returns name order, ext4 hash
     * order) and file order reaches the rendered SVG, which consumers commit.
     *
     * <p>A directory that cannot be read is logged and skipped rather than ending the walk: on a
     * real checkout one unreadable directory should not cost the rest of the diagram.
     *
     * @param includeTests {@code true} to read test source sets as well
     * @throws IOException if {@code root} itself cannot be walked, for the caller to log
     */
    public static List<Path> javaFiles(Path root, boolean includeTests) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && isPruned(dir, includeTests)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.toString().endsWith(".java")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                if (file.equals(root)) {
                    throw e;
                }
                log.warning(() -> "Skipping unreadable " + file + ": " + e.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(null);
        return files;
    }

    /** True when nothing under {@code dir} should be read. Never asked about the walk root. */
    static boolean isPruned(Path dir, boolean includeTests) {
        Path fileName = dir.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (name.startsWith(".") || "node_modules".equals(name)) {
            return true;
        }
        Path parent = dir.getParent();
        if (parent == null) {
            return false;
        }
        if (BUILD_OUTPUT_DIRS.contains(name) && hasBuildFile(parent)) {
            return true;
        }
        return !includeTests && isTestSourceSet(parent, name);
    }

    private static boolean hasBuildFile(Path dir) {
        for (String buildFile : BUILD_FILES) {
            if (Files.isRegularFile(dir.resolve(buildFile))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestSourceSet(Path parent, String name) {
        Path parentName = parent.getFileName();
        if (parentName == null || !"src".equals(parentName.toString())) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).contains("test") || "it".equals(name);
    }
}
