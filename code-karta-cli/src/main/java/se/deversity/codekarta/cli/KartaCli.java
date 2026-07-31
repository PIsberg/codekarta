package se.deversity.codekarta.cli;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.JavaSourceInputParser;
import se.deversity.codekarta.input.MultiFileSequenceParser;
import se.deversity.codekarta.input.parser.StateMachineParser;
import se.deversity.codekarta.layout.ElkLayoutEngine;
import se.deversity.codekarta.layout.LayoutEngine;
import se.deversity.codekarta.layout.SimpleLayoutEngine;
import se.deversity.codekarta.render.SvgRenderer;

import se.deversity.vibetags.annotations.AIAudit;
import se.deversity.vibetags.annotations.AIContract;
import se.deversity.vibetags.annotations.AIIdempotent;
import se.deversity.vibetags.annotations.AITestDriven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@AIContract(reason = "run(Path, Path, boolean, String) is a public static method tested directly by KartaCliTest without spawning a process. Its signature and output filename conventions (module-diagram.svg, class-diagram.svg, <name>-sequence-diagram.svg) must remain stable.")
@AIAudit(checkFor = {"Path traversal", "Unauthorized file write"})
@AITestDriven(
    framework = AITestDriven.Framework.JUNIT_5,
    coverageGoal = 90,
    testLocation = "code-karta-cli/src/test/java/com/karta/cli/KartaCliTest.java",
    mockPolicy = "Do not mock parsers or layout engines — KartaCliTest calls run() directly against the example-shipping-system fixture for end-to-end coverage"
)
public class KartaCli {

    private static final Logger log = Logger.getLogger(KartaCli.class.getName());

    static final Path DEFAULT_OUTPUT = Path.of("output");

    /**
     * Stages of the parse → layout → render → write pipeline.
     *
     * <p>Tracked as a local variable in {@link #run(Path, Path, boolean, String, boolean)}
     * so that {@code StateMachineParser} can extract the pipeline as a state-transition
     * diagram — each constant becomes a {@code STATE} node and each sequential assignment
     * becomes a {@code TRANSITION} edge.</p>
     */
    enum PipelineStage { PARSING, LAYOUT, RENDERING, WRITING, DONE }

