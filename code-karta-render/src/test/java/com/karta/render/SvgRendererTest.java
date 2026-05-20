package com.karta.render;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;
import se.deversity.vibetags.annotations.AIParallelTests;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@AIParallelTests
class SvgRendererTest {

    private final SvgRenderer renderer = new SvgRenderer();

    @Test
    void outputStartsWithXmlDeclaration() {
        Graph graph = new Graph();
        String svg = renderer.render(graph);
        assertTrue(svg.startsWith("<?xml"), "output must start with XML declaration");
    }

    @Test
    void outputContainsSvgRootElement() {
        Graph graph = new Graph();
        String svg = renderer.render(graph);
        assertTrue(svg.contains("<svg "), "output must contain <svg> element");
        assertTrue(svg.endsWith("</svg>"), "output must end with </svg>");
    }

    @Test
    void rendersLaidOutNodeAsRect() {
        Graph graph = new Graph();
        Node node = new Node("A", "CLASS", "MyClass");
        node.setX(20.0); node.setY(20.0); node.setWidth(150.0); node.setHeight(50.0);
        graph.addNode(node);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("class=\"node-rect\""), "node rect must carry the contract CSS class");
        assertTrue(svg.contains("MyClass"), "node label must appear in output");
    }

    @Test
    void rendersNodeTooltip() {
        Graph graph = new Graph();
        Node node = new Node("A", "CLASS", "MyClass");
        node.setX(20.0); node.setY(20.0); node.setWidth(150.0); node.setHeight(50.0);
        graph.addNode(node);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("<title>"), "<title> tooltip must be present");
        assertTrue(svg.contains("MyClass"), "tooltip must contain the node label");
    }

    @Test
    void skipsNodeWithoutLayoutCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("Unlaid", "CLASS", "Unlaid"));

        String svg = renderer.render(graph);

        assertFalse(svg.contains("Unlaid"), "nodes without coordinates must be skipped");
    }

    @Test
    void rendersEdgePath() {
        Graph graph = new Graph();
        Node src = laid("src", "Source", 20, 20);
        Node tgt = laid("tgt", "Target", 20, 200);
        graph.addNode(src);
        graph.addNode(tgt);
        graph.addEdge(new Edge("e1", "src", "tgt", "EXTENDS"));

        String svg = renderer.render(graph);

        assertTrue(svg.contains("class=\"edge-line\""), "edge must carry the contract CSS class");
    }

    @Test
    void skipsEdgeWhenSourceNotLaidOut() {
        Graph graph = new Graph();
        graph.addNode(new Node("src", "CLASS", "Source")); // no coordinates
        Node tgt = laid("tgt", "Target", 20, 150);
        graph.addNode(tgt);
        graph.addEdge(new Edge("e1", "src", "tgt", "EXTENDS"));

        String svg = renderer.render(graph);

        assertFalse(svg.contains("class=\"edge-line\""), "edge with unlaid source must not produce an edge-line element");
    }

    @Test
    void rendersEdgeLabelWhenPresent() {
        Graph graph = new Graph();
        Node src = laid("src", "A", 20, 20);
        Node tgt = laid("tgt", "B", 20, 200);
        graph.addNode(src);
        graph.addNode(tgt);
        Edge edge = new Edge("e1", "src", "tgt", "CALLS");
        edge.setLabel("1");
        graph.addEdge(edge);

        String svg = renderer.render(graph);

        assertTrue(svg.contains(">1<"), "edge label text must appear in output");
    }

    @Test
    void rendersGroupRectForLaidOutMembers() {
        Graph graph = new Graph();
        Node n = laid("n1", "A", 40, 40);
        graph.addNode(n);
        Group group = new Group("g1", "com.karta");
        group.addMember("n1");
        graph.addGroup(group);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("group-rect"), "group must render a rect with group-rect class");
        assertTrue(svg.contains("com.karta"), "group label must appear");
    }

    @Test
    void escapesXmlSpecialCharsInLabel() {
        Graph graph = new Graph();
        Node node = new Node("id", "CLASS", "<Alert & 'test'>");
        node.setX(10.0); node.setY(10.0); node.setWidth(150.0); node.setHeight(50.0);
        graph.addNode(node);

        String svg = renderer.render(graph);

        assertFalse(svg.contains("<Alert"), "raw < must be escaped");
        assertTrue(svg.contains("&lt;Alert"), "< must be escaped as &lt;");
        assertTrue(svg.contains("&amp;"), "& must be escaped");
    }

    @Test
    void acceptsCustomCssStyle() {
        Graph graph = new Graph();
        Node node = laid("n1", "X", 20, 20);
        graph.addNode(node);

        String customCss = ".node-rect { fill: red; }";
        String svg = renderer.render(graph, customCss);

        assertTrue(svg.contains("fill: red"), "custom CSS must be injected");
    }

    @Test
    void rendersClickableAttributionInBottomRightOutsideContent() {
        Graph graph = new Graph();
        Node node = laid("n1", "X", 20, 20);
        graph.addNode(node);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("Created with https://github.com/PIsberg/codekarta"),
                "attribution text must be rendered");
        assertTrue(svg.contains("href=\"https://github.com/PIsberg/codekarta\""),
                "attribution must link to the project repository");
        assertTrue(svg.contains("class=\"diagram-attribution\""),
                "attribution must expose a stable CSS class");
        assertTrue(svg.contains("text-anchor=\"end\""),
                "attribution must be anchored to the right edge");
        assertTrue(svg.contains("text-decoration=\"underline\""),
                "attribution must be visibly underlined");
        assertTrue(svg.indexOf("Created with https://github.com/PIsberg/codekarta") > svg.indexOf("class=\"node-rect\""),
                "attribution must be rendered after diagram content");
    }

    @Test
    void rendersPerColorArrowMarker() {
        Graph graph = new Graph();
        Node src = laid("src", "A", 20, 20);
        Node tgt = laid("tgt", "B", 20, 200);
        graph.addNode(src);
        graph.addNode(tgt);
        graph.addEdge(new Edge("e1", "src", "tgt", "IMPLEMENTS"));

        String svg = renderer.render(graph);

        // IMPLEMENTS → color #1d4ed8, hollow triangle marker
        assertTrue(svg.contains("marker-hollow-1d4ed8"), "per-color hollow marker must be generated for IMPLEMENTS");
        assertTrue(svg.contains("url(#marker-hollow-1d4ed8)"), "edge must reference its per-color marker");
    }

    @Test
    void rendersCompartmentsForNodeWithProperties() {
        Graph graph = new Graph();
        Node node = new Node("W", "CLASS", "Widget");
        node.setX(20.0); node.setY(20.0); node.setWidth(180.0); node.setHeight(70.0);
        node.setProperties(Map.of(
            "fields", "name: String\ncount: int",
            "methods", "getName(): String"
        ));
        graph.addNode(node);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("name: String"), "fields compartment must appear");
        assertTrue(svg.contains("getName"), "methods compartment must appear");
        // Divider line between header and fields
        assertTrue(svg.contains("stroke-width=\"0.8\""), "compartment divider line must be present");
    }

    @Test
    void doesNotDetectClassGraphAsInteraction() {
        Graph graph = new Graph();
        Node a = laid("A", "ClassA", 20, 20);
        Node b = laid("B", "ClassB", 20, 200);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge(new Edge("e1", "A", "B", "EXTENDS"));

        assertFalse(renderer.isInteractionGraph(graph), "CLASS/EXTENDS graph must not be detected as interaction");
    }

    @Test
    void detectsInteractionGraph() {
        Graph graph = new Graph();
        Node caller = methodNode("A.doThing", "doThing", 20, 20);
        Node callee = methodNode("B.helper", "helper", 20, 200);
        graph.addNode(caller);
        graph.addNode(callee);
        Edge e = new Edge("c1", "A.doThing", "B.helper", "CALLS");
        e.setLabel("1");
        graph.addEdge(e);

        assertTrue(renderer.isInteractionGraph(graph), "METHOD graph with integer CALLS labels must be detected as interaction");
    }

    // --- helpers ---

    private Node laid(String id, String label, double x, double y) {
        Node n = new Node(id, "CLASS", label);
        n.setX(x); n.setY(y); n.setWidth(150.0); n.setHeight(50.0);
        return n;
    }

    private Node methodNode(String id, String label, double x, double y) {
        Node n = new Node(id, "METHOD", label);
        n.setX(x); n.setY(y); n.setWidth(150.0); n.setHeight(50.0);
        return n;
    }
}
