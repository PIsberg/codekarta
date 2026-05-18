package com.karta.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
    void deriveOutputNamePreservesLowercaseName() {
        assertEquals("service-sequence-diagram.svg",
                KartaCli.deriveOutputName(Path.of("service.java")));
    }
}
