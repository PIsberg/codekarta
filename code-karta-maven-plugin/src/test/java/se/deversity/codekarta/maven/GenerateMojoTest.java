package se.deversity.codekarta.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.cli.RunOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerateMojoTest {

    private static GenerateMojo mojo(Path input, Path output, String packaging) {
        MavenProject project = new MavenProject();
        project.setPackaging(packaging);
        GenerateMojo mojo = new GenerateMojo();
        mojo.setProject(project);
        mojo.setInput(input.toFile());
        mojo.setOutputDirectory(output.toFile());
        mojo.setFormat(RunOptions.FORMAT_SVG);
        mojo.setLayout("simple");
        mojo.setMaxMembers(6);
        return mojo;
    }

    private static void writeSources(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Animal.java"), "public class Animal { private String name; }");
        Files.writeString(dir.resolve("Dog.java"), "public class Dog extends Animal {}");
    }

    @Test
    void generatesADiagramWithNoConfigurationBeyondTheDefaults(@TempDir Path in, @TempDir Path out)
            throws Exception {
        writeSources(in);

        mojo(in, out, "jar").execute();

        Path svg = out.resolve("class-diagram.svg");
        assertTrue(Files.exists(svg), "the zero-config case must produce a diagram");
        assertTrue(Files.readString(svg).contains("<svg "));
    }

    @Test
    void generatesEveryConfiguredDiagram(@TempDir Path in, @TempDir Path out) throws Exception {
        writeSources(in);
        GenerateMojo mojo = mojo(in, out, "jar");

        Diagram svg = new Diagram();
        svg.setOutputName("classes.svg");
        Diagram json = new Diagram();
        json.setFormat(RunOptions.FORMAT_JSON);
        json.setOutputName("graph.json");
        mojo.setDiagrams(List.of(svg, json));

        mojo.execute();

        assertTrue(Files.exists(out.resolve("classes.svg")));
        assertTrue(Files.readString(out.resolve("graph.json")).contains("nodes"));
    }

    @Test
    void skipDoesNothingAtAll(@TempDir Path in, @TempDir Path out) throws Exception {
        writeSources(in);
        GenerateMojo mojo = mojo(in, out, "jar");
        mojo.setSkip(true);

        mojo.execute();

        assertFalse(Files.exists(out.resolve("class-diagram.svg")));
    }

    @Test
    void aPomPackagingModuleIsSkippedRatherThanFailed(@TempDir Path in, @TempDir Path out)
            throws Exception {
        // An aggregator has no sources. This runs at the parent level in every reactor, so
        // failing or writing an empty directory there would make the plugin unusable in a parent.
        GenerateMojo mojo = mojo(in, out, "pom");

        mojo.execute();

        assertFalse(Files.exists(out.resolve("class-diagram.svg")));
    }

    @Test
    void aPomModuleStillRunsWhenAskedForTheReactorDiagram(@TempDir Path in, @TempDir Path out)
            throws Exception {
        // BuildReactorParser resolves each declared module to a real pom, and needs at least two
        // in the reactor before it draws anything. A pom that merely lists module names is not a
        // reactor as far as it is concerned, and it says so rather than drawing one box.
        Files.createDirectories(in.resolve("a"));
        Files.createDirectories(in.resolve("b"));
        Files.writeString(in.resolve("pom.xml"),
                "<project><artifactId>root</artifactId><modules>"
                        + "<module>a</module><module>b</module></modules></project>");
        Files.writeString(in.resolve("a").resolve("pom.xml"),
                "<project><artifactId>a</artifactId></project>");
        Files.writeString(in.resolve("b").resolve("pom.xml"),
                "<project><artifactId>b</artifactId>"
                        + "<dependencies><dependency><artifactId>a</artifactId></dependency>"
                        + "</dependencies></project>");
        GenerateMojo mojo = mojo(in, out, "pom");
        mojo.setModulesOnly(true);

        mojo.execute();

        assertTrue(Files.exists(out.resolve("modules-diagram.svg")),
                "modulesOnly is the reason to run on an aggregator");
    }

    @Test
    void missingSourceDirectoryIsNotAFailureByDefault(@TempDir Path out) {
        // Every reactor has modules without src/main/java. Failing on them would mean the plugin
        // cannot be declared once in a parent pom, which is how plugins are actually adopted.
        GenerateMojo mojo = mojo(Path.of("does", "not", "exist"), out, "jar");

        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void failOnEmptyTurnsAnEmptyResultIntoABuildFailure(@TempDir Path out) {
        GenerateMojo mojo = mojo(Path.of("does", "not", "exist"), out, "jar");
        mojo.setFailOnEmpty(true);

        MojoExecutionException thrown = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(thrown.getMessage().contains("no diagram"),
                "the message must say what happened: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("parsing failed"),
                "and name the ambiguity, because parsers never throw: " + thrown.getMessage());
    }

    @Test
    void splitPackagesWritesOneDiagramPerPackage(@TempDir Path in, @TempDir Path out)
            throws Exception {
        writeSources(in.resolve("com").resolve("one"));
        writeSources(in.resolve("com").resolve("two"));
        GenerateMojo mojo = mojo(in, out, "jar");
        mojo.setSplitPackages(true);

        mojo.execute();

        try (var walk = Files.walk(out)) {
            long svgs = walk.filter(p -> p.toString().endsWith(".svg")).count();
            assertTrue(svgs >= 2, "expected a diagram per package, found " + svgs);
        }
    }

    // --- configuration mapping ---

    @Test
    void diagramValuesOverrideTheGoalLevelDefaults(@TempDir Path in, @TempDir Path out) {
        GenerateMojo mojo = mojo(in, out, "jar");

        Diagram diagram = new Diagram();
        diagram.setLayout("elk");
        diagram.setFormat(RunOptions.FORMAT_JSON);
        diagram.setMaxMembers(99);
        diagram.setOutputName("mine.json");

        RunOptions options = mojo.toRunOptions(diagram);

        assertEquals("elk", options.layout());
        assertEquals(RunOptions.FORMAT_JSON, options.format());
        assertEquals(99, options.maxMembers());
        assertEquals("mine.json", options.outputName());
    }

    @Test
    void unsetDiagramValuesFallBackToTheGoalLevelDefaults(@TempDir Path in, @TempDir Path out) {
        GenerateMojo mojo = mojo(in, out, "jar");
        mojo.setLayout("elk");
        mojo.setMaxMembers(3);
        mojo.setSequenceOnly(true);

        RunOptions options = mojo.toRunOptions(new Diagram());

        assertEquals("elk", options.layout());
        assertEquals(3, options.maxMembers());
        assertTrue(options.sequenceOnly());
        assertEquals(RunOptions.FORMAT_SVG, options.format());
    }

    @Test
    void excludesFromBothLevelsAreCombined(@TempDir Path in, @TempDir Path out) {
        GenerateMojo mojo = mojo(in, out, "jar");
        mojo.setExcludes(List.of("*Test"));
        Diagram diagram = new Diagram();
        diagram.setExcludes(List.of("*Fixture"));

        RunOptions options = mojo.toRunOptions(diagram);

        assertTrue(options.customExcludes().contains("*Test"), "goal-level patterns must survive");
        assertTrue(options.customExcludes().contains("*Fixture"), "so must per-diagram ones");
    }

    @Test
    void unsetMaxDepthMeansUnbounded(@TempDir Path in, @TempDir Path out) {
        RunOptions options = mojo(in, out, "jar").toRunOptions(new Diagram());

        assertEquals(Integer.MAX_VALUE, options.maxDepth());
    }

    @Test
    void booleanFlagsAreOredRatherThanOverridden(@TempDir Path in, @TempDir Path out) {
        // A goal-level stateMachine=true is a statement about every diagram in the execution. A
        // per-diagram false cannot silently cancel it, because the bean cannot tell an unset
        // boolean from an explicit false.
        GenerateMojo mojo = mojo(in, out, "jar");
        mojo.setStateMachine(true);

        assertTrue(mojo.toRunOptions(new Diagram()).stateMachine());
    }

    @Test
    void diagramToStringNamesItsSettings() {
        Diagram diagram = new Diagram();
        diagram.setInput("src/main/java");
        diagram.setFormat("json");

        assertTrue(diagram.toString().contains("src/main/java"));
        assertTrue(diagram.toString().contains("json"));
    }
}
