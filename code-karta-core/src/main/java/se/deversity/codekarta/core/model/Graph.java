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

    public void addNode(Node node) {
        refreshIndex();
        nodes.add(node);
        // putIfAbsent, not put: addNode permits duplicate ids and findNode has always returned the
        // first match, which is what a stream().filter().findFirst() did.
        nodeIndex.putIfAbsent(node.getId(), node);
        indexedNodeCount = nodes.size();
    }

    public void addNodeIfAbsent(Node node) {
        refreshIndex();
        if (nodeIndex.containsKey(node.getId())) {
            return;
        }
        nodes.add(node);
        nodeIndex.put(node.getId(), node);
        indexedNodeCount = nodes.size();
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public @Nullable Node findNode(String id) {
        refreshIndex();
        return nodeIndex.get(id);
    }

    public List<Node> getNodes() { return nodes; }

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
