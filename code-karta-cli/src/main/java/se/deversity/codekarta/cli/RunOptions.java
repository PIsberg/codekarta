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
 * @param format         {@code "svg"} or {@code "json"}. {@code "json"} writes the graph itself
 *                       rather than a picture of it, for a consumer that wants to run its own
 *                       checks over the analysis. Layout still runs, so the JSON carries
 *                       coordinates. Defaults to {@code "svg"} when {@code null}.
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
        String format) {

    /** The default output format, and what {@code format} becomes when it is {@code null}. */
    public static final String FORMAT_SVG = "svg";

    /** Writes the IR as JSON instead of an SVG. See {@code JsonRenderer}. */
    public static final String FORMAT_JSON = "json";

    public RunOptions {
        layout = layout == null ? "simple" : layout;
        customExcludes = customExcludes == null ? Collections.emptySet() : Set.copyOf(customExcludes);
        format = format == null ? FORMAT_SVG : format;
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
                outputName, maxMembers, FORMAT_SVG);
    }

    /** @return true when this run should write JSON rather than SVG */
    public boolean isJson() {
        return FORMAT_JSON.equalsIgnoreCase(format);
    }

    /** The pipeline as it runs with no flags at all: class diagram, simple layout, capped members. */
    public static RunOptions defaults() {
        return new RunOptions(false, "simple", false, Collections.emptySet(),
                Integer.MAX_VALUE, false, null, ClassDiagramParser.DEFAULT_MAX_MEMBERS, FORMAT_SVG);
    }

    public RunOptions withOutputName(String name) {
        return new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, name, maxMembers, format);
    }

    /**
     * @param newFormat {@link #FORMAT_SVG} or {@link #FORMAT_JSON}
     * @return a copy of these options with the format replaced
     */
    public RunOptions withFormat(String newFormat) {
        return new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, outputName, maxMembers, newFormat);
    }
}
