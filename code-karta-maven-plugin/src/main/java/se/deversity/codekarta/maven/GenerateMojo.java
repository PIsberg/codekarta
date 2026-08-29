package se.deversity.codekarta.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import se.deversity.codekarta.cli.KartaCli;
import se.deversity.codekarta.cli.RunOptions;
import se.deversity.vibetags.annotations.AIAudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates code-karta diagrams during a Maven build.
 *
 * <p>With no configuration at all it draws a class diagram of the source directory of the project
 * being built, into {@code target/code-karta}. Declare the plugin, bind the {@code generate} goal,
 * and that is the whole setup. Several diagrams from one execution are configured with a
 * {@code diagrams} list, where each entry overrides only the goal-level values it cares about.
 *
 * <p>Bound to {@code package} by default rather than {@code compile}, so it runs after the module
 * has been shown to build and stays out of the inner loop.
 *
 * <p>Every parameter mirrors a CLI flag of the same name, so {@code docs/CLI.md} is the reference
 * for both. See {@code docs/MAVEN-PLUGIN.md} for worked configurations.
 */
// outputDirectory and every outputName arrive from a pom this plugin did not write, so both are
// caller-supplied paths in someone else build. KartaCli.resolveOutputFile stays the single place
// that decides where bytes land, and it refuses any name that would leave the output directory.
// Do not construct an output Path here.
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true,
        requiresProject = true)
