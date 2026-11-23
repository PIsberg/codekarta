package se.deversity.codekarta.maven;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One diagram to generate. A {@code <diagram>} element inside the plugin's {@code <diagrams>}
 * list.
 *
 * <p>Every field mirrors a CLI flag of the same name, so the reference in
 * {@code docs/CLI.md} describes both. A field left unset falls back to the goal-level parameter,
 * which is what makes the single-diagram case configuration-free. The flags are boxed so that
 * "unset" and "false" differ: a {@code <stateMachine>false</stateMachine>} here switches off a
 * goal-level {@code true}, which primitive booleans OR-ed with the goal level could not (#277).
 *
 * <p>This is a plain mutable bean because that is what Maven's configurator populates. It is not
 * part of the library API.
 */
public class Diagram {

    private @Nullable String input;
    private @Nullable String output;
    private @Nullable String outputName;
    private @Nullable String format;
    private @Nullable String layout;
    private @Nullable Boolean sequenceOnly;
    private @Nullable Boolean stateMachine;
    private @Nullable Boolean modulesOnly;
    private @Nullable Boolean splitPackages;
    private @Nullable Boolean includeTests;
    private List<String> excludes = new ArrayList<>();
    private @Nullable Integer maxDepth;
    private @Nullable Integer maxMembers;

    public @Nullable String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public @Nullable String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public @Nullable String getOutputName() { return outputName; }
    public void setOutputName(String outputName) { this.outputName = outputName; }

    public @Nullable String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public @Nullable String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    /** @return the per-diagram value, or {@code null} when unset and the goal level applies */
    public @Nullable Boolean getSequenceOnly() { return sequenceOnly; }
    public void setSequenceOnly(Boolean sequenceOnly) { this.sequenceOnly = sequenceOnly; }

    /** @return the per-diagram value, or {@code null} when unset and the goal level applies */
    public @Nullable Boolean getStateMachine() { return stateMachine; }
    public void setStateMachine(Boolean stateMachine) { this.stateMachine = stateMachine; }

    /** @return the per-diagram value, or {@code null} when unset and the goal level applies */
    public @Nullable Boolean getModulesOnly() { return modulesOnly; }
    public void setModulesOnly(Boolean modulesOnly) { this.modulesOnly = modulesOnly; }

    /** @return the per-diagram value, or {@code null} when unset and the goal level applies */
    public @Nullable Boolean getSplitPackages() { return splitPackages; }
    public void setSplitPackages(Boolean splitPackages) { this.splitPackages = splitPackages; }

    /** @return the per-diagram value, or {@code null} when unset and the goal level applies */
    public @Nullable Boolean getIncludeTests() { return includeTests; }
    public void setIncludeTests(Boolean includeTests) { this.includeTests = includeTests; }

    /** @return a copy, so a caller cannot reach in and edit the configured patterns */
    public List<String> getExcludes() { return List.copyOf(excludes); }
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes == null ? new ArrayList<>() : excludes;
    }

    public @Nullable Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public @Nullable Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }

    @Override
    public String toString() {
        return "Diagram[input=" + input + ", output=" + output + ", outputName=" + outputName
                + ", format=" + format + ", layout=" + layout
                + ", sequenceOnly=" + sequenceOnly + ", stateMachine=" + stateMachine
                + ", modulesOnly=" + modulesOnly + ", splitPackages=" + splitPackages
                + ", includeTests=" + includeTests
                + ", excludes=" + excludes + ", maxDepth=" + maxDepth
                + ", maxMembers=" + maxMembers + "]";
    }
}
