package com.karta.cli;

import com.karta.core.model.Graph;
import com.karta.input.JavaSourceInputParser;
import com.karta.input.MultiFileSequenceParser;
import com.karta.layout.ElkLayoutEngine;
import com.karta.layout.LayoutEngine;
import com.karta.layout.SimpleLayoutEngine;
import com.karta.render.SvgRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class KartaCli {

    private static final Logger log = Logger.getLogger(KartaCli.class.getName());

    static final Path DEFAULT_OUTPUT = Path.of("output");

    public static void main(String[] args) {
        Path inputPath = null;
        Path outputDir = DEFAULT_OUTPUT;
        boolean sequenceOnly = false;
        String layout = "simple";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input"         -> { if (i + 1 < args.length) inputPath = Path.of(args[++i]); }
                case "--output"        -> { if (i + 1 < args.length) outputDir = Path.of(args[++i]); }
                case "--layout"        -> { if (i + 1 < args.length) layout = args[++i]; }
                case "--sequence-only" -> sequenceOnly = true;
                case "--help", "-h"    -> { printUsage(); System.exit(0); }
            }
        }

        if (inputPath == null) {
            System.err.println("Error: --input is required.");
            printUsage();
            System.exit(1);
        }

        try {
            Path output = run(inputPath, outputDir, sequenceOnly, layout);
            System.out.println("Generated: " + output.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Runs the full parse → layout → render pipeline and writes the SVG to outputDir.
     *
     * @return the path of the written SVG file
     */
    public static Path run(Path inputPath, Path outputDir) throws IOException {
        return run(inputPath, outputDir, false, "simple");
    }

    /**
     * Runs the full parse → layout → render pipeline and writes the SVG to outputDir.
     *
     * @param sequenceOnly when true, uses CallSequenceParser (single file) or
     *                     MultiFileSequenceParser (directory)
     * @return the path of the written SVG file
     */
    public static Path run(Path inputPath, Path outputDir, boolean sequenceOnly) throws IOException {
        return run(inputPath, outputDir, sequenceOnly, "simple");
    }

    /**
     * Runs the full parse → layout → render pipeline and writes the SVG to outputDir.
     *
     * <p>When {@code sequenceOnly=true} and {@code inputPath} is a directory,
     * all {@code .java} files in that directory are parsed together with
     * cross-file symbol resolution ({@link MultiFileSequenceParser}), producing
     * a stitched multi-class call-sequence diagram.
     *
     * @param sequenceOnly when true, uses CallSequenceParser (single file) or
     *                     MultiFileSequenceParser (directory); exception-flow
     *                     annotations are omitted
     * @param layout       layout engine to use: {@code "elk"} for the ELK layered
     *                     algorithm, {@code "simple"} (default) for the pure-Java
     *                     BFS grid layout
     * @return the path of the written SVG file
     */
    public static Path run(Path inputPath, Path outputDir,
                           boolean sequenceOnly, String layout) throws IOException {
        Files.createDirectories(outputDir);

        Graph graph;
        if (sequenceOnly && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-file sequence mode for directory: " + inputPath);
            graph = new MultiFileSequenceParser().parse(inputPath);
        } else {
            graph = new JavaSourceInputParser(sequenceOnly).parse(inputPath);
        }

        resolveLayout(layout).layout(graph);
        String svg = new SvgRenderer().render(graph);

        Path outputFile = outputDir.resolve(deriveOutputName(inputPath, sequenceOnly));
        Files.writeString(outputFile, svg);
        log.fine("Wrote diagram: " + outputFile);
        return outputFile;
    }

    private static LayoutEngine resolveLayout(String name) {
        return "elk".equalsIgnoreCase(name) ? new ElkLayoutEngine() : new SimpleLayoutEngine();
    }

    /**
     * Maps an input path to a deterministic SVG filename.
     */
    static String deriveOutputName(Path inputPath) {
        return deriveOutputName(inputPath, false);
    }

    static String deriveOutputName(Path inputPath, boolean sequenceOnly) {
        if (Files.isDirectory(inputPath)) {
            return sequenceOnly ? "sequence-diagram.svg" : "class-diagram.svg";
        }
        String fileName = inputPath.getFileName().toString();
        if ("module-info.java".equals(fileName)) {
            return "module-diagram.svg";
        }
        return fileName.replace(".java", "").toLowerCase() + "-sequence-diagram.svg";
    }

    private static void printUsage() {
        System.out.println("Usage: karta --input <path> [--output <dir>] [--sequence-only] [--layout simple|elk]");
        System.out.println();
        System.out.println("  --input  <path>      What to parse:");
        System.out.println("                         module-info.java  → module diagram");
        System.out.println("                         directory         → class diagram");
        System.out.println("                         *.java file       → sequence/exception diagram");
        System.out.println("                         directory + --sequence-only");
        System.out.println("                                           → multi-file stitched sequence diagram");
        System.out.println("  --output <dir>       Output directory  (default: ./output)");
        System.out.println("  --sequence-only      Emit only CALLS edges (no exception-flow).");
        System.out.println("                         When combined with a directory input, parses");
        System.out.println("                         all .java files together using cross-file");
        System.out.println("                         symbol resolution to stitch call graphs.");
        System.out.println("                         Tip: point --input at the source package root");
        System.out.println("                         (e.g. src/main/java) so the symbol solver can");
        System.out.println("                         resolve cross-package references correctly.");
        System.out.println("  --layout <engine>    Layout algorithm:  simple (default) or elk.");
        System.out.println("                         elk uses the Eclipse Layout Kernel layered");
        System.out.println("                         algorithm for edge-crossing minimisation and");
        System.out.println("                         orthogonal routing (recommended for large graphs).");
        System.out.println("  --help               Show this message");
    }
}