@AIAudit(checkFor = {"Path traversal", "Unauthorized file write"})
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Where diagrams are written. Created if missing.
     *
     * <p>Defaults under {@code target/} so that a build does not write into the source tree
     * unasked. A project that commits its diagrams points this at {@code docs/diagrams} instead:
     * output is byte-identical for identical input, so a committed diagram changes only when the
     * code does.
     */
    @Parameter(property = "karta.outputDirectory",
            defaultValue = "${project.build.directory}/code-karta")
    private File outputDirectory;

    /**
     * What to parse. Defaults to the source directory of the project being built.
     *
     * <p>A file, a directory, or a {@code module-info.java}. The shape of the path selects the
     * kind of diagram, exactly as it does on the command line.
     */
    @Parameter(property = "karta.input", defaultValue = "${project.build.sourceDirectory}")
    private File input;

    /** {@code svg} (default) or {@code json}. */
    @Parameter(property = "karta.format", defaultValue = RunOptions.FORMAT_SVG)
    private String format;

    /** {@code simple} (default) or {@code elk}. {@code elk} needs a Java 21 runtime. */
    @Parameter(property = "karta.layout", defaultValue = "simple")
    private String layout;

    /** File name to write, instead of one derived from the input. A plain name, no separators. */
    @Parameter(property = "karta.outputName")
    private String outputName;

    /** Emit only CALLS edges, no exception flow. */
    @Parameter(property = "karta.sequenceOnly", defaultValue = "false")
    private boolean sequenceOnly;

    /** Emit STATE nodes and TRANSITION edges from enum-backed workflow code. */
    @Parameter(property = "karta.stateMachine", defaultValue = "false")
    private boolean stateMachine;

    /** Draw the cross-module graph rather than the class graph. */
    @Parameter(property = "karta.modulesOnly", defaultValue = "false")
    private boolean modulesOnly;

    /** One diagram per package instead of one for the whole tree. */
    @Parameter(property = "karta.splitPackages", defaultValue = "false")
    private boolean splitPackages;

    /** Wildcard patterns of types or methods to leave out, for example {@code *Test}. */
    @Parameter
    private List<String> excludes = new ArrayList<>();

    /** Maximum call-chain depth to stitch. Unbounded when unset. */
    @Parameter(property = "karta.maxDepth")
    private Integer maxDepth;

    /** Field and method lines per class box before the rest collapse. */
    @Parameter(property = "karta.maxMembers", defaultValue = "6")
    private int maxMembers;

    /**
     * Diagrams to generate. Each entry inherits every value it does not set from the parameters
     * above. Leave it out to generate one diagram from the goal-level parameters.
     */
    @Parameter
    private List<Diagram> diagrams = new ArrayList<>();

    /** Skip this execution entirely. */
    @Parameter(property = "karta.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Fail the build when a diagram produces nothing.
     *
     * <p>Off by default, because a module with no Java source is ordinary in a reactor and failing
     * on it would make the plugin unusable when declared in a parent pom. Turn it on for a module
     * whose diagram is a deliverable: parsers never throw and return an empty graph on failure, so
     * without this a source tree that failed to parse looks exactly like one with nothing in it.
     */
    @Parameter(property = "karta.failOnEmpty", defaultValue = "false")
    private boolean failOnEmpty;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("code-karta: skipped, karta.skip is true");
            return;
        }
        if ("pom".equals(project.getPackaging()) && diagrams.isEmpty() && !modulesOnly) {
            // An aggregator has no sources of its own. Generating nothing is the right answer, and
            // saying so beats an empty output directory nobody can account for.
            getLog().info("code-karta: skipped, packaging is pom and no diagrams are configured."
                    + " Set modulesOnly to draw the reactor instead.");
            return;
        }

        List<Diagram> requested = diagrams.isEmpty() ? List.of(new Diagram()) : diagrams;
        int written = 0;
        for (Diagram diagram : requested) {
            written += generate(diagram);
        }
        getLog().info("code-karta: wrote " + written + (written == 1 ? " file to " : " files to ")
                + outputDirectory);
    }

    private int generate(Diagram diagram) throws MojoExecutionException {
        Path in = Path.of(firstNonBlank(diagram.getInput(), input.getPath()));
        Path outDir = Path.of(firstNonBlank(diagram.getOutput(), outputDirectory.getPath()));

        if (!Files.exists(in)) {
            // A module with no src/main/java is ordinary in a reactor, not a misconfiguration.
            getLog().debug("code-karta: nothing to parse, " + in + " does not exist");
            return countOrFail(0, in);
        }

        RunOptions options = toRunOptions(diagram);
        try {
            if (diagram.isSplitPackages() || splitPackages) {
                List<Path> paths = KartaCli.runPerPackage(in, outDir, options);
                for (Path path : paths) {
                    getLog().info("code-karta: " + path);
                }
                return countOrFail(paths.size(), in);
            }
            Path result = KartaCli.run(in, outDir, options);
            if (result == null) {
                return countOrFail(0, in);
            }
            getLog().info("code-karta: " + result);
            return 1;
        } catch (IOException e) {
            throw new MojoExecutionException("code-karta could not write to " + outDir, e);
        }
    }

    private int countOrFail(int count, Path in) throws MojoExecutionException {
        if (count > 0) {
            return count;
        }
        String message = "code-karta produced no diagram for " + in
                + ". Either there was nothing to draw, or parsing failed: parsers log a warning"
                + " and return an empty graph rather than throwing, so check the build log.";
        if (failOnEmpty) {
            throw new MojoExecutionException(message);
        }
        getLog().info(message + " Set failOnEmpty to true to make this a build failure.");
        return 0;
    }

    /** Visible for testing: the mapping from plugin configuration onto the CLI options record. */
    RunOptions toRunOptions(Diagram diagram) {
        Set<String> patterns = new LinkedHashSet<>(excludes);
        patterns.addAll(diagram.getExcludes());
        // Read each boxed getter once. Calling it twice across a null check reads as a fresh
        // nullable value to SpotBugs on the second call, and the unboxing there is then a real
        // dereference of something it has just been told may be null.
        Integer diagramDepth = diagram.getMaxDepth();
        Integer depth = diagramDepth != null ? diagramDepth : maxDepth;
        Integer diagramMembers = diagram.getMaxMembers();
        return new RunOptions(
                diagram.isSequenceOnly() || sequenceOnly,
                firstNonBlank(diagram.getLayout(), layout),
                diagram.isStateMachine() || stateMachine,
                patterns,
                depth != null ? depth : Integer.MAX_VALUE,
                diagram.isModulesOnly() || modulesOnly,
                firstNonBlank(diagram.getOutputName(), outputName),
                diagramMembers != null ? diagramMembers : maxMembers,
                firstNonBlank(diagram.getFormat(), format));
    }

    private static String firstNonBlank(String specific, String fallback) {
        return specific != null && !specific.isBlank() ? specific : fallback;
    }

    // Package-private setters, so a test can build a configured mojo without Maven injection.

    void setProject(MavenProject project) { this.project = project; }
    void setOutputDirectory(File dir) { this.outputDirectory = dir; }
    void setInput(File in) { this.input = in; }
    void setFormat(String value) { this.format = value; }
    void setLayout(String value) { this.layout = value; }
    void setOutputName(String value) { this.outputName = value; }
    void setSequenceOnly(boolean value) { this.sequenceOnly = value; }
    void setStateMachine(boolean value) { this.stateMachine = value; }
    void setModulesOnly(boolean value) { this.modulesOnly = value; }
    void setSplitPackages(boolean value) { this.splitPackages = value; }
    void setExcludes(List<String> value) { this.excludes = value == null ? new ArrayList<>() : value; }
    void setMaxDepth(Integer value) { this.maxDepth = value; }
    void setMaxMembers(int value) { this.maxMembers = value; }
    void setDiagrams(List<Diagram> value) { this.diagrams = value == null ? new ArrayList<>() : value; }
    void setSkip(boolean value) { this.skip = value; }
    void setFailOnEmpty(boolean value) { this.failOnEmpty = value; }
}
