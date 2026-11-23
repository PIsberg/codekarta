package se.deversity.codekarta.cli;

import se.deversity.codekarta.input.parser.ClassDiagramParser;
import se.deversity.vibetags.annotations.AIImmutable;
import se.deversity.vibetags.annotations.AIKeepInSync;

import java.util.Collections;
import java.util.Set;

/**
 * Everything {@link KartaCli#run(java.nio.file.Path, java.nio.file.Path, RunOptions)} needs
 * besides the two paths.
 *
 * <p>These used to be positional parameters, one overload per flag. That works up to about
 * six; past that the call sites become unreadable and adding a flag means adding an overload
 * to every caller. The older overloads stay — they are a published contract — and delegate
 * here.
 *
 * @param sequenceOnly   emit CALLS edges only, no exception flow
 * @param layout         {@code "elk"} or {@code "simple"}
 * @param stateMachine   extract STATE nodes and TRANSITION edges
 * @param customExcludes wildcard patterns of types/methods to drop
 * @param maxDepth       maximum call-chain depth to stitch
 * @param modulesOnly    render the cross-module graph rather than the class graph
 * @param outputName     file name to write, or {@code null} to derive one from the input.
 *                       A bare file name: anything that would land outside the output
 *                       directory is rejected rather than followed.
 * @param maxMembers     compartment lines kept per class before "…(+N more)";
 *                       {@link ClassDiagramParser#UNLIMITED_MEMBERS} keeps all of them
 * @param format         {@code "svg"}, {@code "json"}, {@code "mermaid"} or {@code "dot"}.
 *                       {@code "json"} writes the graph itself rather than a picture of it, for a
 *                       consumer that wants to run its own checks over the analysis; layout still
 *                       runs, so the JSON carries coordinates. {@code "mermaid"} and {@code "dot"}
 *                       write diagram source text for Markdown and Graphviz, without layout.
 *                       Defaults to {@code "svg"} when {@code null}.
 * @param includeTests   read test source sets ({@code src/test}, {@code src/integrationTest},
 *                       ...) when the input is a project or repository root. Off by default, so
 *                       pointing the CLI at a checkout draws the code that ships. Build output,
 *                       VCS metadata and {@code node_modules} are skipped either way; see
 *                       {@code SourceTree} in the input tier.
 */
@AIImmutable(note = "The compact constructor defensively copies customExcludes and defaults layout; withOutputName returns a new instance rather than mutating. Callers pass the same RunOptions down the whole pipeline, so a mutator here would let one stage change another stage's inputs.")
@AIKeepInSync(
    mirrors = {"KartaCli.printUsage()", "docs/CLI.md flag table", "docs/SKILL.md flag list"},
    reason = "Every component here is a user-facing CLI flag. Adding or renaming one without updating the usage text and the docs tables leaves the flag undiscoverable — nothing in the build catches the gap.")
