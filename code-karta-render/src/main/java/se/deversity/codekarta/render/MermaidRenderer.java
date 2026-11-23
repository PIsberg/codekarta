package se.deversity.codekarta.render;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.vibetags.annotations.AIArchitecture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a graph as Mermaid text, for diagrams that live in Markdown: GitHub, GitLab, MkDocs and
 * Obsidian draw a fenced {@code mermaid} block inline, and a change to it diffs as text rather
 * than as a regenerated SVG (issue #275).
 *
 * <p>The diagram kind follows the graph's content, as {@link SvgRenderer} does: an interaction
 * graph is a {@code sequenceDiagram} (messages in the order {@link SequenceDiagramRenderer} draws
 * them), STATE nodes a {@code stateDiagram-v2}, MODULE or PACKAGE nodes a {@code flowchart TD},
 * and anything else a {@code classDiagram}. Node ids are generated ({@code n0}, {@code n1}, ...)
 * and every name is a quoted label, so no Java identifier, package or generic can break the
 * syntax; characters that would end a statement are written as Mermaid entity codes. Output
 * depends on the graph alone and is byte-identical across runs.
 */
@AIArchitecture(belongsTo = "render", cannotReference = {"input", "layout", "cli"})
public final class MermaidRenderer {

    private static final String INDENT = "    ";

    public String render(Graph graph) {
        if (new SvgRenderer().isInteractionGraph(graph)) {
            return sequence(graph);
        }
        if (has(graph, "STATE")) {
            return states(graph);
        }
        if (has(graph, "MODULE") || has(graph, "PACKAGE")) {
            return flowchart(graph);
        }
        return classes(graph);
    }

    // ------------------------------------------------------------------ class diagram

    private String classes(Graph graph) {
        Map<String, String> ids = ids(graph);
        StringBuilder out = new StringBuilder("classDiagram\n");
        for (Node node : graph.getNodes()) {
            String id = ids.get(node.getId());
            line(out, "class " + id + "[\"" + escape(label(node)) + "\"]");
            Map<String, String> props = node.getProperties();
            if (props != null) {
                for (String section : new String[] {"fields", "methods"}) {
                    String text = props.get(section);
                    if (text != null && !text.isEmpty()) {
                        for (String member : text.split("\n")) {
                            line(out, id + " : " + escapeMember(member));
                        }
                    }
                }
            }
            if ("INTERFACE".equals(node.getType())) {
                line(out, "<<interface>> " + id);
            }
        }
        for (Edge edge : graph.getEdges()) {
            String source = ids.get(edge.getSourceId());
            String target = ids.get(edge.getTargetId());
            if (source == null || target == null) {
                continue;
            }
            String type = edge.getType() == null ? "" : edge.getType();
            switch (type) {
                case "EXTENDS" -> line(out, target + " <|-- " + source);
                case "IMPLEMENTS" -> line(out, target + " <|.. " + source);
                default -> line(out, source + " --> " + target
                        + (edge.getLabel() != null ? " : " + escape(edge.getLabel()) : ""));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ sequence diagram

    private String sequence(Graph graph) {
        SequenceDiagramRenderer layout = new SequenceDiagramRenderer(new SvgRenderer());
        List<SequenceDiagramRenderer.Participant> all = layout.buildParticipants(graph);
        List<SequenceDiagramRenderer.Message> messages = layout.orderMessages(graph);

        // Participants that send or receive a message, in lane order; all of them when none do.
        Set<SequenceDiagramRenderer.Participant> used = new LinkedHashSet<>();
        for (SequenceDiagramRenderer.Message m : messages) {
            SequenceDiagramRenderer.Participant from = SequenceDiagramRenderer.participantFor(m.fromId(), all);
            SequenceDiagramRenderer.Participant to = SequenceDiagramRenderer.participantFor(m.toId(), all);
            if (from != null) used.add(from);
            if (to != null) used.add(to);
        }
        List<SequenceDiagramRenderer.Participant> shown = new ArrayList<>();
        for (SequenceDiagramRenderer.Participant p : all) {
            if (used.isEmpty() || used.contains(p)) {
                shown.add(p);
            }
        }
        Map<SequenceDiagramRenderer.Participant, String> alias = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder("sequenceDiagram\n");
        for (SequenceDiagramRenderer.Participant p : shown) {
            String a = "p" + alias.size();
            alias.put(p, a);
            line(out, "participant " + a + " as " + escape(p.label()));
        }
        for (SequenceDiagramRenderer.Message m : messages) {
            String from = alias.get(SequenceDiagramRenderer.participantFor(m.fromId(), shown));
            String to = alias.get(SequenceDiagramRenderer.participantFor(m.toId(), shown));
            if (from == null || to == null) {
                continue;
            }
            String arrow = "EXCEPTION_PROPAGATION".equals(m.type()) ? "--x" : "->>";
            line(out, from + arrow + to + ": " + escape(m.label()));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ state diagram

    private String states(Graph graph) {
        Map<String, String> ids = ids(graph);
        Set<String> machines = new LinkedHashSet<>();
        for (Node node : graph.getNodes()) {
            if ("STATE".equals(node.getType())) {
                machines.add(prefix(node.getId()));
            }
        }
        StringBuilder out = new StringBuilder("stateDiagram-v2\n");
        for (Node node : graph.getNodes()) {
            String name = label(node);
            if (machines.size() > 1) {
                String machine = prefix(node.getId());
                name = machine.substring(machine.lastIndexOf('.') + 1) + "." + name;
            }
            line(out, "state \"" + escape(name) + "\" as " + ids.get(node.getId()));
        }
        for (Edge edge : graph.getEdges()) {
            String source = ids.get(edge.getSourceId());
            String target = ids.get(edge.getTargetId());
            if (source != null && target != null) {
                line(out, source + " --> " + target
                        + (edge.getLabel() != null ? " : " + escape(edge.getLabel()) : ""));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ flowchart

    private String flowchart(Graph graph) {
        Map<String, String> ids = ids(graph);
        StringBuilder out = new StringBuilder("flowchart TD\n");
        for (Node node : graph.getNodes()) {
            line(out, ids.get(node.getId()) + "[\"" + escape(label(node)) + "\"]");
        }
        for (Edge edge : graph.getEdges()) {
            String source = ids.get(edge.getSourceId());
            String target = ids.get(edge.getTargetId());
            if (source == null || target == null) {
                continue;
            }
            String type = edge.getType() == null ? "" : edge.getType();
            String text = edge.getLabel() != null ? edge.getLabel()
                    : type.toLowerCase(Locale.ROOT).replace('_', ' ');
            String arrow = "EXPORTS".equals(type) ? " -.->" : " -->";
            line(out, source + arrow + (text.isEmpty() ? " " : "|" + escape(text) + "| ") + target);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, String> ids(Graph graph) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Node node : graph.getNodes()) {
            ids.putIfAbsent(node.getId(), "n" + ids.size());
        }
        return ids;
    }

    private static boolean has(Graph graph, String type) {
        return graph.getNodes().stream().anyMatch(n -> type.equals(n.getType()));
    }

    private static String label(Node node) {
        return node.getLabel() != null ? node.getLabel() : node.getId();
    }

    private static String prefix(String id) {
        int dot = id.lastIndexOf('.');
        return dot > 0 ? id.substring(0, dot) : id;
    }

    private static void line(StringBuilder out, String text) {
        out.append(INDENT).append(text).append('\n');
    }

    /** Characters that end or corrupt a Mermaid statement, as Mermaid entity codes; line breaks become spaces. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("#quot;");
                case '#' -> out.append("#35;");
                case ';' -> out.append("#59;");
                case '<' -> out.append("#lt;");
                case '>' -> out.append("#gt;");
                case '|' -> out.append("#124;");
                case '\n', '\r' -> out.append(' ');
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** A class member line: generics in Mermaid's tilde notation, then the same escaping. */
    static String escapeMember(String member) {
        return escape(member.replace('<', '~').replace('>', '~'));
    }
}
