package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AIPerformance;
import se.deversity.vibetags.annotations.AISchemaSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The intermediate representation that flows between all three pipeline tiers, and the only type
 * that crosses a tier boundary.
 *
 * <p>A graph is nodes, edges and groups, and nothing else: no diagram type, no parser state, no
 * rendering options. The renderer works out what kind of diagram to draw from the content, which
 * is why there is no field saying so.
 *
 * <p>The pipeline is three independent steps over one graph:
 *
 * <pre>{@code
 * Graph graph = new JavaSourceInputParser().parse(Path.of("src/main/java"));
 * new SimpleLayoutEngine().layout(graph);   // writes x/y/width/height onto the nodes
 * String svg = new SvgRenderer().render(graph);
 * }</pre>
 *
 * <p>Layout mutates in place and returns the same instance, so the coordinates on the nodes are
 * the only state that changes after parsing. Nodes the engine could not position keep
 * {@code null} coordinates and the renderer skips them.
 *
 * <p>Lookups by node id are O(1). Do not replace them with a scan over {@link #getNodes()}: every
 * parser looks a node up once per class, method and call site, and a linear scan makes building
 * the graph quadratic in the size of the codebase being analysed.
 *
 * <p>Not thread-safe. Build and consume a graph on one thread.
 *
 * @see Node
 * @see Edge
 * @see Group
 */
@AICore(sensitivity = "Critical", note = "Central IR that flows between all three pipeline tiers. Contains only structural data — no business logic, no tier-specific knowledge. Layout coordinates (x/y/width/height) are the only mutable state, written exclusively by Tier 2 engines.")
@AIArchitecture(belongsTo = "core", cannotReference = {"input", "layout", "render", "cli"})
@AIPerformance(constraint = "findNode and addNodeIfAbsent must stay O(1). Both are called once per class, method and call site by every parser and both layout engines, so a linear scan makes graph construction O(n²) — measured at 20k nodes as 7.5 s of lookups against 4 ms indexed. Do not reintroduce a nodes.stream() search, and do not replace the id index with a scan 'for simplicity'.")
@AISchemaSafe
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonIgnore", "com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Graph {

    private List<Node> nodes;
    private List<Edge> edges;
    private List<Group> groups;

    /**
     * Id to first-inserted node, so {@link #findNode} and {@link #addNodeIfAbsent} are O(1).
     *
     * <p>Not part of the serialised schema: it is derived state, rebuilt from {@code nodes}
     * whenever the list length changes. That covers every mutation this class performs and
     * {@link #setNodes}. It does not cover a caller that mutates the list returned by
     * {@link #getNodes()} without changing its length, for example replacing an element in place
     * or removing one node and adding another; that is unsupported, and was the only thing the
     * previous linear scan bought.
     */
    @JsonIgnore
    private final Map<String, Node> nodeIndex = new HashMap<>();

    /** {@code nodes.size()} when {@link #nodeIndex} was last known to be consistent. */
    @JsonIgnore
    private int indexedNodeCount;

    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    /**
     * Appends a node, whether or not one with the same id is already present.
     *
     * <p>Duplicate ids are permitted and {@link #findNode} returns the first of them. Use
     * {@link #addNodeIfAbsent} when building from a source tree, where the same type is reached
     * from several files.
     *
     * @param node the node to append
     */
    public void addNode(Node node) {
        refreshIndex();
        nodes.add(node);
        // putIfAbsent, not put: addNode permits duplicate ids and findNode has always returned the
        // first match, which is what a stream().filter().findFirst() did.
        nodeIndex.putIfAbsent(node.getId(), node);
        indexedNodeCount = nodes.size();
    }

    /**
     * Appends a node unless one with the same id is already present, in which case this does
     * nothing and the existing node is kept.
     *
     * <p>O(1). This is what parsers use, and it runs once per class, method and call site.
     *
     * @param node the node to append if its id is new
     */
    public void addNodeIfAbsent(Node node) {
        refreshIndex();
        if (nodeIndex.containsKey(node.getId())) {
            return;
        }
        nodes.add(node);
        nodeIndex.put(node.getId(), node);
        indexedNodeCount = nodes.size();
    }

    /**
     * Appends an edge. Its endpoints are ids and are not required to resolve to nodes that exist,
     * now or ever.
     *
     * @param edge the edge to append
     */
    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    /**
     * Appends a group. Its members are ids and are not required to resolve.
     *
     * @param group the group to append
     */
    public void addGroup(Group group) {
        groups.add(group);
    }

    /**
     * Returns the node with the given id, or {@code null} if there is none.
     *
     * <p>O(1). Where several nodes share an id, the first one added wins.
     *
     * @param id the node id to look up
     * @return the matching node, or {@code null}
     */
    public @Nullable Node findNode(String id) {
        refreshIndex();
        return nodeIndex.get(id);
    }

    /**
     * The graph's own node list, not a copy. Appending to it or removing from it is seen by
     * {@link #findNode}; replacing an element in place is not. See the note on the id index.
     *
     * @return the live node list
     */
    public List<Node> getNodes() { return nodes; }

    /**
     * Replaces the node list wholesale, for example after filtering. The id index is rebuilt.
     *
     * @param nodes the new node list, which the graph takes ownership of
     */
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
        // The new list may be any length, including the old one, so size comparison cannot detect
        // this. Force the rebuild.
        indexedNodeCount = -1;
    }

    public List<Edge> getEdges() { return edges; }
    public void setEdges(List<Edge> edges) { this.edges = edges; }

    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }

    private void refreshIndex() {
        if (indexedNodeCount == nodes.size()) {
            return;
        }
        nodeIndex.clear();
        for (Node node : nodes) {
            nodeIndex.putIfAbsent(node.getId(), node);
        }
        indexedNodeCount = nodes.size();
    }
}
