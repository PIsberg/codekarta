package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A named cluster of {@link Node}s that the render tier draws as a bounding frame.
 *
 * <p>Groups are how the IR expresses containment without nesting nodes: a package, a JPMS module
 * boundary, or a {@code try}/{@code catch} region in an exception-flow diagram. Membership is by
 * node id, so a node can be listed by a group that no longer holds it and a group can be empty.
 * Neither is an error, and the renderer draws a frame around whichever members it can resolve.
 *
 * <p>A node belonging to two groups is not rejected here, but the layout engines assume one
 * enclosing group per node and the result is undefined.
 *
 * @see Graph
 */
@AICore(sensitivity = "Medium", note = "IR cluster — maps a label (package, module boundary, try/catch region) to a set of node IDs. Used by the render tier to draw bounding frames.")
@AISchemaSafe
@AIStrictTypes
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Group {

    private String id;
    private String label;
    private List<String> memberIds;
    private Map<String, String> properties;

    /** Creates an empty group with no id or label. For Jackson. */
    public Group() {
        this.memberIds = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    /**
     * Creates an empty group.
     *
     * @param id    unique within its graph
     * @param label the caption drawn on the frame, such as a package or module name
     */
    public Group(String id, String label) {
        this.id = id;
        this.label = label;
        this.memberIds = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    /**
     * Adds a node id to this group. The node need not exist; membership is resolved when the
     * graph is rendered, and an id that resolves to nothing is skipped rather than rejected.
     *
     * @param nodeId the id of a {@link Node}
     */
    public void addMember(String nodeId) {
        memberIds.add(nodeId);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
