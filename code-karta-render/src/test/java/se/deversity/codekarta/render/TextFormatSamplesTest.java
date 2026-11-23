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
import java.util.ArrayList;
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
 *
 * <p>Next to each sample, {@code <name>.mmd.expect} and {@code <name>.dot.expect} list the labels
 * a reader must see, one per line. Parsing proves Mermaid accepts the text; only drawing it shows
 * whether {@code run(): void} comes out as {@code run() : : void} (#305). The validator renders
 * each sample and fails unless every expected line is the full text of some drawn label.
 */
class TextFormatSamplesTest {

    /** Quote, semicolon, hash, angle brackets, pipe, colon, brackets of every kind, comment marker, backslash, line break. */
    static final String HOSTILE = "q\"s;h#l<g>p|c:b{c}[s](p)~t,a`b\\s&e%%m -->x\nend";

    /** HOSTILE as Mermaid draws it: the line break becomes a space. */
    static final String FLAT = HOSTILE.replace('\n', ' ');

    /** HOSTILE's first line; DOT draws a label's line break as a new line. */
    static final String HEAD = HOSTILE.substring(0, HOSTILE.indexOf('\n'));

    private record Sample(Graph graph, List<String> mermaid, List<String> dot) { }

    @Test
    void writesEverySampleForTheParsers() throws IOException {
        Map<String, Sample> samples = samples();
        Path dir = Path.of(System.getProperty("formatSamples.dir", "target/format-samples"));
        Files.createDirectories(dir);
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, Sample> e : samples.entrySet()) {
            Sample s = e.getValue();
            written.add(write(dir, e.getKey() + ".mmd", new MermaidRenderer().render(s.graph())));
            written.add(write(dir, e.getKey() + ".mmd.expect", String.join("\n", s.mermaid()) + "\n"));
            written.add(write(dir, e.getKey() + ".dot", new DotRenderer().render(s.graph())));
            written.add(write(dir, e.getKey() + ".dot.expect", String.join("\n", s.dot()) + "\n"));
        }
        assertEquals(samples.size() * 4, written.size());
    }

    private static Path write(Path dir, String name, String text) throws IOException {
        return Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
    }

    /** Every sample, with the labels Mermaid and Graphviz must draw for it. */
    static Map<String, Sample> samples() {
        Map<String, Sample> s = new LinkedHashMap<>();
        s.put("class", new Sample(MermaidRendererTest.classGraph(),
                List.of("Order", "id: long", "items: List<Item>", "submit()", "Payable", "Item", "Base", "items"),
                List.of("Order", "Payable", "Item", "Base", "com.x", "items")));
        s.put("members", new Sample(MermaidRendererTest.memberGraph(),
                List.of("items: Map<String, List<Item>>", "…(+3 more)",
                        "find(Map<K, V>, int) : Map<String, List<Item>>", "run() : void"),
                List.of("Order")));
        s.put("class-hostile", new Sample(hostileClassGraph(),
                List.of(FLAT, "id: long", "items: Map<String, List<Item>>", HEAD, "end: int",
                        "submit(List<Item> items, int n) : void", "C"),
                List.of(HEAD, "end", "C")));
        s.put("sequence", new Sample(MermaidRendererTest.sequenceGraph(),
                List.of("A", "B", "Boom", "1: find", "2: helper", "throws Boom"),
                List.of("A.run", "B.find", "A.helper", "Boom", "1", "2", "throws")));
        s.put("sequence-hostile", new Sample(hostileSequenceGraph(),
                List.of(FLAT, "B", "1: " + FLAT, "throws " + FLAT),
                List.of(FLAT + ".run", "B." + FLAT, HEAD, "end", "1", "throws")));
        s.put("state", new Sample(MermaidRendererTest.stateGraph(false),
                List.of("NEW", "PAID", "SHIPPED", "advance"),
                List.of("NEW", "PAID", "SHIPPED", "advance")));
        s.put("state-two-machines", new Sample(MermaidRendererTest.stateGraph(true),
                List.of("Order.NEW", "Order.SHIPPED", "Light.RED", "Light.GREEN"),
                List.of("NEW", "RED", "GREEN", "advance")));
        s.put("state-hostile", new Sample(hostileStateGraph(),
                List.of(FLAT + "NEW", FLAT + "DONE", FLAT),
                List.of(HEAD, "endNEW", "endDONE", "end")));
        s.put("module", new Sample(MermaidRendererTest.moduleGraph(),
                List.of("app", "core", "java.base", "com.x", "requires", "exports"),
                List.of("app", "core", "java.base", "com.x", "requires", "exports")));
        s.put("module-hostile", new Sample(hostileModuleGraph(),
                List.of(FLAT, "core", "exports"),
                List.of(HEAD, "end", "core", "exports")));
        return s;
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
