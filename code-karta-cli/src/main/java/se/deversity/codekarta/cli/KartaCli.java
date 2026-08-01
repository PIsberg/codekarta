package se.deversity.codekarta.cli;

import se.deversity.codekarta.core.model.Graph;
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
    testLocation = "code-karta-cli/src/test/java/se/deversity/codekarta/cli/KartaCliTest.java",
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
        boolean splitPackages = false;
        String layout = "simple";
        String outputName = null;
        java.util.Set<String> customExcludes = java.util.Collections.emptySet();
        int maxDepth = Integer.MAX_VALUE;
        int maxMembers = se.deversity.codekarta.input.parser.ClassDiagramParser.DEFAULT_MAX_MEMBERS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input"         -> { if (i + 1 < args.length) inputPath = Path.of(args[++i]); }
                case "--output"        -> { if (i + 1 < args.length) outputDir = Path.of(args[++i]); }
                case "--output-name"   -> { if (i + 1 < args.length) outputName = args[++i]; }
                case "--layout"        -> { if (i + 1 < args.length) layout = args[++i]; }
                case "--sequence-only" -> sequenceOnly = true;
                case "--state-machine" -> stateMachine = true;
                case "--modules-only"  -> modulesOnly = true;
                case "--split-packages" -> splitPackages = true;
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
                case "--max-members"   -> {
                    if (i + 1 < args.length) {
                        String raw = args[++i];
                        if ("all".equalsIgnoreCase(raw)) {
                            maxMembers = se.deversity.codekarta.input.parser.ClassDiagramParser.UNLIMITED_MEMBERS;
                        } else {
                            try {
                                maxMembers = Integer.parseInt(raw);
                            } catch (NumberFormatException e) {
                                System.err.println("Warning: --max-members must be an integer or 'all', ignoring.");
                            }
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

        RunOptions options = new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, outputName, maxMembers);

        try {
            if (splitPackages) {
                java.util.List<Path> written = runPerPackage(inputPath, outputDir, options);
                if (written.isEmpty()) {
                    System.out.println("Skipped: no package under " + inputPath + " produced a diagram.");
                } else {
                    for (Path p : written) {
                        System.out.println("Generated: " + p.toAbsolutePath());
                    }
                    System.out.println("Generated " + written.size() + " diagrams under "
                            + outputDir.toAbsolutePath());
                }
                return;
            }
            Path output = run(inputPath, outputDir, options);
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

    public static Path run(Path inputPath, Path outputDir,
                           boolean sequenceOnly, String layout,
                           boolean stateMachine, java.util.Set<String> customExcludes,
                           int maxDepth, boolean modulesOnly) throws IOException {
        return run(inputPath, outputDir, new RunOptions(sequenceOnly, layout, stateMachine,
                customExcludes, maxDepth, modulesOnly, null,
                se.deversity.codekarta.input.parser.ClassDiagramParser.DEFAULT_MAX_MEMBERS));
    }

    /**
     * Runs the full parse → layout → render pipeline and writes the SVG to outputDir.
     *
     * <p>This is the one implementation; every other {@code run} overload delegates here.
     *
     * @return the path of the written SVG file, or {@code null} when there was nothing worth
     *         drawing (the reason is logged)
     */
    @SuppressWarnings({"PMD.UnusedAssignment", "UnusedVariable"}) // 'state' assignments are extracted by StateMachineParser as the pipeline diagram
    public static Path run(Path inputPath, Path outputDir, RunOptions options) throws IOException {
        Files.createDirectories(outputDir);

        PipelineStage state = PipelineStage.PARSING;
        Graph graph;
        if (options.stateMachine()) {
            log.fine(() -> "State-machine mode for input: " + inputPath);
            graph = new StateMachineParser().parse(inputPath);
        } else if (options.modulesOnly() && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-module mode for directory: " + inputPath);
            graph = parseModules(inputPath);
        } else if (options.sequenceOnly() && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-file sequence mode for directory: " + inputPath);
            graph = new MultiFileSequenceParser(options.customExcludes(), options.maxDepth()).parse(inputPath);
        } else {
            graph = new JavaSourceInputParser(options.sequenceOnly(), options.customExcludes(),
                    options.maxMembers()).parse(inputPath);
        }

        state = PipelineStage.LAYOUT;
        resolveLayout(options.layout()).layout(graph);

        if (graph.getNodes().isEmpty() && graph.getEdges().isEmpty()) {
            log.info("Graph is empty for input " + inputPath + ", skipping diagram generation.");
            return null;
        }

        // A "state machine" with states but no transitions is not a state machine — it is an
        // ordinary enum that happens to have constants. Rendering it produces a wall of
        // disconnected boxes (an identity enum of 127 constants yields 127 of them, no arrows)
        // that looks like a result and carries no information. Decline it for the same reason the
        // empty graph above is declined, and say which case this is.
        if (options.stateMachine() && graph.getEdges().isEmpty() && !graph.getNodes().isEmpty()) {
            log.info(() -> "No state transitions found in " + inputPath + " ("
                    + graph.getNodes().size() + " states, 0 transitions), skipping diagram"
                    + " generation. A state-transition diagram needs transitions: switch cases,"
                    + " state assignments, or transition(from, to, event) calls.");
            return null;
        }

        state = PipelineStage.RENDERING;
        String svg = new SvgRenderer().render(graph);

        state = PipelineStage.WRITING;
        Path outputFile = resolveOutputFile(inputPath, outputDir, options);
        Files.writeString(outputFile, svg);

        warnIfOversized(svg, graph, outputFile);

        state = PipelineStage.DONE;
        log.fine("Pipeline " + state + ": wrote " + outputFile);
        return outputFile;
    }

    /**
     * Reads the module structure, from JPMS if it is declared and from the build files if it is not.
     *
     * <p>{@code --modules-only} originally understood {@code module-info.java} and nothing else,
     * which meant it answered "which of our modules depends on which" for JPMS projects and
     * returned "Graph is empty" for everyone else — including every Maven or Gradle reactor,
     * which is where that question is actually asked. JPMS still wins when both are present: it
     * is the more precise statement of the same thing.
     */
    static Graph parseModules(Path inputPath) {
        Graph jpms = new se.deversity.codekarta.input.parser.ModuleInfoParser().parseDirectory(inputPath);
        if (!jpms.getNodes().isEmpty()) {
            return jpms;
        }
        log.fine(() -> "No module-info.java under " + inputPath + "; reading the build reactor instead.");
        return new se.deversity.codekarta.input.parser.BuildReactorParser().parse(inputPath);
    }

    /**
     * Picks the file to write, honouring {@code --output-name} when it is safe to.
     *
     * <p>The name is caller-supplied, so it is treated as data rather than as a path: a name
     * carrying separators or {@code ..} would let {@code --output-name} write outside the
     * directory the caller named with {@code --output}, which is not a thing a diagram
     * generator should be able to do. Such a name is refused and the derived name is used.
     */
    static Path resolveOutputFile(Path inputPath, Path outputDir, RunOptions options) {
        String requested = options.outputName();
        if (requested != null && !requested.isBlank()) {
            Path candidate = outputDir.resolve(requested).normalize();
            Path parent = candidate.getParent();
            boolean isBareName = Path.of(requested).getNameCount() == 1
                    && !requested.contains("..")
                    && parent != null
                    && parent.equals(outputDir.normalize());
            if (isBareName) {
                return candidate;
            }
            log.warning(() -> "--output-name '" + requested + "' is not a plain file name inside "
                    + outputDir + "; using the derived name instead.");
        }
        return outputDir.resolve(deriveOutputName(inputPath, options.sequenceOnly(),
                options.stateMachine(), options.modulesOnly()));
    }

    /**
     * Canvas edge, in pixels, past which a diagram stops being something a person can read.
     * Roughly ten 1440px screens; anything beyond it is a data dump wearing a diagram's clothes.
     */
    static final double OVERSIZE_PX = 15000.0;

    /**
     * Renders one diagram per package rather than one diagram for the whole tree.
     *
     * <p>This is the general answer to the scale problem. A single diagram over a large tree is
     * unreadable long before it is wrong: a stitched call graph over one real package reached 986
     * nodes and roughly 36000x43700 pixels. Bounding depth does not help, because the fan-out that
     * causes it is horizontal — the tree is wide, not deep. Splitting by package does help, because
     * a package is the unit the author already used to group things that belong together, so each
     * resulting diagram answers one question at a size a person can actually look at.
     *
     * <p>Every directory holding at least one {@code .java} file is rendered, and the output mirrors
     * the package structure under {@code outputDir} so the diagram for {@code a.b.c} is easy to
     * find. A package that yields nothing renderable is skipped, not failed: the caller asked for
     * whatever is there, and one empty package should not abort the other forty.
     *
     * @return the diagrams actually written, in directory order
     */
    static java.util.List<Path> runPerPackage(Path inputRoot, Path outputDir,
                                              RunOptions options) throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            log.info(() -> "--split-packages needs a directory; " + inputRoot + " is a file.");
            Path single = run(inputRoot, outputDir, options);
            return single == null ? java.util.List.of() : java.util.List.of(single);
        }

        java.util.List<Path> packageDirs = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(inputRoot)) {
            walk.filter(Files::isDirectory)
                .filter(KartaCli::holdsJavaSource)
                .sorted()
                .forEach(packageDirs::add);
        }

        java.util.List<Path> written = new java.util.ArrayList<>();
        for (Path pkg : packageDirs) {
            Path relative = inputRoot.relativize(pkg);
            Path target = relative.toString().isEmpty() ? outputDir : outputDir.resolve(relative);
            try {
                Path svg = run(pkg, target, options);
                if (svg != null) {
                    written.add(svg);
                }
            } catch (IOException e) {
                // One unreadable package must not cost the caller the other forty.
                log.warning(() -> "Skipped " + pkg + ": " + e.getMessage());
            }
        }
        return written;
    }

    /** True when the directory itself holds at least one {@code .java} file (not counting subdirs). */
    private static boolean holdsJavaSource(Path dir) {
        try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
            return entries.anyMatch(p -> {
                // getFileName() is null only for a filesystem root, which is never a regular file;
                // guard anyway so the predicate is total.
                Path name = p.getFileName();
                return name != null && Files.isRegularFile(p) && name.toString().endsWith(".java");
            });
        } catch (IOException e) {
            return false;
        }
    }

    /** Canvas dimensions as written into the rendered {@code <svg>} root element. */
    private static final java.util.regex.Pattern SVG_CANVAS =
            java.util.regex.Pattern.compile("<svg[^>]*\\bwidth=\"(\\d+)\"\\s+height=\"(\\d+)\"");

    /**
     * Warns when the rendered diagram will not fit any screen.
     *
     * <p>A whole-package stitched call graph can reach a thousand nodes, which renders to tens of
     * thousands of pixels on an edge — one real case measured 36020x43744. The file is still
     * written, because the caller may well be feeding it to something other than an eye, but
     * silence there reads as success. Say the size and name the flags that reduce it.
     *
     * <p>The size is read back from the rendered SVG rather than derived from node extents. A
     * sequence diagram draws lifelines and messages far below its participant boxes, so the node
     * bounding box understates the real canvas several times over: measuring it that way reported
     * 20970x5732 for a diagram that was actually 22600x42988, and a warning that misstates the
     * number it is warning about is worse than none.
     */
    static void warnIfOversized(String svg, Graph graph, Path outputFile) {
        java.util.regex.Matcher matcher = SVG_CANVAS.matcher(svg);
        if (!matcher.find()) {
            return;
        }
        final long w = Long.parseLong(matcher.group(1));
        final long h = Long.parseLong(matcher.group(2));
        if (w > OVERSIZE_PX || h > OVERSIZE_PX) {
            final int nodes = graph.getNodes().size();
            log.warning(() -> "Wrote " + outputFile + " but it is " + w + "x" + h
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
        System.out.println("Usage: karta --input <path> [--output <dir>] [--output-name <file>] [--sequence-only] [--state-machine] [--modules-only] [--split-packages] [--layout simple|elk] [--exclude <patterns>] [--max-depth <depth>] [--max-members <n>]");
        System.out.println();
        System.out.println("  --input  <path>      What to parse:");
        System.out.println("                         module-info.java  → module diagram");
        System.out.println("                         directory         → class diagram");
        System.out.println("                         *.java file       → sequence/exception diagram");
        System.out.println("                         directory + --sequence-only");
        System.out.println("                                           → multi-file stitched sequence diagram");
        System.out.println("                         directory + --modules-only");
        System.out.println("                                           → cross-module communication diagram,");
        System.out.println("                                             from module-info.java if present,");
        System.out.println("                                             otherwise from the Maven <modules> or");
        System.out.println("                                             Gradle include(...) reactor");
        System.out.println("                         file/dir + --state-machine");
        System.out.println("                                           → enum-backed state transition diagram");
        System.out.println("  --output <dir>       Output directory  (default: ./output)");
        System.out.println("  --output-name <file> File name to write inside --output, instead of the name");
        System.out.println("                         derived from the input (class-diagram.svg and friends).");
        System.out.println("                         Lets several runs share one output directory. Must be a");
        System.out.println("                         plain file name — no path separators.");
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
        System.out.println("  --split-packages     Emit one diagram per package instead of one for");
        System.out.println("                         the whole tree, mirroring the package structure");
        System.out.println("                         under --output. Use this when a single diagram");
        System.out.println("                         would be too large to read: a wide tree stays");
        System.out.println("                         wide whatever --max-depth is set to, because");
        System.out.println("                         the fan-out is horizontal, and a package is");
        System.out.println("                         already the author's own grouping of things");
        System.out.println("                         that belong together.");
        System.out.println("  --layout <engine>    Layout algorithm:  simple (default) or elk.");
        System.out.println("                         elk uses the Eclipse Layout Kernel layered");
        System.out.println("                         algorithm for edge-crossing minimisation and");
        System.out.println("                         orthogonal routing (recommended for large graphs).");
        System.out.println("  --exclude <patterns> Comma-separated wildcard patterns of classes or methods");
        System.out.println("                         to exclude (e.g. *Test,se.deversity.codekarta.util.*,Map) to");
        System.out.println("                         reduce diagram clutter under scale.");
        System.out.println("  --max-depth <depth>  Maximum call sequence depth to parse/stitch (integer).");
        System.out.println("  --max-members <n>    Field/method lines per class box before the rest collapse");
        System.out.println("                         into \"…(+N more)\" (default 6). Pass 'all' or 0 to show");
        System.out.println("                         every member — useful for a small package where the");
        System.out.println("                         members are the point.");
        System.out.println("  --help               Show this message");
    }
}
