package se.deversity.codekarta.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        assertTrue(svg.endsWith("</svg>"), "SVG must be closed");
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
        assertTrue(svg.endsWith("</svg>"),  "must end with </svg>");
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
        Path kartaCliSrc = Path.of("src/main/java/com/karta/cli/KartaCli.java");
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
}
