package se.deversity.codekarta.cli;

import se.deversity.codekarta.input.parser.ClassDiagramParser;

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
 */
public record RunOptions(
        boolean sequenceOnly,
        String layout,
        boolean stateMachine,
        Set<String> customExcludes,
        int maxDepth,
        boolean modulesOnly,
        String outputName,
        int maxMembers) {

    public RunOptions {
        layout = layout == null ? "simple" : layout;
        customExcludes = customExcludes == null ? Collections.emptySet() : Set.copyOf(customExcludes);
    }

    /** The pipeline as it runs with no flags at all: class diagram, simple layout, capped members. */
    public static RunOptions defaults() {
        return new RunOptions(false, "simple", false, Collections.emptySet(),
                Integer.MAX_VALUE, false, null, ClassDiagramParser.DEFAULT_MAX_MEMBERS);
    }

    public RunOptions withOutputName(String name) {
        return new RunOptions(sequenceOnly, layout, stateMachine, customExcludes,
                maxDepth, modulesOnly, name, maxMembers);
    }
}