    @SuppressWarnings("PMD.AvoidReassigningLoopVariables") // args[++i] is the standard flag-value idiom
    public static void main(String[] args) {
        Path inputPath = null;
        Path outputDir = DEFAULT_OUTPUT;
        boolean sequenceOnly = false;
        boolean stateMachine = false;
        boolean modulesOnly = false;
        String layout = "simple";
        java.util.Set<String> customExcludes = java.util.Collections.emptySet();
        int maxDepth = Integer.MAX_VALUE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input"         -> { if (i + 1 < args.length) inputPath = Path.of(args[++i]); }
                case "--output"        -> { if (i + 1 < args.length) outputDir = Path.of(args[++i]); }
                case "--layout"        -> { if (i + 1 < args.length) layout = args[++i]; }
                case "--sequence-only" -> sequenceOnly = true;
                case "--state-machine" -> stateMachine = true;
                case "--modules-only"  -> modulesOnly = true;
                case "--exclude"       -> {
                    if (i + 1 < args.length) {
                        String rawExcludes = args[++i];
                        customExcludes = java.util.Arrays.stream(rawExcludes.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toSet());
                    }
                }
                case "--max-depth"     -> {
                    if (i + 1 < args.length) {
                        try {
                            maxDepth = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: --max-depth must be an integer, ignoring.");
                        }
                    }
                }
                case "--help", "-h"    -> { printUsage(); System.exit(0); }
                default -> { /* unknown args are ignored */ }
            }
        }

        if (inputPath == null) {
            System.err.println("Error: --input is required.");
            printUsage();
            System.exit(1);
        }

        try {
            Path output = run(inputPath, outputDir, sequenceOnly, layout, stateMachine, customExcludes, maxDepth, modulesOnly);
            if (output != null) {
                System.out.println("Generated: " + output.toAbsolutePath());
            } else {
                System.out.println("Skipped generating a diagram; see the log message above for why.");
            }
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
        return run(inputPath, outputDir, sequenceOnly, layout, false);
    }

    /**
     * Runs the full parse → layout → render pipeline and writes the SVG to outputDir.
     *
     * @param stateMachine when true, uses StateMachineParser to extract enum-backed
     *                     STATE nodes and TRANSITION edges
     * @return the path of the written SVG file
     */
    public static Path run(Path inputPath, Path outputDir,
                           boolean sequenceOnly, String layout,
                           boolean stateMachine) throws IOException {
        return run(inputPath, outputDir, sequenceOnly, layout, stateMachine, java.util.Collections.emptySet(), Integer.MAX_VALUE);
    }

    /**
     * Runs the full parse → layout → render pipeline with exclusions and depth limits and writes the SVG to outputDir.
     *
     * @param customExcludes set of type/package/method patterns to filter out from diagram nodes/edges
     * @param maxDepth       maximum sequence depth limit (for depth-limited stitched sequence diagrams)
     * @return the path of the written SVG file
     */
    @AIIdempotent(reason = "Re-running with the same inputs regenerates byte-identical SVG output — the verify-phase diagram generation and doc regeneration rely on repeated runs converging.")
    public static Path run(Path inputPath, Path outputDir,
                           boolean sequenceOnly, String layout,
                           boolean stateMachine, java.util.Set<String> customExcludes,
                           int maxDepth) throws IOException {
        return run(inputPath, outputDir, sequenceOnly, layout, stateMachine, customExcludes, maxDepth, false);
    }

    @SuppressWarnings({"PMD.UnusedAssignment", "UnusedVariable"}) // 'state' assignments are extracted by StateMachineParser as the pipeline diagram
    public static Path run(Path inputPath, Path outputDir,
                           boolean sequenceOnly, String layout,
                           boolean stateMachine, java.util.Set<String> customExcludes,
                           int maxDepth, boolean modulesOnly) throws IOException {
        Files.createDirectories(outputDir);

        PipelineStage state = PipelineStage.PARSING;
        Graph graph;
        if (stateMachine) {
            log.fine(() -> "State-machine mode for input: " + inputPath);
            graph = new StateMachineParser().parse(inputPath);
        } else if (modulesOnly && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-module mode for directory: " + inputPath);
            graph = new se.deversity.codekarta.input.parser.ModuleInfoParser().parseDirectory(inputPath);
        } else if (sequenceOnly && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-file sequence mode for directory: " + inputPath);
            graph = new MultiFileSequenceParser(customExcludes, maxDepth).parse(inputPath);
        } else {
            graph = new JavaSourceInputParser(sequenceOnly, customExcludes).parse(inputPath);
        }

        state = PipelineStage.LAYOUT;
        resolveLayout(layout).layout(graph);

        if (graph.getNodes().isEmpty() && graph.getEdges().isEmpty()) {
            log.info("Graph is empty for input " + inputPath + ", skipping diagram generation.");
            return null;
        }

        // A "state machine" with states but no transitions is not a state machine — it is an
        // ordinary enum that happens to have constants. Rendering it produces a wall of
        // disconnected boxes (an identity enum of 127 constants yields 127 of them, no arrows)
        // that looks like a result and carries no information. Decline it for the same reason the
        // empty graph above is declined, and say which case this is.
        if (stateMachine && graph.getEdges().isEmpty() && !graph.getNodes().isEmpty()) {
            log.info(() -> "No state transitions found in " + inputPath + " ("
                    + graph.getNodes().size() + " states, 0 transitions), skipping diagram"
                    + " generation. A state-transition diagram needs transitions: switch cases,"
                    + " state assignments, or transition(from, to, event) calls.");
            return null;
        }

        state = PipelineStage.RENDERING;
        String svg = new SvgRenderer().render(graph);

        state = PipelineStage.WRITING;
        Path outputFile = outputDir.resolve(deriveOutputName(inputPath, sequenceOnly, stateMachine, modulesOnly));
        Files.writeString(outputFile, svg);

        warnIfOversized(graph, outputFile);

        state = PipelineStage.DONE;
        log.fine("Pipeline " + state + ": wrote " + outputFile);
        return outputFile;
    }

    /**
     * Canvas edge, in pixels, past which a diagram stops being something a person can read.
     * Roughly ten 1440px screens; anything beyond it is a data dump wearing a diagram's clothes.
     */
    static final double OVERSIZE_PX = 15000.0;

    /**
     * Warns when the laid-out graph will not fit any screen.
     *
     * <p>A whole-package stitched call graph can reach a thousand nodes, which lays out to tens of
     * thousands of pixels on an edge — one real case measured 36020x43744. The file is still
     * written, because the caller may well be feeding it to something other than an eye, but
     * silence there reads as success. Say the size and name the two flags that reduce it.
     */
    static void warnIfOversized(Graph graph, Path outputFile) {
        double maxX = 0;
        double maxY = 0;
        for (Node node : graph.getNodes()) {
            Double x = node.getX();
            Double y = node.getY();
            Double w = node.getWidth();
            Double h = node.getHeight();
            if (x != null && w != null) maxX = Math.max(maxX, x + w);
            if (y != null && h != null) maxY = Math.max(maxY, y + h);
        }
        if (maxX > OVERSIZE_PX || maxY > OVERSIZE_PX) {
            final long w = Math.round(maxX);
            final long h = Math.round(maxY);
            final int nodes = graph.getNodes().size();
            log.warning(() -> "Wrote " + outputFile + " but it is about " + w + "x" + h
                    + "px for " + nodes + " nodes, which no screen will show usefully. Narrow it"
                    + " with --max-depth to bound call-chain length, or --exclude to drop noisy"
                    + " types (e.g. --exclude '*Test,*Builder'), or point --input at a single"
                    + " package instead of a whole tree.");
        }
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
        return deriveOutputName(inputPath, sequenceOnly, false);
    }

    static String deriveOutputName(Path inputPath, boolean sequenceOnly, boolean stateMachine) {
        return deriveOutputName(inputPath, sequenceOnly, stateMachine, false);
    }

    static String deriveOutputName(Path inputPath, boolean sequenceOnly, boolean stateMachine, boolean modulesOnly) {
        if (Files.isDirectory(inputPath)) {
            if (stateMachine) {
                return "state-machine-diagram.svg";
            }
            if (modulesOnly) {
                return "modules-diagram.svg";
            }
            return sequenceOnly ? "sequence-diagram.svg" : "class-diagram.svg";
        }
        String fileName = String.valueOf(inputPath.getFileName());
        if ("module-info.java".equals(fileName)) {
            return "module-diagram.svg";
        }
        if (stateMachine) {
            return fileName.replace(".java", "").toLowerCase(java.util.Locale.ROOT) + "-state-machine-diagram.svg";
        }
        return fileName.replace(".java", "").toLowerCase(java.util.Locale.ROOT) + "-sequence-diagram.svg";
    }

    private static void printUsage() {
        System.out.println("Usage: karta --input <path> [--output <dir>] [--sequence-only] [--state-machine] [--modules-only] [--layout simple|elk] [--exclude <patterns>] [--max-depth <depth>]");
        System.out.println();
        System.out.println("  --input  <path>      What to parse:");
        System.out.println("                         module-info.java  → module diagram");
        System.out.println("                         directory         → class diagram");
        System.out.println("                         *.java file       → sequence/exception diagram");
        System.out.println("                         directory + --sequence-only");
        System.out.println("                                           → multi-file stitched sequence diagram");
        System.out.println("                         directory + --modules-only");
        System.out.println("                                           → cross-module communication diagram");
        System.out.println("                         file/dir + --state-machine");
        System.out.println("                                           → enum-backed state transition diagram");
        System.out.println("  --output <dir>       Output directory  (default: ./output)");
        System.out.println("  --sequence-only      Emit only CALLS edges (no exception-flow).");
        System.out.println("                         When combined with a directory input, parses");
        System.out.println("                         all .java files together using cross-file");
        System.out.println("                         symbol resolution to stitch call graphs.");
        System.out.println("                         Tip: point --input at the source package root");
        System.out.println("                         (e.g. src/main/java) so the symbol solver can");
        System.out.println("                         resolve cross-package references correctly.");
        System.out.println("  --state-machine      Emit STATE nodes and TRANSITION edges from enum");
        System.out.println("                         constants, switch cases, state assignments, and");
        System.out.println("                         transition(from, to, event) calls.");
        System.out.println("  --layout <engine>    Layout algorithm:  simple (default) or elk.");
        System.out.println("                         elk uses the Eclipse Layout Kernel layered");
        System.out.println("                         algorithm for edge-crossing minimisation and");
        System.out.println("                         orthogonal routing (recommended for large graphs).");
        System.out.println("  --exclude <patterns> Comma-separated wildcard patterns of classes or methods");
        System.out.println("                         to exclude (e.g. *Test,se.deversity.codekarta.util.*,Map) to");
        System.out.println("                         reduce diagram clutter under scale.");
        System.out.println("  --max-depth <depth>  Maximum call sequence depth to parse/stitch (integer).");
        System.out.println("  --help               Show this message");
    }
}
