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
 * which is what makes the single-diagram case configuration-free.
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
    private boolean sequenceOnly;
    private boolean stateMachine;
    private boolean modulesOnly;
    private boolean splitPackages;
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

    public boolean isSequenceOnly() { return sequenceOnly; }
    public void setSequenceOnly(boolean sequenceOnly) { this.sequenceOnly = sequenceOnly; }

    public boolean isStateMachine() { return stateMachine; }
    public void setStateMachine(boolean stateMachine) { this.stateMachine = stateMachine; }

    public boolean isModulesOnly() { return modulesOnly; }
    public void setModulesOnly(boolean modulesOnly) { this.modulesOnly = modulesOnly; }

    public boolean isSplitPackages() { return splitPackages; }
    public void setSplitPackages(boolean splitPackages) { this.splitPackages = splitPackages; }

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
                + ", excludes=" + excludes + ", maxDepth=" + maxDepth
                + ", maxMembers=" + maxMembers + "]";
    }
}
