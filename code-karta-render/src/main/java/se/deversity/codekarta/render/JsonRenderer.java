package se.deversity.codekarta.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIIdempotent;

/**
 * Serialises a {@link Graph} to JSON, for consumers that want the analysis rather than a picture.
 *
 * <p>An SVG is a dead end for tooling: an architecture rule that fails a build, a dependency
 * report, a diff between two revisions and a feed into someone's own renderer all need the graph,
 * not a drawing of it. This is the same object the pipeline passes between tiers, written out
 * verbatim.
 *
 * <pre>{@code
 * Graph graph = new JavaSourceInputParser().parse(Path.of("src/main/java"));
 * new SimpleLayoutEngine().layout(graph);          // optional; adds x/y/width/height
 * String json = new JsonRenderer().render(graph);
 * }</pre>
 *
 * <p>Layout is optional. Run it and every node carries coordinates; skip it and the coordinate
 * fields are absent, because the model omits nulls. Everything else is present either way.
 *
 * <p>The schema is the IR: the field names of {@code Graph}, {@code Node}, {@code Edge} and
 * {@code Group}, which {@code docs/COMPATIBILITY.md} lists as public API. It round-trips, so
 * {@code new ObjectMapper().readValue(json, Graph.class)} gives back an equivalent graph.
 *
 * <p>Output is deterministic for a given graph: two runs produce byte-identical JSON. That is not
 * free from Jackson and is asserted by a test, because a {@code HashMap} iterated in hash order
 * and a platform-dependent line separator would both break it, and the CLI's idempotence
 * guarantee rests on it.
 *
 * @see SvgRenderer
 */
@AIArchitecture(belongsTo = "render", cannotReference = {"input", "layout", "cli"})
@AIIdempotent(reason = "Two renders of the same graph must be byte-identical: map keys are sorted and the pretty printer is pinned to \n, because HashMap iteration order and System.lineSeparator() are both unstable. KartaCli's byte-identical-output invariant, and the CI step that diffs regenerated files, depend on it.")
public class JsonRenderer {

    /**
     * Shared and safe to share: an ObjectWriter is immutable once configured, unlike the mapper
     * it came from.
     */
    private static final ObjectWriter WRITER = buildWriter();

    private static ObjectWriter buildWriter() {
        // Two-space indent with an explicit \n. DefaultPrettyPrinter's default indenter uses
        // System.lineSeparator(), which would make the output differ between a Windows and a Linux
        // run of the same build.
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));

        return new ObjectMapper()
                // Node.properties and Group.properties are HashMaps, so without this the key order
                // is hash order and varies with content and JVM.
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .writer(printer);
    }

    /**
     * Renders the graph as pretty-printed JSON with a trailing newline.
     *
     * @param graph the graph to serialise; coordinates are included only if a layout engine has
     *              run over it
     * @return JSON describing the graph, never {@code null}
     * @throws IllegalStateException if the graph cannot be serialised. The IR is plain data with
     *         no custom serialisers, so this means the model has been changed in a way Jackson
     *         cannot handle, not that the input was bad.
     */
    public String render(Graph graph) {
        try {
            return WRITER.writeValueAsString(graph) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialise the graph to JSON. This is a defect in the IR model, "
                            + "not in the input that produced it.", e);
        }
    }
}
