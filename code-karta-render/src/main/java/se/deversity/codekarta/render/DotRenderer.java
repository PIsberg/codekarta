package se.deversity.codekarta.render;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.vibetags.annotations.AIArchitecture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a graph as Graphviz DOT, for pipelines that already lay graphs out with Graphviz
 * (issue #275). Groups become clusters, so packages are boxed as they are in the SVG; relation
 * types keep the SVG's arrow semantics (hollow arrowheads for inheritance, dashed for
 * implementation and exception flow). Node ids are generated and every label is quoted, and the
 * output is byte-identical across runs.
 */
@AIArchitecture(belongsTo = "render", cannotReference = {"input", "layout", "cli"})
public final class DotRenderer {

    private static final String INDENT = "    ";
    private static final String RED = "\"#dc2626\"";

    public String render(Graph graph) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Node node : graph.getNodes()) {
            ids.putIfAbsent(node.getId(), "n" + ids.size());
        }
        StringBuilder out = new StringBuilder("digraph karta {\n");
        out.append(INDENT).append("rankdir=TB;\n");
        out.append(INDENT).append("node [shape=box, style=rounded, fontname=\"Helvetica\"];\n");
        out.append(INDENT).append("edge [fontname=\"Helvetica\", fontsize=10];\n");

        // A node belongs to the first group that lists it, as Group documents one enclosing group.
        Set<String> placed = new HashSet<>();
        int cluster = 0;
        for (Group group : graph.getGroups()) {
            Set<String> members = new HashSet<>(group.getMemberIds());
            List<Node> inside = new ArrayList<>();
            for (Node node : graph.getNodes()) {
                if (members.contains(node.getId()) && !placed.contains(node.getId())) {
                    inside.add(node);
                }
            }
            if (inside.isEmpty()) {
                continue;
            }
            out.append(INDENT).append("subgraph cluster_").append(cluster++).append(" {\n");
            out.append(INDENT).append(INDENT).append("label=").append(quote(
                    group.getLabel() != null ? group.getLabel() : group.getId())).append(";\n");
            for (Node node : inside) {
                out.append(INDENT).append(INDENT).append(nodeStatement(node, ids));
                placed.add(node.getId());
            }
            out.append(INDENT).append("}\n");
        }
        for (Node node : graph.getNodes()) {
            if (placed.add(node.getId())) {
                out.append(INDENT).append(nodeStatement(node, ids));
            }
        }
        for (Edge edge : graph.getEdges()) {
            String source = ids.get(edge.getSourceId());
            String target = ids.get(edge.getTargetId());
            if (source != null && target != null) {
                out.append(INDENT).append(source).append(" -> ").append(target)
                   .append(edgeAttributes(edge)).append(";\n");
            }
        }
        return out.append("}\n").toString();
    }

    private static String nodeStatement(Node node, Map<String, String> ids) {
        String type = node.getType() == null ? "" : node.getType();
        // A method's own label is only its name; the id says whose it is.
        String label = "METHOD".equals(type) || node.getLabel() == null ? node.getId() : node.getLabel();
        StringBuilder attrs = new StringBuilder("label=").append(quote(label));
        switch (type) {
            case "INTERFACE" -> attrs.append(", style=\"rounded,dashed\"");
            case "STATE" -> attrs.append(", shape=ellipse");
            case "EXCEPTION" -> attrs.append(", color=").append(RED);
            default -> { }
        }
        return ids.get(node.getId()) + " [" + attrs + "];\n";
    }

    private static String edgeAttributes(Edge edge) {
        String type = edge.getType() == null ? "" : edge.getType();
        List<String> attrs = new ArrayList<>();
        String label = edge.getLabel();
        if (label == null) {
            label = switch (type) {
                case "EXCEPTION_PROPAGATION" -> "throws";
                case "REQUIRES" -> "requires";
                case "EXPORTS" -> "exports";
                default -> null;
            };
        }
        if (label != null) {
            attrs.add("label=" + quote(label));
        }
        switch (type) {
            case "EXTENDS" -> attrs.add("arrowhead=empty");
            case "IMPLEMENTS" -> {
                attrs.add("arrowhead=empty");
                attrs.add("style=dashed");
            }
            case "HAS" -> attrs.add("arrowhead=vee");
            case "EXPORTS" -> attrs.add("style=dashed");
            case "EXCEPTION_PROPAGATION" -> {
                attrs.add("style=dashed");
                attrs.add("color=" + RED);
            }
            default -> { }
        }
        return attrs.isEmpty() ? "" : " [" + String.join(", ", attrs) + "]";
    }

    /** A DOT double-quoted string: backslash and quote escaped, a line break as DOT's own escape. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> { }
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
