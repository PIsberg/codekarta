package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * A vertex in the {@link Graph} IR: a class, interface, enum, method, module, package, state or
 * exception, depending on {@link #getType()}.
 *
 * <p>Nodes are produced by the input tier, positioned by the layout tier and drawn by the render
 * tier. The three tiers never talk to each other, so this class is the whole of what they agree
 * on. It carries structure only: no parsing state, no rendering hints, no behaviour.
 *
 * <p>{@code id}, {@code type} and {@code label} are the serialised schema and are always set on a
 * node that came from a parser. {@code x}, {@code y}, {@code width} and {@code height} are layout
 * output and are {@code null} until a {@code LayoutEngine} has run. A node the engine could not
 * position keeps them {@code null}, and the renderer skips it rather than defaulting it to the
 * origin; that pairing is deliberate on both sides, so a caller writing its own renderer must
 * handle {@code null} coordinates.
 *
 * <p>Not thread-safe, and not intended to be. A {@code Graph} is built and consumed by one thread.
 *
 * @see Graph
 * @see NodeType
 */
@AICore(sensitivity = "High", note = "IR vertex. Fields id/type/label/properties are the stable serialised schema; x/y/width/height are layout-only and may be null before Tier 2 runs.")
@AISchemaSafe
@AIStrictTypes
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node {

    private String id;
    private String type;
    private String label;
    private Map<String, String> properties;

    // Populated by the layout tier
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double width;
    private @Nullable Double height;

    /** Creates an empty node. For Jackson; parsers use the three-argument constructor. */
    public Node() {}

    /**
     * Creates a node with no properties and no layout coordinates.
     *
     * @param id    unique within its graph; parsers use a qualified form such as
     *              {@code com.example.Order} or {@code com.example.Order#submit}
     * @param type  a {@link NodeType} string value. The string, not a constant reference, is what
     *              the renderer matches on to pick a CSS class
     * @param label the text drawn in the node box, usually the simple name
     */
    public Node(String id, String type, String label) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.properties = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    /**
     * Free-form per-node metadata, written by parsers and read by the renderer.
     *
     * <p>Not part of the stable schema: keys come and go with parser changes, and a consumer that
     * branches on one is depending on an implementation detail. The returned map is the node's
     * own, so mutating it mutates the node.
     *
     * @return the live properties map, never {@code null} for a node built by a parser
     */
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    /**
     * Horizontal position, or {@code null} if no layout engine has run or the engine could not
     * place this node. The renderer skips nodes with {@code null} coordinates.
     *
     * @return the x coordinate in SVG user units, or {@code null}
     */
    public @Nullable Double getX() { return x; }
    public void setX(@Nullable Double x) { this.x = x; }

    public @Nullable Double getY() { return y; }
    public void setY(@Nullable Double y) { this.y = y; }

    public @Nullable Double getWidth() { return width; }
    public void setWidth(@Nullable Double width) { this.width = width; }

    public @Nullable Double getHeight() { return height; }
    public void setHeight(@Nullable Double height) { this.height = height; }
}
