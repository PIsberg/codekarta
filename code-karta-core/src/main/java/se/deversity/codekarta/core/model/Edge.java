package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

/**
 * A directed relationship between two {@link Node}s: an inheritance link, a field reference, a
 * method call, an exception propagation or a state transition, depending on {@link #getType()}.
 *
 * <p>Endpoints are node ids, not node references, so an edge can be created before its target
 * exists and can survive a node list being filtered. Nothing enforces that {@code sourceId} and
 * {@code targetId} resolve; a renderer that cannot find either end is expected to skip the edge.
 *
 * <p>{@code label} carries different things per type and is the one field worth knowing about:
 * for a {@code CALLS} edge it is the sequence number that orders a sequence diagram, and for a
 * {@code HAS} edge it is the field name. It is {@code null} where the type has nothing to say.
 *
 * @see Graph
 * @see EdgeType
 */
@AICore(sensitivity = "High", note = "IR directed edge. id/sourceId/targetId/type are required; label is optional (sequence number for CALLS, field name for HAS).")
@AISchemaSafe
@AIStrictTypes
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Edge {

    private String id;
    private String sourceId;
    private String targetId;
    private String type;
    private @Nullable String label;

    /** Creates an empty edge. For Jackson; parsers use the four-argument constructor. */
    public Edge() {}

    /**
     * Creates an edge with no label.
     *
     * @param id       unique within its graph
     * @param sourceId id of the node the edge leaves; need not exist yet
     * @param targetId id of the node the edge enters; need not exist yet
     * @param type     an {@link EdgeType} string value, which the renderer matches on to pick a
     *                 CSS class and a line style
     */
    public Edge(String id, String sourceId, String targetId, String type) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    /**
     * Text drawn along the edge, or {@code null}.
     *
     * <p>Type-dependent: the call sequence number for {@code CALLS}, the field name for
     * {@code HAS}, the trigger for a state transition.
     *
     * @return the edge label, or {@code null} if this edge type carries none
     */
    public @Nullable String getLabel() { return label; }
    public void setLabel(@Nullable String label) { this.label = label; }
}
