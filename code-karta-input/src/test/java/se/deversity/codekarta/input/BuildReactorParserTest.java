package se.deversity.codekarta.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.BuildReactorParser;
import se.deversity.vibetags.annotations.AIParallelTests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every fixture here is written into a {@code @TempDir} rather than pointing at a real
 * repository: the parser's whole job is reading build files, so a test that read the
 * project's own would pass or fail on unrelated build changes.
 */
@AIParallelTests
class BuildReactorParserTest {

    private final BuildReactorParser parser = new BuildReactorParser();

    // ------------------------------------------------------------------ Maven

    @Test
    void readsMavenModulesAsModuleNodes(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha", "beta");

        Graph graph = parser.parse(root);

        assertEquals(Set.of("parent", "alpha", "beta"), ids(graph));
        assertTrue(graph.getNodes().stream().allMatch(n -> "MODULE".equals(n.getType())),
                "reactor members must be MODULE nodes");
    }

    @Test
    void aggregatorDeclaresItsModulesWithHasEdges(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha", "beta");

        Graph graph = parser.parse(root);

        assertTrue(hasEdge(graph, "parent", "alpha", "HAS"));
        assertTrue(hasEdge(graph, "parent", "beta", "HAS"));
    }

    @Test
    void intraReactorDependencyBecomesRequires(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha", "beta");
        writeModulePom(root.resolve("beta"), "beta", "alpha");

        Graph graph = parser.parse(root);

        assertTrue(hasEdge(graph, "beta", "alpha", "REQUIRES"),
                "beta depends on its sibling alpha");
    }

    @Test
    void dependenciesOutsideTheReactorAreDropped(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha", "beta");
        writeModulePom(root.resolve("beta"), "beta", "junit-jupiter");

        Graph graph = parser.parse(root);

        assertFalse(ids(graph).contains("junit-jupiter"),
                "third-party dependencies answer no cross-module question");
    }

    @Test
    void nestedAggregatorsAreFollowed(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "group");
        Path group = root.resolve("group");
        Files.writeString(group.resolve("pom.xml"), aggregatorPom("group", "leaf"));
        Files.createDirectories(group.resolve("leaf"));
        writeModulePom(group.resolve("leaf"), "leaf");

        Graph graph = parser.parse(root);

