package se.deversity.codekarta.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.core.model.Graph;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KartaCliTest {

    // --- run() end-to-end tests ---

    @Test
    void generatesModuleDiagram(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Path moduleInfo = inputDir.resolve("module-info.java");
        Files.writeString(moduleInfo, "module com.test { requires java.base; }");

        Path result = KartaCli.run(moduleInfo, outputDir);

        assertEquals("module-diagram.svg", result.getFileName().toString());
        assertTrue(Files.exists(result), "output file must exist");
        String svg = Files.readString(result);
        assertTrue(svg.contains("<svg "),   "output must be SVG");
        assertTrue(svg.endsWith("</svg>\n"), "SVG must be closed, and the file must end with a newline");
    }

    @Test
    void generatesClassDiagram(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"),  "public class Animal {}");
        Files.writeString(inputDir.resolve("Dog.java"), "public class Dog extends Animal {}");

        Path result = KartaCli.run(inputDir, outputDir);

        assertEquals("class-diagram.svg", result.getFileName().toString());
        assertTrue(Files.exists(result));
        assertTrue(Files.readString(result).contains("<svg "));
    }

    @Test
    void generatesSequenceDiagram(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Path javaFile = inputDir.resolve("OrderProcessor.java");
        Files.writeString(javaFile, """
                public class OrderProcessor {
                    void submit() { process(); }
                    void process() {}
                }
                """);

        Path result = KartaCli.run(javaFile, outputDir);

        assertEquals("orderprocessor-sequence-diagram.svg", result.getFileName().toString());
        assertTrue(Files.exists(result));
    }

    @Test
    void multiFileSequenceModeStitchesDirectory(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("Alpha.java"), "public class Alpha { void a() {} }");
        Files.writeString(inputDir.resolve("Beta.java"),  "public class Beta  { void b() {} }");

        Path result = KartaCli.run(inputDir, outputDir, true);

        assertEquals("sequence-diagram.svg", result.getFileName().toString(),
                "directory + sequence-only must produce sequence-diagram.svg");
        String svg = Files.readString(result);
        assertTrue(svg.contains("<svg "),  "output must be SVG");
        assertTrue(svg.contains("Alpha") || svg.contains("Beta"),
                "SVG must reference at least one parsed class");
    }

    @Test
    void sequenceOnlyFlagProducesValidSvg(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Path javaFile = inputDir.resolve("Service.java");
        Files.writeString(javaFile, """
                public class Service {
                    void handle() throws Exception { execute(); }
                    void execute() {}
                }
                """);

        Path result = KartaCli.run(javaFile, outputDir, true);

        assertEquals("service-sequence-diagram.svg", result.getFileName().toString());
        assertTrue(Files.exists(result));
        String svg = Files.readString(result);
        assertTrue(svg.contains("<svg "), "output must be SVG");
        // sequence-only uses CallSequenceParser — no EXCEPTION nodes should appear
        assertFalse(svg.contains("exception:"), "sequence-only must not emit exception-type nodes");
    }

    @Test
    void createsOutputDirectoryIfAbsent(@TempDir Path inputDir, @TempDir Path baseDir) throws Exception {
        Path moduleInfo = inputDir.resolve("module-info.java");
        Files.writeString(moduleInfo, "module com.test {}");
        Path newDir = baseDir.resolve("deep/nested/dir");
        assertFalse(Files.exists(newDir), "directory must not exist before run");

        KartaCli.run(moduleInfo, newDir);

        assertTrue(Files.isDirectory(newDir), "output directory must be created");
    }

    @Test
    void svgContainsXmlDeclarationAndRootElement(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("A.java"), "public class A {}");

        Path result = KartaCli.run(inputDir, outputDir);
        String svg = Files.readString(result);

        assertTrue(svg.startsWith("<?xml"), "must start with XML declaration");
        assertTrue(svg.contains("<svg "),   "must contain <svg> open tag");
        assertTrue(svg.endsWith("</svg>\n"), "must end with </svg> and a trailing newline");
    }

    @Test
    void svgContainsNodeLabel(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("MyService.java"), "public class MyService {}");
        Files.writeString(inputDir.resolve("MyRepo.java"),
                "public class MyRepo { private MyService svc; }");

        Path result = KartaCli.run(inputDir, outputDir);
        String svg = Files.readString(result);

        assertTrue(svg.contains("MyService"), "node label must appear in SVG");
    }

    @Test
    void elkLayoutProducesValidSvg(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("Dog.java"),    "public class Dog extends Animal {}");
        Files.writeString(inputDir.resolve("Animal.java"), "public class Animal {}");

        Path result = KartaCli.run(inputDir, outputDir, false, "elk");

        assertEquals("class-diagram.svg", result.getFileName().toString());
        String svg = Files.readString(result);
        assertTrue(svg.contains("<svg "), "ELK output must be SVG");
        assertTrue(svg.contains("Dog") || svg.contains("Animal"),
                "SVG must reference parsed class labels");
    }

    @Test
    void stateMachineModeGeneratesStateTransitionDiagram(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Path javaFile = inputDir.resolve("Workflow.java");
        Files.writeString(javaFile, """
                public class Workflow {
                    enum State { OPEN, CLOSED }
                    void configure() { transition(State.OPEN, State.CLOSED, "close"); }
                }
                """);

        Path result = KartaCli.run(javaFile, outputDir, false, "simple", true);

        assertEquals("workflow-state-machine-diagram.svg", result.getFileName().toString());
        String svg = Files.readString(result);
        assertTrue(svg.contains("OPEN"), "state node label must appear");
        assertTrue(svg.contains("close"), "transition label must appear");
    }

    @Test
    void stateMachineModeOnKartaCliItself(@TempDir Path outputDir) throws Exception {
        // KartaCli.java contains PipelineStage — this test is self-referential:
        // the tool generates a state-transition diagram of its own pipeline.
        Path kartaCliSrc = Path.of("src/main/java/se/deversity/codekarta/cli/KartaCli.java");
        assumeTrue(Files.exists(kartaCliSrc),
                "Skipping: KartaCli.java not found at " + kartaCliSrc.toAbsolutePath());

        Path result = KartaCli.run(kartaCliSrc, outputDir, false, "simple", true);

        assertEquals("kartacli-state-machine-diagram.svg", result.getFileName().toString());
        assertTrue(Files.exists(result));
        String svg = Files.readString(result);
        assertTrue(svg.contains("<svg "),      "output must be SVG");
        assertTrue(svg.contains("PARSING"),    "PARSING stage must appear as a state node");
        assertTrue(svg.contains("LAYOUT"),     "LAYOUT stage must appear as a state node");
        assertTrue(svg.contains("RENDERING"),  "RENDERING stage must appear as a state node");
        assertTrue(svg.contains("WRITING"),    "WRITING stage must appear as a state node");
        assertTrue(svg.contains("DONE"),       "DONE stage must appear as a state node");
    }

    // --- deriveOutputName unit tests ---

    @Test
    void deriveOutputNameForModuleInfo() {
        assertEquals("module-diagram.svg",
                KartaCli.deriveOutputName(Path.of("module-info.java")));
    }

    @Test
    void deriveOutputNameForJavaFileLowercases() {
        assertEquals("orderprocessor-sequence-diagram.svg",
                KartaCli.deriveOutputName(Path.of("OrderProcessor.java")));
    }

    @Test
    void deriveOutputNameForDirectoryUsesActualDir(@TempDir Path dir) {
        assertEquals("class-diagram.svg", KartaCli.deriveOutputName(dir));
    }

    @Test
    void deriveOutputNameForStateMachineFile() {
        assertEquals("workflow-state-machine-diagram.svg",
                KartaCli.deriveOutputName(Path.of("Workflow.java"), false, true));
    }

    @Test
    void deriveOutputNamePreservesLowercaseName() {
        assertEquals("service-sequence-diagram.svg",
                KartaCli.deriveOutputName(Path.of("service.java")));
    }

    // ------------------------------------------------------------------
    // Oversize warning
    // ------------------------------------------------------------------

    /** A graph laid out beyond the readable bound must be reported, not written in silence. */
    @Test
    void warnsWhenTheLaidOutCanvasExceedsTheReadableBound() {
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(KartaCli.class.getName());
        java.util.logging.Handler capture = captureInto(records);
        log.addHandler(capture);
        try {
            KartaCli.warnIfOversized(svgOfSize(22600, 42988), graphWithNodeAt(10, 10),
                                     Path.of("wide.svg"));

            assertTrue(records.stream().anyMatch(r ->
                            r.getLevel() == java.util.logging.Level.WARNING
                                    && r.getMessage().contains("--max-depth")),
                    "an oversize canvas must warn and name the flags that narrow it");
        } finally {
            log.removeHandler(capture);
        }
    }

    /** A graph inside the bound must stay silent, or the warning degrades into noise. */
    @Test
    void doesNotWarnForANormalSizedCanvas() {
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(KartaCli.class.getName());
        java.util.logging.Handler capture = captureInto(records);
        log.addHandler(capture);
        try {
            KartaCli.warnIfOversized(svgOfSize(1200, 800), graphWithNodeAt(10, 10), Path.of("normal.svg"));

            assertTrue(records.stream()
                            .noneMatch(r -> r.getLevel() == java.util.logging.Level.WARNING),
                    "a diagram that fits on a screen must not warn");
        } finally {
            log.removeHandler(capture);
        }
    }

    // ------------------------------------------------------------------
    // --split-packages
    // ------------------------------------------------------------------

    /**
     * A tree of three packages must yield three diagrams laid out to mirror the packages, not one
     * diagram of everything. This is the readable-at-scale path: a wide tree stays wide whatever
     * --max-depth says, so the split is what makes the output usable.
     */
    @Test
    void splitPackagesEmitsOneDiagramPerPackageMirroringTheTree(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        writeClass(src.resolve("com/example/core"), "Engine");
        writeClass(src.resolve("com/example/core"), "Piston");
        writeClass(src.resolve("com/example/io"), "Reader");
        Files.createDirectories(src.resolve("com/example/empty"));

        Path out = dir.resolve("out");
        java.util.List<Path> written = KartaCli.runPerPackage(src, out, RunOptions.defaults());

        assertFalse(written.isEmpty(), "a tree with sources must produce diagrams");
        assertTrue(Files.exists(out.resolve("com/example/core")),
                "output must mirror the package structure for com.example.core");
        assertTrue(Files.exists(out.resolve("com/example/io")),
                "output must mirror the package structure for com.example.io");
        assertFalse(Files.exists(out.resolve("com/example/empty")),
                "a package with no sources must not produce an output directory");
    }

    /** A directory holding no Java at all is a no-op, not a failure. */
    @Test
    void splitPackagesOnAnEmptyTreeWritesNothing(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("com/example/nothing"));

        java.util.List<Path> written = KartaCli.runPerPackage(
                src, dir.resolve("out"), RunOptions.defaults());

        assertTrue(written.isEmpty(), "no sources means no diagrams, and no exception");
    }

    /** Pointed at a single file, the split path falls back to rendering that file. */
    @Test
    void splitPackagesOnAFileFallsBackToASingleDiagram(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("src/com/example");
        writeClass(pkg, "Solo");

        java.util.List<Path> written = KartaCli.runPerPackage(
                pkg.resolve("Solo.java"), dir.resolve("out"), RunOptions.defaults());

        assertEquals(1, written.size(), "a single file input must still produce its one diagram");
    }

    /** The CLI parsed --exclude but never passed it to the state-machine or module parsers (#266). */
    @Test
    void excludeReachesTheStateMachineAndModuleModes(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Workflows.java");
        Files.writeString(src, """
                public class Workflows {
                    enum Order { NEW, PAID }
                    enum Light { RED, GREEN }
                    private Order order = Order.NEW;
                    private Light light = Light.RED;
                    void advance() {
                        switch (order) { case NEW -> order = Order.PAID; default -> {} }
                        switch (light) { case RED -> light = Light.GREEN; default -> {} }
                    }
                }
                """);
        RunOptions options = new RunOptions(false, "simple", true, java.util.Set.of("Light"),
                Integer.MAX_VALUE, false, null,
                se.deversity.codekarta.input.parser.ClassDiagramParser.DEFAULT_MAX_MEMBERS,
                RunOptions.FORMAT_JSON, false);

        String json = Files.readString(KartaCli.run(src, dir.resolve("out"), options));

        assertTrue(json.contains("Workflows.Order.NEW"));
        assertFalse(json.contains("Workflows.Light"), "--exclude must reach --state-machine");

        Path reactor = Files.createDirectories(dir.resolve("reactor"));
        Files.writeString(reactor.resolve("pom.xml"),
                "<project><artifactId>parent</artifactId><modules><module>alpha</module><module>beta</module></modules></project>");
        writeFile(reactor.resolve("alpha/pom.xml"), "<project><artifactId>alpha</artifactId></project>");
        writeFile(reactor.resolve("beta/pom.xml"), "<project><artifactId>beta</artifactId></project>");

        assertNull(KartaCli.parseModules(reactor, false, java.util.Set.of("beta")).findNode("beta"),
                "--exclude must reach --modules-only");
    }

    /**
     * SequenceDiagramRenderer computes its own geometry and ignores Node x/y, so laying out an
     * interaction graph for an SVG was wasted work (ELK layered layout, with --layout elk) (#272).
     * JSON keeps the coordinates it documents.
     */
    @Test
    void anInteractionGraphIsLaidOutForJsonButNotForSvg(@TempDir Path dir) throws Exception {
        Path input = Files.writeString(dir.resolve("A.java"), "class A {}");

        Graph forSvg = interactionGraph();
        KartaCli.run(input, dir.resolve("svg"), RunOptions.defaults().withOutputName("a.svg"), forSvg);
        assertNull(forSvg.findNode("A.a").getX(), "a sequence SVG must not pay for a layout it discards");

        Graph forJson = interactionGraph();
        KartaCli.run(input, dir.resolve("json"),
                RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON).withOutputName("a.json"), forJson);
        assertNotNull(forJson.findNode("A.a").getX(), "JSON output documents layout coordinates");
    }

    private static Graph interactionGraph() {
        Graph graph = new Graph();
        graph.addNode(new se.deversity.codekarta.core.model.Node("A.a", "METHOD", "a"));
        graph.addNode(new se.deversity.codekarta.core.model.Node("B.b", "METHOD", "b"));
        se.deversity.codekarta.core.model.Edge call = new se.deversity.codekarta.core.model.Edge("c1", "A.a", "B.b", "CALLS");
        call.setLabel("1");
        graph.addEdge(call);
        return graph;
    }

    // --- --output-name ---

    /**
     * Without this, N diagrams need N output directories, because every class diagram is
     * called class-diagram.svg. One real consumer ended up with an out/, out/runner/,
     * out/spi/ and out/report/ purely to work around the fixed name.
     */
    @Test
    void outputNameOverridesTheDerivedFileName(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");
        Path out = dir.resolve("out");

        Path written = KartaCli.run(src, out, RunOptions.defaults().withOutputName("alpha-classes.svg"));

        assertEquals("alpha-classes.svg", String.valueOf(written.getFileName()));
        assertTrue(Files.exists(written));
    }

    @Test
    void twoRunsWithDifferentOutputNamesShareOneDirectory(@TempDir Path dir) throws Exception {
        Path alpha = dir.resolve("src/com/example/alpha");
        Path beta = dir.resolve("src/com/example/beta");
        writeClass(alpha, "Alpha");
        writeClass(beta, "Beta");
        Path out = dir.resolve("out");

        KartaCli.run(alpha, out, RunOptions.defaults().withOutputName("alpha.svg"));
        KartaCli.run(beta, out, RunOptions.defaults().withOutputName("beta.svg"));

        assertTrue(Files.exists(out.resolve("alpha.svg")));
        assertTrue(Files.exists(out.resolve("beta.svg")), "the second run must not overwrite the first");
    }

    /** --output-name is caller data, not a path: it must not be able to escape --output. */
    @Test
    void outputNameCannotWriteOutsideTheOutputDirectory(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");
        Path out = dir.resolve("out");

        Path written = KartaCli.run(src, out, RunOptions.defaults().withOutputName("../escaped.svg"));

        assertEquals(out.normalize(), written.getParent().normalize(),
                "a traversing name must fall back to the derived one, inside --output");
        assertFalse(Files.exists(dir.resolve("escaped.svg")), "nothing may be written outside --output");
    }

    /**
     * {@code --output .} normalises {@code ./alpha.svg} to the bare {@code alpha.svg}, whose
     * parent is null, so a parent-equals-output check silently dropped a perfectly plain name.
     */
    @Test
    void outputNameIsHonouredWhenOutputIsTheCurrentDirectory() {
        RunOptions options = RunOptions.defaults().withOutputName("alpha.svg");

        assertEquals(Path.of(".").resolve("alpha.svg"),
                KartaCli.resolveOutputFile(Path.of("Alpha.java"), Path.of("."), options));
        assertEquals(Path.of("alpha.svg"),
                KartaCli.resolveOutputFile(Path.of("Alpha.java"), Path.of(""), options));
    }

    /**
     * A link already sitting in --output, under the name the diagram is about to take, would
     * otherwise carry the write to wherever it points. The output directory is the boundary.
     */
    @Test
    void aSymlinkInTheOutputDirectoryIsNotWrittenThrough(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");
        Path out = Files.createDirectories(dir.resolve("out"));
        Path victim = Files.writeString(dir.resolve("victim.txt"), "untouched");
        symlinkOrAbort(out.resolve("class-diagram.svg"), victim);

        assertThrows(java.io.IOException.class, () -> KartaCli.run(src, out, RunOptions.defaults()));
        assertEquals("untouched", Files.readString(victim), "the link's target must not be overwritten");
    }

    /** A dangling link is the worse case: following it would create a file outside --output. */
    @Test
    void aDanglingSymlinkInTheOutputDirectoryDoesNotCreateItsTarget(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");
        Path out = Files.createDirectories(dir.resolve("out"));
        Path victim = dir.resolve("created-outside.svg");
        symlinkOrAbort(out.resolve("alpha.svg"), victim);

        assertThrows(java.io.IOException.class,
                () -> KartaCli.run(src, out, RunOptions.defaults().withOutputName("alpha.svg")));
        assertFalse(Files.exists(victim), "nothing may be created outside --output");
    }

    @Test
    void outputNameWithASubdirectoryIsRefused(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");
        Path out = dir.resolve("out");

        Path written = KartaCli.run(src, out, RunOptions.defaults().withOutputName("nested/alpha.svg"));

        assertEquals("class-diagram.svg", String.valueOf(written.getFileName()));
    }

    @Test
    void aBlankOutputNameFallsBackToTheDerivedName(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        writeClass(src, "Alpha");

        Path written = KartaCli.run(src, dir.resolve("out"), RunOptions.defaults().withOutputName("  "));

        assertEquals("class-diagram.svg", String.valueOf(written.getFileName()));
    }

    // --- --modules-only over a build reactor ---

    /**
     * --modules-only understood JPMS and nothing else, so it returned "Graph is empty" for
     * every Maven or Gradle reactor without a module-info.java — which is most of them.
     */
    @Test
    void modulesOnlyReadsAMavenReactorWhenThereIsNoModuleInfo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"),
                "<project><artifactId>parent</artifactId><modules>"
              + "<module>alpha</module><module>beta</module></modules></project>\n");
        for (String module : new String[]{"alpha", "beta"}) {
            Files.createDirectories(dir.resolve(module));
            Files.writeString(dir.resolve(module).resolve("pom.xml"),
                    "<project><artifactId>" + module + "</artifactId></project>\n");
        }

        Path written = KartaCli.run(dir, dir.resolve("out"),
                new RunOptions(false, "simple", false, java.util.Set.of(),
                        Integer.MAX_VALUE, true, null, 6));

        assertNotNull(written, "a Maven reactor is a module structure");
        assertEquals("modules-diagram.svg", String.valueOf(written.getFileName()));
        String svg = Files.readString(written);
        assertTrue(svg.contains("alpha") && svg.contains("beta"), "both modules must appear");
    }

    @Test
    void modulesOnlyReadsAGradleReactorWhenThereIsNoModuleInfo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("settings.gradle.kts"),
                "rootProject.name = \"demo\"\ninclude(\"alpha\", \"beta\")\n");

        Path written = KartaCli.run(dir, dir.resolve("out"),
                new RunOptions(false, "simple", false, java.util.Set.of(),
                        Integer.MAX_VALUE, true, null, 6));

        assertNotNull(written, "a Gradle reactor is a module structure");
        assertTrue(Files.readString(written).contains("alpha"));
    }

    @Test
    void modulesOnlyStillPrefersModuleInfoWhenBothExist(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"),
                "<project><artifactId>parent</artifactId><modules>"
              + "<module>alpha</module><module>beta</module></modules></project>\n");
        for (String module : new String[]{"alpha", "beta"}) {
            Files.createDirectories(dir.resolve(module));
            Files.writeString(dir.resolve(module).resolve("pom.xml"),
                    "<project><artifactId>" + module + "</artifactId></project>\n");
        }
        Files.writeString(dir.resolve("alpha").resolve("module-info.java"),
                "module com.example.alpha { requires java.logging; }\n");

        se.deversity.codekarta.core.model.Graph graph = KartaCli.parseModules(dir);

        assertNotNull(graph.findNode("com.example.alpha"), "JPMS is the more precise answer");
        assertNull(graph.findNode("parent"), "and it is used instead of, not alongside, the reactor");
    }

    // --- --max-members ---

    @Test
    void maxMembersRaisesTheCompartmentCap(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/com/example");
        Files.createDirectories(src);
        StringBuilder wide = new StringBuilder("package com.example;\n\npublic class Wide {\n");
        for (int i = 0; i < 10; i++) {
            wide.append("    public void m").append(i).append("() { }\n");
        }
        Files.writeString(src.resolve("Wide.java"), wide.append("}\n").toString());

        Path capped = KartaCli.run(src, dir.resolve("capped"), RunOptions.defaults());
        Path full = KartaCli.run(src, dir.resolve("full"),
                new RunOptions(false, "simple", false, java.util.Set.of(),
                        Integer.MAX_VALUE, false, null, 0));

        assertTrue(Files.readString(capped).contains("more)"), "the default cap still truncates");
        assertFalse(Files.readString(full).contains("more)"), "--max-members all shows every member");
        assertTrue(Files.readString(full).contains("m9()"), "including the last one");
    }

    private static void writeClass(Path packageDir, String name) throws java.io.IOException {
        Files.createDirectories(packageDir);
        String pkg = packageDir.toString().replace('\\', '/');
        pkg = pkg.substring(pkg.indexOf("com/")).replace('/', '.');
        Files.writeString(packageDir.resolve(name + ".java"),
                "package " + pkg + ";\n\npublic class " + name + " {\n"
                        + "    public void work() { }\n}\n");
    }

    /** Minimal SVG root carrying the canvas dimensions the warning reads back. */
    private static String svgOfSize(int width, int height) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
            </svg>
            """.formatted(width, height, width, height);
    }

    private static java.util.logging.Handler captureInto(java.util.List<java.util.logging.LogRecord> sink) {
        return new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { sink.add(record); }
            @Override public void flush() { /* nothing is buffered */ }
            @Override public void close() { /* nothing to release */ }
        };
    }

    private static se.deversity.codekarta.core.model.Graph graphWithNodeAt(double x, double y) {
        se.deversity.codekarta.core.model.Graph graph = new se.deversity.codekarta.core.model.Graph();
        se.deversity.codekarta.core.model.Node node =
                new se.deversity.codekarta.core.model.Node("A", "CLASS", "A");
        node.setX(x);
        node.setY(y);
        node.setWidth(180.0);
        node.setHeight(70.0);
        graph.addNode(node);
        return graph;
    }

    // --- --format json ---

    @Test
    void jsonFormatWritesTheGraphInsteadOfAPicture(@TempDir Path inputDir, @TempDir Path outputDir)
            throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"), "public class Animal {}");
        Files.writeString(inputDir.resolve("Dog.java"), "public class Dog extends Animal {}");

        Path result = KartaCli.run(inputDir, outputDir,
                RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON));

        assertEquals("class-diagram.json", result.getFileName().toString(),
                "the derived name takes the format's extension");
        String json = Files.readString(result);
        assertFalse(json.contains("<svg"), "json output must not be SVG");
        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"Animal\""));
        assertTrue(json.contains("\"EXTENDS\""), "the edge must survive into the JSON");
    }

    @Test
    void jsonFormatIncludesLayoutCoordinates(@TempDir Path inputDir, @TempDir Path outputDir)
            throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"), "public class Animal {}");

        Path result = KartaCli.run(inputDir, outputDir,
                RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON));

        String json = Files.readString(result);
        assertTrue(json.contains("\"x\""),
                "layout runs before rendering, so coordinates are in the JSON: " + json);
        assertTrue(json.contains("\"width\""));
    }

    @Test
    void jsonOutputIsIdenticalAcrossRuns(@TempDir Path inputDir, @TempDir Path outputDir)
            throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"),
                "public class Animal { private String name; private int age; }");

        RunOptions options = RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON);
        String first = Files.readString(KartaCli.run(inputDir, outputDir, options));
        String second = Files.readString(KartaCli.run(inputDir, outputDir, options));

        // Same invariant as the SVG path: a repository can commit this file, and the CI step that
        // diffs regenerated output would fail on any instability.
        assertEquals(first, second, "json output must be byte-identical across runs");
    }

    @Test
    void explicitOutputNameIsUsedAsGivenEvenForJson(@TempDir Path inputDir, @TempDir Path outputDir)
            throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"), "public class Animal {}");

        Path result = KartaCli.run(inputDir, outputDir,
                RunOptions.defaults()
                        .withFormat(RunOptions.FORMAT_JSON)
                        .withOutputName("graph.txt"));

        assertEquals("graph.txt", result.getFileName().toString(),
                "a caller who names the file has already decided what to call it");
    }

    @Test
    void svgStaysTheDefault(@TempDir Path inputDir, @TempDir Path outputDir) throws Exception {
        Files.writeString(inputDir.resolve("Animal.java"), "public class Animal {}");

        Path result = KartaCli.run(inputDir, outputDir, RunOptions.defaults());

        assertEquals("class-diagram.svg", result.getFileName().toString());
        assertTrue(Files.readString(result).contains("<svg "));
    }

    @Test
    void formatDefaultsToSvgWhenNull() {
        assertEquals(RunOptions.FORMAT_SVG,
                new RunOptions(false, "simple", false, null, 1, false, null, 6, null).format());
    }

    @Test
    void withFormatExtensionOnlyRewritesSvgNames() {
        RunOptions json = RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON);

        assertEquals("class-diagram.json", KartaCli.withFormatExtension("class-diagram.svg", json));
        assertEquals("report.json", KartaCli.withFormatExtension("report", json));
        assertEquals("class-diagram.svg",
                KartaCli.withFormatExtension("class-diagram.svg", RunOptions.defaults()));
    }

    // --- a whole repository as --input ---

    /** A Maven checkout: production code, a test, and generated code under target/. */
    private static Path repository(Path root) throws Exception {
        write(root, "pom.xml", "<project/>");
        write(root, "src/main/java/shop/Order.java", "package shop; public class Order {}");
        write(root, "src/main/java/shop/billing/Invoice.java",
                "package shop.billing; public class Invoice { shop.Order order; }");
        write(root, "src/test/java/shop/OrderTest.java", "package shop; class OrderTest {}");
        write(root, "src/test/java/shop/support/Fixtures.java", "package shop.support; class Fixtures {}");
        write(root, "target/generated-sources/shop/OrderMapper.java",
                "package shop; class OrderMapper {}");
        return root;
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void repositoryRootDrawsProductionCodeOnly(@TempDir Path dir, @TempDir Path outputDir) throws Exception {
        Path repo = repository(dir);

        String svg = Files.readString(KartaCli.run(repo, outputDir));

        assertTrue(svg.contains(">Order<"), "production class must be drawn");
        assertTrue(svg.contains(">Invoice<"), "production class in a subpackage must be drawn");
        assertFalse(svg.contains("OrderTest"), "test class must not be drawn by default");
        assertFalse(svg.contains("OrderMapper"), "generated class under target/ must not be drawn");
    }

    @Test
    void includeTestsFlagDrawsTestSources(@TempDir Path dir, @TempDir Path outputDir) throws Exception {
        Path repo = repository(dir);

        KartaCli.main(new String[] {
                "--input", repo.toString(), "--output", outputDir.toString(), "--include-tests"});

        String svg = Files.readString(outputDir.resolve("class-diagram.svg"));
        assertTrue(svg.contains("OrderTest"), "--include-tests must bring test classes back");
        assertFalse(svg.contains("OrderMapper"), "build output stays out even with --include-tests");
    }

    @Test
    void splitPackagesOverARepositorySkipsTestOnlyPackages(@TempDir Path dir, @TempDir Path outputDir)
            throws Exception {
        Path repo = repository(dir);

        java.util.List<Path> written = KartaCli.runPerPackage(repo, outputDir, RunOptions.defaults());

        java.util.List<String> relative = written.stream()
                .map(p -> outputDir.relativize(p).toString().replace('\\', '/'))
                .toList();
        assertEquals(java.util.List.of(
                "src/main/java/shop/class-diagram.svg",
                "src/main/java/shop/billing/class-diagram.svg"), relative);
    }

    // --- argument parsing (#231) ---

    private static String errorFor(String... args) {
        return assertThrows(IllegalArgumentException.class, () -> KartaCli.parseArgs(args)).getMessage();
    }

    @Test
    void anUnknownFlagIsAnErrorRatherThanIgnored() {
        String message = errorFor("--input", "src", "--sequnce-only");

        assertTrue(message.contains("--sequnce-only"), "the message names the argument: " + message);
    }

    @Test
    void aValuedFlagWithNoValueIsAnError() {
        assertTrue(errorFor("--input", "src", "--max-depth").contains("--max-depth"));
        assertTrue(errorFor("--input", "src", "--format").contains("--format"));
        assertTrue(errorFor("--input", "--sequence-only").contains("--input"),
                "a flag where the value should be is a missing value, not a path called --sequence-only");
    }

    @Test
    void anUnknownLayoutOrFormatIsAnError() {
        assertTrue(errorFor("--input", "src", "--layout", "elkk").contains("elkk"));
        assertTrue(errorFor("--input", "src", "--format", "png").contains("png"));
    }

    @Test
    void aNumberFlagWithANonNumberIsAnError() {
        assertTrue(errorFor("--input", "src", "--max-depth", "abc").contains("abc"));
        assertTrue(errorFor("--input", "src", "--max-members", "lots").contains("lots"));
    }

    @Test
    void inputIsRequired() {
        assertTrue(errorFor("--output", "out").contains("--input"));
    }

    @Test
    void validArgumentsAreParsed() {
        KartaCli.Invocation call = KartaCli.parseArgs("--input", "src", "--output", "out",
                "--sequence-only", "--layout", "ELK", "--format", "json", "--max-depth", "3",
                "--max-members", "all", "--exclude", "Foo, *Test", "--split-packages", "--include-tests",
                "--output-name", "x.json");

        assertEquals(Path.of("src"), call.inputPath());
        assertEquals(Path.of("out"), call.outputDir());
        assertTrue(call.splitPackages());
        assertFalse(call.help());
        RunOptions options = call.options();
        assertTrue(options.sequenceOnly());
        assertEquals("elk", options.layout());
        assertEquals(RunOptions.FORMAT_JSON, options.format());
        assertEquals(3, options.maxDepth());
        assertEquals(se.deversity.codekarta.input.parser.ClassDiagramParser.UNLIMITED_MEMBERS, options.maxMembers());
        assertEquals(java.util.Set.of("Foo", "*Test"), options.customExcludes());
        assertTrue(options.includeTests());
        assertEquals("x.json", options.outputName());
    }

    @Test
    void defaultsApplyWhenOnlyInputIsGiven() {
        KartaCli.Invocation call = KartaCli.parseArgs("--input", "src");

        assertEquals(KartaCli.DEFAULT_OUTPUT, call.outputDir());
        assertEquals(RunOptions.defaults(), call.options());
    }

    @Test
    void helpNeedsNoInput() {
        assertTrue(KartaCli.parseArgs("--help").help());
        assertTrue(KartaCli.parseArgs("-h").help());
    }

    @Test
    void runOptionsRejectsAnUnknownLayoutOrFormat() {
        RunOptions defaults = RunOptions.defaults();
        IllegalArgumentException layout = assertThrows(IllegalArgumentException.class, () -> new RunOptions(
                false, "elkk", false, null, 1, false, null, 6, "svg", false));
        assertTrue(layout.getMessage().contains("elkk"));
        IllegalArgumentException format = assertThrows(IllegalArgumentException.class, () -> defaults.withFormat("png"));
        assertTrue(format.getMessage().contains("png"));
        assertEquals("elk", new RunOptions(false, "ELK", false, null, 1, false, null, 6, "JSON", false).layout(),
                "known values are accepted in any case and stored lower-case");
    }

    // --- --split-packages draws each package once (#232) ---

    @Test
    void splitPackagesDrawsEachPackageWithoutItsSubpackages(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        writeFile(src.resolve("top/T.java"), "package top; public class T {}");
        writeFile(src.resolve("top/sub/U.java"), "package top.sub; public class U {}");
        Path out = dir.resolve("out");

        java.util.List<Path> written = KartaCli.runPerPackage(src, out, RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON));

        assertEquals(java.util.List.of(out.resolve("top/class-diagram.json"), out.resolve("top/sub/class-diagram.json")), written);
        String top = Files.readString(out.resolve("top/class-diagram.json"));
        assertTrue(top.contains("\"top.T\""), top);
        assertFalse(top.contains("top.sub.U"), "the top diagram must not draw its subpackage again: " + top);
    }

    @Test
    void splitPackagesKeepsACrossPackageReferenceAsAStub(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        writeFile(src.resolve("top/T.java"), "package top; import top.sub.U; public class T { U u; }");
        writeFile(src.resolve("top/sub/U.java"), "package top.sub; public class U { int size; }");
        Path out = dir.resolve("out");

        KartaCli.runPerPackage(src, out, RunOptions.defaults().withFormat(RunOptions.FORMAT_JSON));

        String top = Files.readString(out.resolve("top/class-diagram.json"));
        assertTrue(top.contains("\"targetId\" : \"top.sub.U\"") || top.contains("\"targetId\":\"top.sub.U\""),
                "the edge to the other package is kept: " + top);
        assertFalse(top.contains("size"), "the other package's type is a stub, without its members: " + top);
    }

    @Test
    void splitPackagesInSequenceModeResolvesCallsIntoOtherPackages(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        writeFile(src.resolve("a/A.java"), "package a; import b.B; public class A { void go() { B.run(); } }");
        writeFile(src.resolve("b/B.java"), "package b; public class B { public static void run() { } }");
        Path out = dir.resolve("out");
        RunOptions sequence = new RunOptions(true, "simple", false, null, Integer.MAX_VALUE, false, null,
                6, RunOptions.FORMAT_JSON, false);

        KartaCli.runPerPackage(src, out, sequence);

        String a = Files.readString(firstJson(out.resolve("a")));
        assertTrue(a.contains("b.B.run"), "the call into package b resolves against the whole tree: " + a);
        assertFalse(a.contains("\"b.B\""), "package b's own class is not drawn in package a's diagram as a type of a: " + a);
    }

    @Test
    void splitPackagesDrawsEachPackagesStateMachineOnceEvenWhenItsEnumLivesElsewhere(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        writeFile(src.resolve("model/OrderStatus.java"), "package model; public enum OrderStatus { NEW, PAID }");
        writeFile(src.resolve("service/Orders.java"), """
                package service;
                import model.OrderStatus;
                public class Orders {
                    private OrderStatus status = OrderStatus.NEW;
                    void pay() { status = OrderStatus.PAID; }
                }
                """);
        writeFile(src.resolve("service/sub/Lamp.java"), """
                package service.sub;
                public class Lamp {
                    enum Light { ON, OFF }
                    private Light light = Light.ON;
                    void off() { light = Light.OFF; }
                }
                """);
        Path out = dir.resolve("out");
        RunOptions stateMachine = new RunOptions(false, "simple", true, null, Integer.MAX_VALUE, false, null,
                6, RunOptions.FORMAT_JSON, false);

        KartaCli.runPerPackage(src, out, stateMachine);

        String service = Files.readString(firstJson(out.resolve("service")));
        assertTrue(service.contains("OrderStatus.PAID"), "the enum from package model still resolves: " + service);
        assertFalse(service.contains("Lamp.Light"), "the subpackage's machine is not drawn again: " + service);
        assertTrue(Files.readString(firstJson(out.resolve("service/sub"))).contains("Lamp.Light.OFF"));
    }

    @Test
    void splitPackagesWithModulesOnlyIsRejected(@TempDir Path dir) {
        assertTrue(errorFor("--input", "src", "--modules-only", "--split-packages").contains("--split-packages"));
        RunOptions modules = new RunOptions(false, "simple", false, null, Integer.MAX_VALUE, true, null,
                6, RunOptions.FORMAT_SVG, false);
        assertThrows(IllegalArgumentException.class, () -> KartaCli.runPerPackage(dir, dir.resolve("out"), modules));
    }

    private static Path firstJson(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
        }
    }

    /**
     * Creating a symlink needs a privilege Windows withholds by default, so the symlink cases run
     * on CI (Linux) and report as aborted, not passed, where the link cannot be made.
     */
    private static void symlinkOrAbort(Path link, Path target) throws java.io.IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            org.junit.jupiter.api.Assumptions.abort("cannot create a symbolic link here: " + e.getMessage());
        }
    }

    private static void writeFile(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
