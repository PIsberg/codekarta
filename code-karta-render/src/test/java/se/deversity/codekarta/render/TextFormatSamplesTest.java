package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Writes Mermaid and DOT renderings of every diagram kind to {@code target/format-samples}, for
 * the real Mermaid and Graphviz parsers to check in CI (issue #293). The exact-text tests in
 * {@link MermaidRendererTest} and {@link DotRendererTest} pin what the renderers write; only the
 * parsers can say whether that text is accepted, and they run in Node, outside this build:
 * {@code scripts/format-check/validate.mjs}, run by the "Mermaid and DOT output parse" job.
 *
 * <p>Each kind is written twice: the ordinary fixture, and a hostile one whose every label,
 * member line and edge label carries the characters most likely to end or corrupt a statement.
 */
class TextFormatSamplesTest {

    /** Quote, semicolon, hash, angle brackets, pipe, colon, brackets of every kind, comment marker, backslash, line break. */
    static final String HOSTILE = "q\"s;h#l<g>p|c:b{c}[s](p)~t,a`b\\s&e%%m -->x\nend";

    @Test
    void writesEverySampleForTheParsers() throws IOException {
        Map<String, Graph> graphs = new LinkedHashMap<>();
        graphs.put("class", MermaidRendererTest.classGraph());
        graphs.put("class-hostile", hostileClassGraph());
        graphs.put("sequence", MermaidRendererTest.sequenceGraph());
        graphs.put("sequence-hostile", hostileSequenceGraph());
        graphs.put("state", MermaidRendererTest.stateGraph(false));
        graphs.put("state-two-machines", MermaidRendererTest.stateGraph(true));
        graphs.put("state-hostile", hostileStateGraph());
        graphs.put("module", MermaidRendererTest.moduleGraph());
        graphs.put("module-hostile", hostileModuleGraph());

        Path dir = Path.of(System.getProperty("formatSamples.dir", "target/format-samples"));
        Files.createDirectories(dir);
        int written = 0;
        for (Map.Entry<String, Graph> e : graphs.entrySet()) {
            Files.writeString(dir.resolve(e.getKey() + ".mmd"),
                    new MermaidRenderer().render(e.getValue()), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(e.getKey() + ".dot"),
                    new DotRenderer().render(e.getValue()), StandardCharsets.UTF_8);
            written += 2;
        }
        assertEquals(graphs.size() * 2, written);
    }

    /** The hostile fixtures must reach the renderer branch they are named after, or the parse check proves less than it claims. */
    @Test
    void eachHostileFixtureIsTheDiagramKindItIsNamedAfter() {
        MermaidRenderer renderer = new MermaidRenderer();
        assertEquals("classDiagram", firstLine(renderer.render(hostileClassGraph())));
        assertEquals("sequenceDiagram", firstLine(renderer.render(hostileSequenceGraph())));
        assertEquals("stateDiagram-v2", firstLine(renderer.render(hostileStateGraph())));
        assertEquals("flowchart TD", firstLine(renderer.render(hostileModuleGraph())));
    }

    private static String firstLine(String text) {
        return text.substring(0, text.indexOf('\n'));
    }

    static Graph hostileClassGraph() {
        Graph graph = new Graph();
        Node a = new Node("com.x.A", "CLASS", HOSTILE);
        a.setProperties(Map.of(
                "fields", "id: long\nitems: Map<String, List<Item>>\n" + HOSTILE + ": int",
                "methods", "submit(List<Item> items, int n): void\n" + HOSTILE + "()"));
        graph.addNode(a);
        graph.addNode(new Node("com.x.B", "INTERFACE", HOSTILE));
        graph.addNode(new Node("com.x.C", "CLASS", "C"));
        graph.addEdge(new Edge("e1", "com.x.A", "com.x.B", "IMPLEMENTS"));
        graph.addEdge(new Edge("e2", "com.x.A", "com.x.C", "EXTENDS"));
        Edge has = new Edge("e3", "com.x.A", "com.x.C", "HAS");
        has.setLabel(HOSTILE);
        graph.addEdge(has);
        Group group = new Group("pkg", HOSTILE);
        group.addMember("com.x.A");
        group.addMember("com.x.B");
        graph.addGroup(group);
        return graph;
    }

    /** Participant and message labels come from method ids (class, then method name), so the hostile text goes there. */
    static Graph hostileSequenceGraph() {
        String caller = HOSTILE.replace('\n', ' ') + ".run";
        String callee = "B." + HOSTILE.replace('\n', ' ');
        Graph graph = new Graph();
        graph.addNode(new Node(caller, "METHOD", HOSTILE));
        graph.addNode(new Node(callee, "METHOD", HOSTILE));
        graph.addNode(new Node("exception:" + HOSTILE, "EXCEPTION", HOSTILE));
        Edge call = new Edge("c1", caller, callee, "CALLS");
        call.setLabel("1");
        graph.addEdge(call);
        graph.addEdge(new Edge("x1", callee, "exception:" + HOSTILE, "EXCEPTION_PROPAGATION"));
        return graph;
    }

    static Graph hostileStateGraph() {
        Graph graph = new Graph();
        for (String s : List.of("NEW", "DONE")) {
            graph.addNode(new Node("W.Order." + s, "STATE", HOSTILE + s));
        }
        Edge t = new Edge("t1", "W.Order.NEW", "W.Order.DONE", "TRANSITION");
        t.setLabel(HOSTILE);
        graph.addEdge(t);
        return graph;
    }

    static Graph hostileModuleGraph() {
        Graph graph = new Graph();
        graph.addNode(new Node("app", "MODULE", HOSTILE));
        graph.addNode(new Node("core", "MODULE", "core"));
        graph.addNode(new Node("com.x", "PACKAGE", HOSTILE));
        Edge requires = new Edge("r1", "app", "core", "REQUIRES");
        requires.setLabel(HOSTILE);
        graph.addEdge(requires);
        graph.addEdge(new Edge("x1", "app", "com.x", "EXPORTS"));
        return graph;
    }
}