public record RunOptions(
        boolean sequenceOnly,
        String layout,
        boolean stateMachine,
        Set<String> customExcludes,
        int maxDepth,
        boolean modulesOnly,
        String outputName,
        int maxMembers,
        String format,
        boolean includeTests) {

    /** The default output format, and what {@code format} becomes when it is {@code null}. */
    public static final String FORMAT_SVG = "svg";

    /** Writes the IR as JSON instead of an SVG. See {@code JsonRenderer}. */
    public static final String FORMAT_JSON = "json";

    /** Writes Mermaid diagram text for Markdown. See {@code MermaidRenderer}. */
    public static final String FORMAT_MERMAID = "mermaid";

    /** Writes Graphviz DOT. See {@code DotRenderer}. */
    public static final String FORMAT_DOT = "dot";

    /** The default layout: the pure-Java BFS grid. */
    public static final String LAYOUT_SIMPLE = "simple";

    /** The Eclipse Layout Kernel layered layout. */
    public static final String LAYOUT_ELK = "elk";

    /**
     * Defaults {@code layout} and {@code format} when {@code null}, stores them lower-case, and
     * rejects any other value. An unknown layout used to become the simple layout and an unknown
     * format SVG, without a word (issue #231); checking here covers the CLI, the Maven plugin and
     * library callers alike.
     *
     * @throws IllegalArgumentException for a layout other than {@code simple}/{@code elk} or a
     *                                  format other than {@code svg}/{@code json}/{@code mermaid}/{@code dot}
     */
    public RunOptions {
        layout = oneOf("layout", layout == null ? LAYOUT_SIMPLE : layout, LAYOUT_SIMPLE, LAYOUT_ELK);
        customExcludes = customExcludes == null ? Collections.emptySet() : Set.copyOf(customExcludes);
        format = oneOf("format", format == null ? FORMAT_SVG : format,
                FORMAT_SVG, FORMAT_JSON, FORMAT_MERMAID, FORMAT_DOT);
    }

    private static String oneOf(String name, String value, String... allowed) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (!java.util.List.of(allowed).contains(lower)) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < allowed.length; i++) {
                names.append(i == 0 ? "" : i == allowed.length - 1 ? " or " : ", ")
                     .append('\'').append(allowed[i]).append('\'');
            }
            throw new IllegalArgumentException(name + " must be " + names + ", got '" + value + "'.");
        }
        return lower;
    }

    /**
     * Kept so the pre-0.4 eight-component constructor still compiles. Defaults the format to SVG.
     *
     * @deprecated since 0.4.0, use the canonical constructor and pass a format.
     */
    @Deprecated(since = "0.4.0", forRemoval = false)
    public RunOptions(boolean sequenceOnly, String layout, boolean stateMachine,
                      Set<String> customExcludes, int maxDepth, boolean modulesOnly,
                      String outputName, int maxMembers) {
        this(sequenceOnly, layout, stateMachine, customExcludes, maxDepth, modulesOnly,
                outputName, maxMembers, FORMAT_SVG, false);
    }

    /**
     * Kept so the 0.4 nine-component constructor still compiles. Reads production sources only.
     *
     * @deprecated since 0.5.0, use the canonical constructor and pass includeTests.
     */
    @Deprecated(since = "0.5.0", forRemoval = false)
    public RunOptions(boolean sequenceOnly, String layout, boolean stateMachine,
                      Set<String> customExcludes, int maxDepth, boolean modulesOnly,
                      String outputName, int maxMembers, String format) {
        this(sequenceOnly, layout, stateMachine, customExcludes, maxDepth, modulesOnly,
                outputName, maxMembers, format, false);
    }

    /** @return true when this run should write JSON rather than SVG */
    public boolean isJson() {
        return FORMAT_JSON.equalsIgnoreCase(format);
    }

    /** @return true for the diagram-source formats, Mermaid and DOT, which carry no coordinates */
    public boolean isText() {
        return FORMAT_MERMAID.equals(format) || FORMAT_DOT.equals(format);
    }

    /**
     * The pipeline as it runs with no flags at all: class diagram, simple layout, capped members,
     * production sources only.
     */
    public static RunOptions defaults() {
        return new RunOptions(false, LAYOUT_SIMPLE, false, Collections.emptySet(),
                Integer.MAX_VALUE, false, null, ClassDiagramParser.DEFAULT_MAX_MEMBERS, FORMAT_SVG,
                false);
    }

    public RunOptions withOutputName(String name) {
        return new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, name, maxMembers, format, includeTests);
    }

    /**
     * @param newFormat {@link #FORMAT_SVG}, {@link #FORMAT_JSON}, {@link #FORMAT_MERMAID} or {@link #FORMAT_DOT}
     * @return a copy of these options with the format replaced
     */
    public RunOptions withFormat(String newFormat) {
        return new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, outputName, maxMembers, newFormat, includeTests);
    }
}
