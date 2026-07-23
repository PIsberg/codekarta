package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AISchemaSafe;

import java.util.ArrayList;
import java.util.List;

@AICore(sensitivity = "Critical", note = "Central IR that flows between all three pipeline tiers. Contains only structural data — no business logic, no tier-specific knowledge. Layout coordinates (x/y/width/height) are the only mutable state, written exclusively by Tier 2 engines.")
@AIArchitecture(belongsTo = "core", cannotReference = {"input", "layout", "render", "cli"})
@AISchemaSafe
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Graph {

    private List<Node> nodes;
    private List<Edge> edges;
    private List<Group> groups;

    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void addNodeIfAbsent(Node node) {
        boolean exists = nodes.stream().anyMatch(n -> node.getId().equals(n.getId()));
        if (!exists) {
            nodes.add(node);
        }
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public @Nullable Node findNode(String id) {
        return nodes.stream().filter(n -> id.equals(n.getId())).findFirst().orElse(null);
    }

    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    public List<Edge> getEdges() { return edges; }
    public void setEdges(List<Edge> edges) { this.edges = edges; }

    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }
}
