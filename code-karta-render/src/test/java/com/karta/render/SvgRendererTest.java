package com.karta.render;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(svg.contains("<rect"), "node must produce a <rect> element");
        assertTrue(svg.contains("MyClass"), "node label must appear in output");
    }

    @Test
    void skipsNodeWithoutLayoutCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("Unlaid", "CLASS", "Unlaid"));

        String svg = renderer.render(graph);

        assertFalse(svg.contains("Unlaid"), "nodes without coordinates must be skipped");
    }

    @Test
    void rendersEdgeAsLine() {
        Graph graph = new Graph();
        Node src = laid("src", "Source", 20, 20);
        Node tgt = laid("tgt", "Target", 20, 150);
        graph.addNode(src);
        graph.addNode(tgt);
        graph.addEdge(new Edge("e1", "src", "tgt", "EXTENDS"));

        String svg = renderer.render(graph);

        assertTrue(svg.contains("<line"), "edge must produce a <line> element");
    }

    @Test
    void skipsEdgeWhenSourceNotLaidOut() {
        Graph graph = new Graph();
        graph.addNode(new Node("src", "CLASS", "Source")); // no coordinates
        Node tgt = laid("tgt", "Target", 20, 150);
        graph.addNode(tgt);
        graph.addEdge(new Edge("e1", "src", "tgt", "EXTENDS"));

        String svg = renderer.render(graph);

        assertFalse(svg.contains("<line"), "edge with unlaid source must be skipped");
    }

    @Test
    void rendersEdgeLabelWhenPresent() {
        Graph graph = new Graph();
        Node src = laid("src", "A", 20, 20);
        Node tgt = laid("tgt", "B", 20, 150);
        graph.addNode(src);
        graph.addNode(tgt);
        Edge edge = new Edge("e1", "src", "tgt", "CALLS");
        edge.setLabel("1");
        graph.addEdge(edge);

        String svg = renderer.render(graph);

        assertTrue(svg.contains(">1<"), "edge label must appear in output");
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

        assertTrue(svg.contains("group-rect"), "group must render a dashed rect");
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

    // --- helpers ---

    private Node laid(String id, String label, double x, double y) {
        Node n = new Node(id, "CLASS", label);
        n.setX(x); n.setY(y); n.setWidth(150.0); n.setHeight(50.0);
        return n;
    }
}
