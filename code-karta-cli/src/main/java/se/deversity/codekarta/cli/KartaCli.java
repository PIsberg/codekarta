package se.deversity.codekarta.cli;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.JavaSourceInputParser;
import se.deversity.codekarta.input.MultiFileSequenceParser;
import se.deversity.codekarta.input.parser.StateMachineParser;
import se.deversity.codekarta.layout.ElkLayoutEngine;
import se.deversity.codekarta.layout.LayoutEngine;
import se.deversity.codekarta.layout.SimpleLayoutEngine;
import se.deversity.codekarta.render.JsonRenderer;
import se.deversity.codekarta.render.SvgRenderer;

import org.jspecify.annotations.Nullable;
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

    /** What one command line asks for. {@code help} means print the usage and stop. */
    /** A parsed command line. {@code inputPath} is null for help, version, and a viewer-only run. */
    record Invocation(Path inputPath, Path outputDir, boolean splitPackages, boolean help, RunOptions options,
                      boolean viewer, boolean version) {}

    /**
     * Reads a command line. Anything it does not understand is an error rather than a default:
     * an unknown flag, a flag that needs a value and has none (the next argument is another
     * flag, or there is none), and a value outside what the flag accepts. Lenient parsing turned
     * a misspelt {@code --sequnce-only} into a class diagram and {@code --layout elkk} into the
     * simple layout, with exit code 0 (issue #231).
     *
     * @throws IllegalArgumentException naming the offending argument
     */
    @SuppressWarnings("PMD.AvoidReassigningLoopVariables") // args[++i] is the standard flag-value idiom
    static Invocation parseArgs(String... args) {
        Path inputPath = null;
        Path outputDir = DEFAULT_OUTPUT;
        boolean sequenceOnly = false;
        boolean stateMachine = false;
        boolean modulesOnly = false;
        boolean splitPackages = false;
        boolean includeTests = false;
        boolean viewer = false;
        String layout = RunOptions.LAYOUT_SIMPLE;
        String outputName = null;
        String format = RunOptions.FORMAT_SVG;
        java.util.Set<String> customExcludes = java.util.Collections.emptySet();
        int maxDepth = Integer.MAX_VALUE;
        int maxMembers = se.deversity.codekarta.input.parser.ClassDiagramParser.DEFAULT_MAX_MEMBERS;

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            switch (flag) {
                case "--input"         -> inputPath = Path.of(valueOf(args, ++i, flag));
                case "--output"        -> outputDir = Path.of(valueOf(args, ++i, flag));
                case "--output-name"   -> outputName = valueOf(args, ++i, flag);
                case "--layout"        -> layout = valueOf(args, ++i, flag);
                case "--format"        -> format = valueOf(args, ++i, flag);
                case "--sequence-only" -> sequenceOnly = true;
                case "--state-machine" -> stateMachine = true;
                case "--modules-only"  -> modulesOnly = true;
                case "--split-packages" -> splitPackages = true;
                case "--include-tests" -> includeTests = true;
                case "--viewer"        -> viewer = true;
                case "--exclude"       -> customExcludes = java.util.Arrays.stream(valueOf(args, ++i, flag).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toSet());
                case "--max-depth"     -> maxDepth = intValueOf(valueOf(args, ++i, flag), flag, "an integer");
                case "--max-members"   -> {
                    String raw = valueOf(args, ++i, flag);
                    maxMembers = "all".equalsIgnoreCase(raw)
                            ? se.deversity.codekarta.input.parser.ClassDiagramParser.UNLIMITED_MEMBERS
                            : intValueOf(raw, flag, "an integer or 'all'");
                }
                case "--help", "-h"    -> {
                    return new Invocation(null, outputDir, false, true, RunOptions.defaults(), false, false);
                }
                case "--version", "-V" -> {
                    return new Invocation(null, outputDir, false, false, RunOptions.defaults(), false, true);
                }
                default -> throw new IllegalArgumentException("unknown argument '" + flag + "'.");
            }
        }

        if (inputPath == null && !viewer) {
            throw new IllegalArgumentException("--input is required.");
        }
        if (splitPackages && modulesOnly) {
            throw new IllegalArgumentException("--split-packages cannot be combined with --modules-only.");
        }
        // RunOptions checks layout and format, so the CLI, the Maven plugin and library callers
        // all reject the same values with the same message.
        return new Invocation(inputPath, outputDir, splitPackages, false,
                RunOptions.builder()
                        .sequenceOnly(sequenceOnly).layout(layout).stateMachine(stateMachine)
                        .customExcludes(customExcludes).maxDepth(maxDepth).modulesOnly(modulesOnly)
                        .outputName(outputName).maxMembers(maxMembers).format(format)
                        .includeTests(includeTests)
                        .build(), viewer, false);
    }

    /** The value after {@code flag}; a missing one, or another flag in its place, is an error. */
    private static String valueOf(String[] args, int index, String flag) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(flag + " needs a value.");
        }
        return args[index];
    }

    private static int intValueOf(String raw, String flag, String expected) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " must be " + expected + ", got '" + raw + "'.", e);
        }
    }

    public static void main(String[] args) {
        int status = execute(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * The command line, minus the JVM exit: parses {@code args}, runs, and reports on
     * {@code out} and {@code err}. Split out of {@link #main} so the exit status and console
     * output can be tested without ending the test JVM.
     *
     * @return 0 on success (a declined diagram included), 1 for arguments it does not
     *         understand, 2 when reading or writing fails
     */
    static int execute(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if (args.length > 0 && "--batch".equals(args[0])) {
            return batch(args, out, err);
        }
        Invocation call;
        try {
            call = parseArgs(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            printUsage(out);
            return 1;
        }
        if (call.help()) {
            printUsage(out);
            return 0;
        }
        if (call.version()) {
            out.println("code-karta " + version());
            return 0;
        }
        Path inputPath = call.inputPath();
        Path outputDir = call.outputDir();
        RunOptions options = call.options();

        try {
            if (inputPath != null) {
                generate(inputPath, outputDir, options, call.splitPackages(), out);
            }
            if (call.viewer()) {
                Path viewer = writeViewer(outputDir);
                if (viewer == null) {
                    out.println("Skipped: no .svg diagrams under " + outputDir + " to build a viewer from.");
                } else {
                    out.println("Generated viewer: " + viewer.toAbsolutePath());
                }
            }
            return 0;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    /**
     * {@code --batch <file>}: runs each line of {@code file} as one command line, in order, in this
     * JVM, and stops at the first that fails. Blank lines and lines starting with {@code #} are
     * skipped; arguments are separated by whitespace, so a path with a space in it cannot appear.
     *
     * <p>The repository's own diagrams are regenerated from one such file by both builds, which
     * used to keep two hand-copied lists of thirteen runs that drifted apart (#249) and started a
     * JVM for each (issue #279).
     *
     * @return 0 when every line succeeds; otherwise the failing line's status, with the line named
     */
    static int batch(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if (args.length != 2 || args[1].startsWith("--")) {
            err.println("Error: --batch takes exactly one argument, the file of command lines, and nothing else.");
            printUsage(out);
            return 1;
        }
        java.util.List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(args[1]));
        } catch (IOException | java.nio.file.InvalidPathException e) {
            err.println("Error: cannot read the batch file " + args[1] + ": " + e.getMessage());
            return 2;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] lineArgs = line.split("\\s+");
            if (java.util.Arrays.asList(lineArgs).contains("--batch")) {
                err.println("Error: " + args[1] + " line " + (i + 1) + ": --batch cannot be nested.");
                return 1;
            }
            java.io.ByteArrayOutputStream lineErr = new java.io.ByteArrayOutputStream();
            int status = execute(lineArgs, out, new java.io.PrintStream(lineErr, true, java.nio.charset.StandardCharsets.UTF_8));
            if (status != 0) {
                err.println("Error: " + args[1] + " line " + (i + 1) + " failed with status " + status + ": " + line);
                err.print(lineErr.toString(java.nio.charset.StandardCharsets.UTF_8));
                return status;
            }
            err.print(lineErr.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
        return 0;
    }

    private static void generate(Path inputPath, Path outputDir, RunOptions options, boolean splitPackages,
                                 java.io.PrintStream out) throws IOException {
        if (splitPackages) {
            java.util.List<Path> written = runPerPackage(inputPath, outputDir, options);
            if (written.isEmpty()) {
                out.println("Skipped: no package under " + inputPath + " produced a diagram.");
            } else {
                for (Path p : written) {
                    out.println("Generated: " + p.toAbsolutePath());
                }
                out.println("Generated " + written.size() + " diagrams under "
                        + outputDir.toAbsolutePath());
            }
            return;
        }
        Path output = run(inputPath, outputDir, options);
        if (output != null) {
            out.println("Generated: " + output.toAbsolutePath());
        } else {
            out.println("Skipped generating a diagram; see the log message above for why.");
        }
    }

    /**
     * The version this build was made from, as {@code --version} prints it: filled into
     * {@code version.properties} by the Maven and Gradle builds alike, so it is right in the fat
     * jar, the plain jar and a run from {@code target/classes}. {@code "unknown"} if the file is
     * missing or was never filtered.
     */
    public static String version() {
        try (java.io.InputStream in = KartaCli.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return "unknown";
            }
            java.util.Properties properties = new java.util.Properties();
            properties.load(in);
            String value = properties.getProperty("version", "").trim();
            return value.isEmpty() || value.contains("${") ? "unknown" : value;
        } catch (IOException e) {
            return "unknown";
        }
    }

    /** The viewer's file name, written into the output directory it indexes. */
    public static final String VIEWER_FILE = "viewer.html";

    /**
     * Writes {@value #VIEWER_FILE} into {@code outputDir}: one self-contained page browsing every
     * {@code .svg} under it, subdirectories included, listed by relative path in sorted order.
     *
     * <p>Only regular files inside {@code outputDir} are read. The walk does not follow links, and
     * an SVG that is itself a link is skipped, so a page meant to be shared cannot pick up the
     * content of a file somewhere else. The page is written like a diagram, never through a link.
     *
     * @return the viewer, or {@code null} when there is no diagram to show
     */
    public static Path writeViewer(Path outputDir) throws IOException {
        if (!Files.isDirectory(outputDir)) {
            return null;
        }
        java.util.List<Path> svgs;
        try (java.util.stream.Stream<Path> walk = Files.walk(outputDir)) {
            svgs = walk.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> String.valueOf(p.getFileName()).endsWith(".svg"))
                    .sorted(java.util.Comparator.comparing(p -> slashed(outputDir.relativize(p))))
                    .toList();
        }
        if (svgs.isEmpty()) {
            return null;
        }
        java.util.List<se.deversity.codekarta.render.HtmlViewerRenderer.Diagram> diagrams = new java.util.ArrayList<>();
        for (Path svg : svgs) {
            String title = slashed(outputDir.relativize(svg));
            diagrams.add(new se.deversity.codekarta.render.HtmlViewerRenderer.Diagram(
                    title.substring(0, title.length() - ".svg".length()), Files.readString(svg)));
        }
        Path viewer = outputDir.resolve(VIEWER_FILE);
        writeInsideOutputDir(viewer, new se.deversity.codekarta.render.HtmlViewerRenderer().render(diagrams));
        return viewer;
    }

    /** A relative path with forward slashes on every platform, so titles and order do not depend on the OS. */
    private static String slashed(Path relative) {
        return relative.toString().replace('\\', '/');
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
     * <p>Every other public {@code run} overload delegates here.
     *
     * @return the path of the written SVG file, or {@code null} when there was nothing worth
     *         drawing (the reason is logged)
     */
    public static Path run(Path inputPath, Path outputDir, RunOptions options) throws IOException {
        return run(inputPath, outputDir, options, null);
    }

    /**
     * {@link #run(Path, Path, RunOptions)} with the parse step already done: {@code parsed} is
     * laid out, rendered and written as if {@code inputPath} had produced it, or, when
     * {@code null}, {@code inputPath} is parsed as usual. {@link #runPerPackage} parses a tree
     * once and hands each package's graph in here.
     *
     * <p>This is the one implementation; every other {@code run} overload delegates here.
     */
    @SuppressWarnings({"PMD.UnusedAssignment", "UnusedVariable"}) // 'state' assignments are extracted by StateMachineParser as the pipeline diagram
    static Path run(Path inputPath, Path outputDir, RunOptions options, @Nullable Graph parsed)
            throws IOException {
        Files.createDirectories(outputDir);

        PipelineStage state = PipelineStage.PARSING;
        Graph graph;
        if (parsed != null) {
            graph = parsed;
        } else if (options.stateMachine()) {
            log.fine(() -> "State-machine mode for input: " + inputPath);
            graph = new StateMachineParser(options.includeTests(), options.customExcludes()).parse(inputPath);
        } else if (options.modulesOnly() && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-module mode for directory: " + inputPath);
            graph = parseModules(inputPath, options.includeTests(), options.customExcludes());
        } else if (options.sequenceOnly() && Files.isDirectory(inputPath)) {
            log.fine(() -> "Multi-file sequence mode for directory: " + inputPath);
            graph = new MultiFileSequenceParser(options.customExcludes(), options.maxDepth(),
                    options.includeTests()).parse(inputPath);
        } else {
            graph = new JavaSourceInputParser(options.sequenceOnly(), options.customExcludes(),
                    options.maxMembers(), options.includeTests()).parse(inputPath);
        }

        state = PipelineStage.LAYOUT;
        // A sequence SVG computes its own geometry and ignores node coordinates, so laying it out
        // (ELK layered layout, with --layout elk) was work thrown away. JSON documents the
        // coordinates, so it is still laid out (issue #272).
        // Mermaid and DOT carry no coordinates at all; their own tools lay them out (issue #275).
        if (!options.isText() && (options.isJson() || !new SvgRenderer().isInteractionGraph(graph))) {
            resolveLayout(options.layout()).layout(graph);
        }

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
        // Layout has already run either way, so the JSON carries coordinates. That is deliberate:
        // a consumer feeding this into its own renderer wants them, and one running architecture
        // checks over the graph can ignore them.
        String rendered = switch (options.format()) {
            case RunOptions.FORMAT_JSON -> new JsonRenderer().render(graph);
            case RunOptions.FORMAT_MERMAID -> new se.deversity.codekarta.render.MermaidRenderer().render(graph);
            case RunOptions.FORMAT_DOT -> new se.deversity.codekarta.render.DotRenderer().render(graph);
            default -> new SvgRenderer().render(graph);
        };

        state = PipelineStage.WRITING;
        Path outputFile = resolveOutputFile(inputPath, outputDir, options);
        writeInsideOutputDir(outputFile, rendered);

        if (RunOptions.FORMAT_SVG.equals(options.format())) {
            warnIfOversized(rendered, graph, outputFile);
        }

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
        return parseModules(inputPath, false);
    }

    static Graph parseModules(Path inputPath, boolean includeTests) {
        return parseModules(inputPath, includeTests, java.util.Set.of());
    }

    static Graph parseModules(Path inputPath, boolean includeTests, java.util.Set<String> customExcludes) {
        Graph jpms = new se.deversity.codekarta.input.parser.ModuleInfoParser(customExcludes)
                .parseDirectory(inputPath, includeTests);
        if (!jpms.getNodes().isEmpty()) {
            return jpms;
        }
        log.fine(() -> "No module-info.java under " + inputPath + "; reading the build reactor instead.");
        return new se.deversity.codekarta.input.parser.BuildReactorParser(customExcludes).parse(inputPath);
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
            // Judge the name on its own rather than by comparing parents: with --output "."
            // the resolved candidate normalises to a bare "alpha.svg" with no parent at all,
            // and a parent-equals-output check then dropped a perfectly plain name.
            if (isBareFileName(requested)) {
                return outputDir.resolve(requested);
            }
            log.warning(() -> "--output-name '" + requested + "' is not a plain file name inside "
                    + outputDir + "; using the derived name instead.");
        }
        return outputDir.resolve(withFormatExtension(
                deriveOutputName(inputPath, options.sequenceOnly(),
                        options.stateMachine(), options.modulesOnly()),
                options));
    }

    /** One path element, no root or drive, and nothing that walks: {@code .}, {@code ..}. */
    private static boolean isBareFileName(String name) {
        if (name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return false;
        }
        try {
            Path path = Path.of(name);
            return path.getNameCount() == 1 && path.getRoot() == null && !".".equals(name);
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    /**
     * Writes the diagram, refusing to go through a symbolic link.
     *
     * <p>The name is already a bare file name inside {@code outputDir}, so the one remaining way
     * out is a link that is already sitting there under that name: following it would overwrite,
     * or for a dangling link create, a file wherever it points. A diagram has no reason to be
     * written through a link, so refuse rather than resolve it. {@code NOFOLLOW_LINKS} closes the
     * window between the check and the open on platforms that support it.
     */
    private static void writeInsideOutputDir(Path outputFile, String content) throws IOException {
        if (Files.isSymbolicLink(outputFile)) {
            throw new IOException("Refusing to write " + outputFile
                    + ": it is a symbolic link, and following it could write outside the output directory.");
        }
        Files.writeString(outputFile, content, java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Swaps the derived name's extension for the requested format.
     *
     * <p>Applied only to derived names. An explicit {@code --output-name} is taken literally,
     * because a caller who names the file has already decided what to call it.
     */
    static String withFormatExtension(String derivedName, RunOptions options) {
        String extension = switch (options.format()) {
            case RunOptions.FORMAT_JSON -> ".json";
            case RunOptions.FORMAT_MERMAID -> ".mmd";
            case RunOptions.FORMAT_DOT -> ".dot";
            default -> null;
        };
        if (extension == null) {
            return derivedName;
        }
        return derivedName.endsWith(".svg")
                ? derivedName.substring(0, derivedName.length() - 4) + extension
                : derivedName + extension;
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
     * <p>Every directory holding at least one {@code .java} file is rendered, skipping the same
     * test source sets, build output and VCS directories a single run skips, and the output mirrors
     * the package structure under {@code outputDir} so the diagram for {@code a.b.c} is easy to
     * find. Each diagram draws the types (or, with {@code sequenceOnly}, the methods) declared
     * directly in its directory, not those of its subpackages; the tree is parsed once, so a
     * reference into another package still resolves, to a stub node or a cross-package call.
     * With {@code stateMachine}, each directory draws the transitions found in its own files and
     * the enums it declares. {@code modulesOnly} cannot be split: it is rejected with
     * {@link IllegalArgumentException}. A package that yields nothing renderable is skipped, not failed: the caller asked for
     * whatever is there, and one empty package should not abort the other forty.
     *
     * <p>Public because the Maven plugin drives it. Everything the CLI can do from a flag, an
     * embedding caller can do from Java; a capability reachable only through main(String[]) is one
     * that has to be shelled out to.
     *
     * @param inputRoot  the source root to walk. A file rather than a directory falls back to a
     *                   single {@link #run} and is logged
     * @param outputDir  root of the mirrored output tree, created as needed
     * @param options    the same options {@code run} takes; {@code outputName} is ignored, because
     *                   several diagrams cannot share one name
     * @return the diagrams actually written, in directory order; empty if none was
     * @throws IOException if the output tree cannot be written
     */
    public static java.util.List<Path> runPerPackage(Path inputRoot, Path outputDir,
                                              RunOptions options) throws IOException {
        if (options.modulesOnly()) {
            // Modules are not per package: a module-info.java describes a whole source set.
            throw new IllegalArgumentException("--split-packages cannot be combined with --modules-only.");
        }
        if (!Files.isDirectory(inputRoot)) {
            log.info(() -> "--split-packages needs a directory; " + inputRoot + " is a file.");
            Path single = run(inputRoot, outputDir, options);
            return single == null ? java.util.List.of() : java.util.List.of(single);
        }

        // Each package drawn from its own files, the tree parsed once so references into other
        // packages still resolve (to stub nodes, cross-package calls, or enums).
        java.util.Map<Path, Graph> graphs = options.stateMachine()
                ? new StateMachineParser(options.includeTests(), options.customExcludes()).parsePerDirectory(inputRoot)
                : options.sequenceOnly()
                ? new MultiFileSequenceParser(options.customExcludes(), options.maxDepth(),
                        options.includeTests()).parsePerDirectory(inputRoot)
                : new se.deversity.codekarta.input.parser.ClassDiagramParser(options.customExcludes(),
                        options.maxMembers(), options.includeTests()).parsePerDirectory(inputRoot);
        java.util.List<Path> written = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Path, Graph> entry : graphs.entrySet()) {
            writePackage(inputRoot, outputDir, options, entry.getKey(), entry.getValue(), written);
        }
        return written;
    }

    /** Writes one package's already-parsed diagram under the mirrored output path. */
    private static void writePackage(Path inputRoot, Path outputDir, RunOptions options, Path pkg,
                                     Graph graph, java.util.List<Path> written) {
        Path relative = inputRoot.relativize(pkg);
        Path target = relative.toString().isEmpty() ? outputDir : outputDir.resolve(relative);
        try {
            Path file = run(pkg, target, options, graph);
            if (file != null) {
                written.add(file);
            }
        } catch (IOException e) {
            // One unreadable package must not cost the caller the other forty.
            log.warning(() -> "Skipped " + pkg + ": " + e.getMessage());
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
        return RunOptions.LAYOUT_ELK.equals(name) ? new ElkLayoutEngine() : new SimpleLayoutEngine();
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

    private static void printUsage(java.io.PrintStream out) {
        out.println("Usage: karta --input <path> [--output <dir>] [--output-name <file>] [--format svg|json|mermaid|dot] [--sequence-only] [--state-machine] [--modules-only] [--split-packages] [--include-tests] [--viewer] [--layout simple|elk] [--exclude <patterns>] [--max-depth <depth>] [--max-members <n>] [--version]");
        out.println("       karta --batch <file>");
        out.println();
        out.println("  --input  <path>      What to parse:");
        out.println("                         module-info.java  → module diagram");
        out.println("                         directory         → class diagram; a project or");
        out.println("                                             repository root works too");
        out.println("                         *.java file       → sequence/exception diagram");
        out.println("                         directory + --sequence-only");
        out.println("                                           → multi-file stitched sequence diagram");
        out.println("                         directory + --modules-only");
        out.println("                                           → cross-module communication diagram,");
        out.println("                                             from module-info.java if present,");
        out.println("                                             otherwise from the Maven <modules> or");
        out.println("                                             Gradle include(...) reactor");
        out.println("                         file/dir + --state-machine");
        out.println("                                           → enum-backed state transition diagram");
        out.println("  --output <dir>       Output directory  (default: ./output)");
        out.println("  --output-name <file> File name to write inside --output, instead of the name");
        out.println("                         derived from the input (class-diagram.svg and friends).");
        out.println("                         Lets several runs share one output directory. Must be a");
        out.println("                         plain file name — no path separators.");
        out.println("  --format <fmt>       svg (default), json, mermaid or dot.");
        out.println("                         json writes the parsed graph itself instead of a");
        out.println("                         picture of it: nodes, edges, groups and layout");
        out.println("                         coordinates, for feeding into your own tooling,");
        out.println("                         architecture checks or renderer. The derived file");
        out.println("                         name changes to .json; --output-name is used as given.");
        out.println("                         mermaid writes a Mermaid diagram (.mmd) to paste into");
        out.println("                         Markdown; dot writes Graphviz DOT (.dot). Both are text,");
        out.println("                         unlaid-out: Mermaid or Graphviz does the layout.");
        out.println("  --sequence-only      Emit only CALLS edges (no exception-flow).");
        out.println("                         When combined with a directory input, parses");
        out.println("                         all .java files together using cross-file");
        out.println("                         symbol resolution to stitch call graphs.");
        out.println("                         Tip: point --input at the source package root");
        out.println("                         (e.g. src/main/java) so the symbol solver can");
        out.println("                         resolve cross-package references correctly.");
        out.println("  --state-machine      Emit STATE nodes and TRANSITION edges from enum");
        out.println("                         constants, switch cases, state assignments, and");
        out.println("                         transition(from, to, event) calls.");
        out.println("  --split-packages     Emit one diagram per package instead of one for");
        out.println("                         the whole tree, mirroring the package structure");
        out.println("                         under --output. Use this when a single diagram");
        out.println("                         would be too large to read: a wide tree stays");
        out.println("                         wide whatever --max-depth is set to, because");
        out.println("                         the fan-out is horizontal, and a package is");
        out.println("                         already the author's own grouping of things");
        out.println("                         that belong together.");
        out.println("  --include-tests      Also read test source sets (src/test, src/integrationTest,");
        out.println("                         src/testFixtures, src/it, ...) when --input is a");
        out.println("                         project or repository root. Off by default, so a");
        out.println("                         checkout draws the code that ships. target/, build/,");
        out.println("                         .git and node_modules are skipped either way.");
        out.println("  --viewer             Also write viewer.html into --output: one self-contained");
        out.println("                         page browsing every .svg under it, with search, edge");
        out.println("                         toggles, zoom and pan. Works opened from disk, offline.");
        out.println("                         Without --input, rebuilds it from what --output holds.");
        out.println("  --layout <engine>    Layout algorithm:  simple (default) or elk.");
        out.println("                         elk uses the Eclipse Layout Kernel layered");
        out.println("                         algorithm for edge-crossing minimisation and");
        out.println("                         orthogonal routing (recommended for large graphs).");
        out.println("  --exclude <patterns> Comma-separated wildcard patterns of classes, methods,");
        out.println("                         enums (--state-machine) or modules (--modules-only) to");
        out.println("                         exclude (e.g. *Test,se.deversity.codekarta.util.*,Order*Service,Map).");
        out.println("                         A simple or qualified name works; * matches anywhere.");
        out.println("  --max-depth <depth>  Maximum call sequence depth to parse/stitch (integer).");
        out.println("  --max-members <n>    Field/method lines per class box before the rest collapse");
        out.println("                         into \"…(+N more)\" (default 6). Pass 'all' or 0 to show");
        out.println("                         every member — useful for a small package where the");
        out.println("                         members are the point.");
        out.println("  --batch <file>       Run every line of <file> as one command line, in order,");
        out.println("                         in one JVM; stop at the first that fails. Blank lines");
        out.println("                         and # comments are skipped. Use it alone.");
        out.println("  --version, -V        Print the version and exit.");
        out.println("  --help               Show this message");
    }
}
