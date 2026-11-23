package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mermaid text output (issue #275): a diagram that lives in Markdown, renders inline on GitHub and
 * GitLab, and diffs as text. The diagram kind follows the graph's content, as it does for SVG.
 */
class MermaidRendererTest {

    private final MermaidRenderer renderer = new MermaidRenderer();

    @Test
    void aClassGraphIsAClassDiagramWithMembersAndRelations() {
        assertEquals("""
                classDiagram
                    class n0["Order"]
                    n0 : id: long
                    n0 : items: List~Item~
                    n0 : submit()
                    class n1["Payable"]
                    <<interface>> n1
                    class n2["Item"]
                    class n3["Base"]
                    n1 <|.. n0
                    n3 <|-- n0
                    n0 --> n2 : items
                """, renderer.render(classGraph()));
    }

    @Test
    void anInteractionGraphIsASequenceDiagramInCallOrder() {
        assertEquals("""
                sequenceDiagram
                    participant p0 as A
                    participant p1 as B
                    participant p2 as Boom
                    p0->>p1: 1: find
                    p0->>p0: 2: helper
                    p1--xp2: throws Boom
                """, renderer.render(sequenceGraph()));
    }

    @Test
    void aStateGraphIsAStateDiagram() {
        assertEquals("""
                stateDiagram-v2
                    state "NEW" as n0
                    state "PAID" as n1
                    state "SHIPPED" as n2
                    n0 --> n1 : advance
                    n1 --> n2 : advance
                """, renderer.render(stateGraph(false)));
    }

    /** Two machines can share constant names, so each state is named with its enum when there are several. */
    @Test
    void statesOfSeveralMachinesCarryTheirEnumName() {
        String out = renderer.render(stateGraph(true));
        assertTrue(out.contains("state \"Order.NEW\" as n0"), out);
        assertTrue(out.contains("state \"Light.RED\" as n3"), out);
    }

    @Test
    void aModuleGraphIsAFlowchart() {
        assertEquals("""
                flowchart TD
                    n0["app"]
                    n1["core"]
                    n2["java.base"]
                    n3["com.x"]
                    n0 -->|requires| n1
                    n0 -->|requires| n2
                    n0 -.->|exports| n3
                """, renderer.render(moduleGraph()));
    }

    /**
     * Labels come from source code. A quote, a semicolon, a hash, an angle bracket or a pipe
     * would end or corrupt the statement it sits in, so each is written as a Mermaid entity code.
     */
    @Test
    void textThatWouldBreakTheSyntaxIsWrittenAsEntityCodes() {
        Graph graph = new Graph();
        graph.addNode(new Node("a", "MODULE", "we\"ird; #1 <a|b>\nx"));
        graph.addNode(new Node("b", "MODULE", "b"));
        Edge edge = new Edge("e", "a", "b", "REQUIRES");
        graph.addEdge(edge);

        String out = renderer.render(graph);

        assertTrue(out.contains("n0[\"we#quot;ird#59; #35;1 #lt;a#124;b#gt; x\"]"), out);
    }

    @Test
    void theSameGraphGivesTheSameText() {
        assertEquals(renderer.render(classGraph()), renderer.render(classGraph()));
        assertEquals(renderer.render(sequenceGraph()), renderer.render(sequenceGraph()));
    }

    static Graph classGraph() {
        Graph graph = new Graph();
        Node order = new Node("com.x.Order", "CLASS", "Order");
        order.setProperties(java.util.Map.of("fields", "id: long\nitems: List<Item>", "methods", "submit()"));
        graph.addNode(order);
        graph.addNode(new Node("com.x.Payable", "INTERFACE", "Payable"));
        graph.addNode(new Node("com.x.Item", "CLASS", "Item"));
        graph.addNode(new Node("com.y.Base", "CLASS", "Base"));
        graph.addEdge(new Edge("e1", "com.x.Order", "com.x.Payable", "IMPLEMENTS"));
        graph.addEdge(new Edge("e2", "com.x.Order", "com.y.Base", "EXTENDS"));
        Edge has = new Edge("e3", "com.x.Order", "com.x.Item", "HAS");
        has.setLabel("items");
        graph.addEdge(has);
        Group x = new Group("pkg-x", "com.x");
        x.addMember("com.x.Order");
        x.addMember("com.x.Payable");
        x.addMember("com.x.Item");
        graph.addGroup(x);
        return graph;
    }

    static Graph sequenceGraph() {
        Graph graph = new Graph();
        graph.addNode(new Node("A.run", "METHOD", "run"));
        graph.addNode(new Node("B.find", "METHOD", "find"));
        graph.addNode(new Node("A.helper", "METHOD", "helper"));
        graph.addNode(new Node("exception:Boom", "EXCEPTION", "Boom"));
        graph.addEdge(call("c1", "A.run", "B.find", "1"));
        graph.addEdge(call("c2", "A.run", "A.helper", "2"));
        graph.addEdge(new Edge("x1", "B.find", "exception:Boom", "EXCEPTION_PROPAGATION"));
        return graph;
    }

    static Graph stateGraph(boolean twoMachines) {
        Graph graph = new Graph();
        for (String s : java.util.List.of("NEW", "PAID", "SHIPPED")) {
            graph.addNode(new Node("W.Order." + s, "STATE", s));
        }
        graph.addEdge(transition("t1", "W.Order.NEW", "W.Order.PAID"));
        graph.addEdge(transition("t2", "W.Order.PAID", "W.Order.SHIPPED"));
        if (twoMachines) {
            graph.addNode(new Node("W.Light.RED", "STATE", "RED"));
            graph.addNode(new Node("W.Light.GREEN", "STATE", "GREEN"));
            graph.addEdge(transition("t3", "W.Light.RED", "W.Light.GREEN"));
        }
        return graph;
    }

    static Graph moduleGraph() {
        Graph graph = new Graph();
        graph.addNode(new Node("app", "MODULE", "app"));
        graph.addNode(new Node("core", "MODULE", "core"));
        graph.addNode(new Node("java.base", "MODULE", "java.base"));
        graph.addNode(new Node("com.x", "PACKAGE", "com.x"));
        graph.addEdge(new Edge("r1", "app", "core", "REQUIRES"));
        graph.addEdge(new Edge("r2", "app", "java.base", "REQUIRES"));
        graph.addEdge(new Edge("x1", "app", "com.x", "EXPORTS"));
        return graph;
    }

    static Edge call(String id, String from, String to, String label) {
        Edge edge = new Edge(id, from, to, "CALLS");
        edge.setLabel(label);
        return edge;
    }

    static Edge transition(String id, String from, String to) {
        Edge edge = new Edge(id, from, to, "TRANSITION");
        edge.setLabel("advance");
        return edge;
    }
}