        assertTrue(ids(graph).contains("leaf"), "a module of a module is still in the reactor");
        assertTrue(hasEdge(graph, "group", "leaf", "HAS"));
    }

    @Test
    void aSinglePomWithNoModulesIsNotAReactor(@TempDir Path root) throws IOException {
        writeModulePom(root, "solo");

        Graph graph = parser.parse(root);

        assertTrue(graph.getNodes().isEmpty(), "one box is not a module diagram");
    }

    @Test
    void moduleEntriesEscapingTheRootAreNotFollowed(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("repo"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        writeModulePom(outside, "outside-module");
        Files.writeString(repo.resolve("pom.xml"), aggregatorPom("parent", "../outside"));

        Graph graph = parser.parse(repo);

        assertFalse(ids(graph).contains("outside-module"),
                "a <module> pointing out of the tree must not be followed");
    }

    @Test
    void anExternalEntityInAPomIsNotResolved(@TempDir Path root) throws IOException {
        Path secret = root.resolve("secret.txt");
        Files.writeString(secret, "top-secret");
        Files.writeString(root.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n"
              + "<!DOCTYPE project [ <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\"> ]>\n"
              + "<project><artifactId>&xxe;</artifactId>\n"
              + "  <modules><module>alpha</module></modules>\n"
              + "</project>\n");
        Files.createDirectories(root.resolve("alpha"));
        writeModulePom(root.resolve("alpha"), "alpha");

        Graph graph = parser.parse(root);

        assertFalse(ids(graph).contains("top-secret"), "a DOCTYPE must not turn into a file read");
    }

    /**
     * A relative root must work as well as an absolute one. {@code --input .} normalises to
     * the bare path "pom.xml", whose parent directory is null, and every {@code <module>}
     * then resolves against nothing. Found against a real repo, where the Gradle fallback
     * produced a plausible-looking diagram and hid it.
     */
    @Test
    void aRelativeRootResolvesItsModulesTheSameWayAnAbsoluteOneDoes(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha", "beta");
        Path relative = relativeTo(Path.of("").toAbsolutePath(), root);

        Graph graph = parser.parse(relative);

        assertEquals(Set.of("parent", "alpha", "beta"), ids(graph));
    }

    /** @return {@code target} expressed relative to {@code from}, or {@code target} itself when they share no root */
    private Path relativeTo(Path from, Path target) {
        try {
            return from.relativize(target);
        } catch (IllegalArgumentException differentRoots) {
            return target;
        }
    }

    // ------------------------------------------------------------------ Gradle

    @Test
    void readsGradleIncludesWhenThereIsNoPom(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"demo\"\n"
              + "include(\"alpha\")\n"
              + "include(\"beta\")\n");

        Graph graph = parser.parse(root);

        assertEquals(Set.of("demo", "alpha", "beta"), ids(graph));
        assertTrue(hasEdge(graph, "demo", "alpha", "HAS"));
    }

    @Test
    void readsMultipleProjectsFromOneIncludeCall(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"demo\"\n"
              + "include(\"alpha\", \"beta\", \"gamma\")\n");

        Graph graph = parser.parse(root);

        assertTrue(ids(graph).containsAll(Set.of("alpha", "beta", "gamma")));
    }

    @Test
    void readsGroovyCommandSyntaxIncludes(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle"),
                "rootProject.name = 'demo'\n"
              + "include ':alpha', ':beta'\n");

        Graph graph = parser.parse(root);

        assertTrue(ids(graph).containsAll(Set.of("alpha", "beta")));
    }

    @Test
    void projectDependenciesBetweenGradleModulesBecomeRequires(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"demo\"\ninclude(\"alpha\", \"beta\")\n");
        Path beta = Files.createDirectories(root.resolve("beta"));
        Files.createDirectories(root.resolve("alpha"));
        Files.writeString(beta.resolve("build.gradle.kts"),
                "dependencies {\n    implementation(project(\":alpha\"))\n}\n");

        Graph graph = parser.parse(root);

        assertTrue(hasEdge(graph, "beta", "alpha", "REQUIRES"));
    }

    @Test
    void includeBuildIsNotAnInclude(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"demo\"\n"
              + "includeBuild(\"../other-build\")\n"
              + "include(\"alpha\")\n");

        Graph graph = parser.parse(root);

        assertEquals(Set.of("demo", "alpha"), ids(graph),
                "a composite build is not a module of this reactor");
    }

    @Test
    void mavenWinsWhenBothBuildSystemsAreDeclared(@TempDir Path root) throws IOException {
        mavenReactor(root, "parent", "alpha");
        Files.writeString(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"gradle-name\"\ninclude(\"gradle-only\")\n");

        Graph graph = parser.parse(root);

        assertTrue(ids(graph).contains("parent"));
        assertFalse(ids(graph).contains("gradle-only"));
    }

    @Test
    void aDirectoryWithNoBuildFilesYieldsAnEmptyGraph(@TempDir Path root) {
        Graph graph = parser.parse(root);

        assertTrue(graph.getNodes().isEmpty());
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    void anUnreadableInputYieldsAnEmptyGraphRatherThanAnException(@TempDir Path root) {
        Graph graph = parser.parse(root.resolve("does-not-exist"));

        assertTrue(graph.getNodes().isEmpty());
        assertTrue(parser.parse(null).getNodes().isEmpty());
    }

    // ------------------------------------------------------------------ fixtures

    private void mavenReactor(Path root, String parentArtifactId, String... modules) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), aggregatorPom(parentArtifactId, modules));
        for (String module : modules) {
            Path dir = Files.createDirectories(root.resolve(module));
            if (!Files.exists(dir.resolve("pom.xml"))) {
                writeModulePom(dir, module);
            }
        }
    }

    private String aggregatorPom(String artifactId, String... modules) {
        StringBuilder sb = new StringBuilder("<project><artifactId>").append(artifactId)
                .append("</artifactId>\n  <modules>\n");
        for (String module : modules) {
            sb.append("    <module>").append(module).append("</module>\n");
        }
        return sb.append("  </modules>\n</project>\n").toString();
    }

    private void writeModulePom(Path dir, String artifactId, String... dependencies) throws IOException {
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder("<project><artifactId>").append(artifactId)
                .append("</artifactId>\n  <dependencies>\n");
        for (String dependency : dependencies) {
            sb.append("    <dependency><groupId>x</groupId><artifactId>")
              .append(dependency).append("</artifactId></dependency>\n");
        }
        Files.writeString(dir.resolve("pom.xml"), sb.append("  </dependencies>\n</project>\n").toString());
    }

    private Set<String> ids(Graph graph) {
        return graph.getNodes().stream().map(n -> n.getId()).collect(Collectors.toSet());
    }

    private boolean hasEdge(Graph graph, String source, String target, String type) {
        return graph.getEdges().stream().anyMatch(e -> matches(e, source, target, type));
    }

    private boolean matches(Edge edge, String source, String target, String type) {
        return source.equals(edge.getSourceId())
                && target.equals(edge.getTargetId())
                && type.equals(edge.getType());
    }
}
