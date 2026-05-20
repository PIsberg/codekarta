package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleLayoutEngineTest {

    private final LayoutEngine engine = new SimpleLayoutEngine();

    @Test
    void assignsCoordinatesToSingleNode() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));

        engine.layout(graph);

        Node a = graph.findNode("A");
        assertNotNull(a.getX());
        assertNotNull(a.getY());
        assertNotNull(a.getWidth());
        assertNotNull(a.getHeight());
    }

    @Test
    void assignsPositiveCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));

        engine.layout(graph);

        for (Node node : graph.getNodes()) {
            assertTrue(node.getX() >= 0, "x must be non-negative");
            assertTrue(node.getY() >= 0, "y must be non-negative");
            assertTrue(node.getWidth() > 0, "width must be positive");
            assertTrue(node.getHeight() > 0, "height must be positive");
        }
    }

    @Test
    void rootNodeIsOnHigherRowThanChild() {
        Graph graph = new Graph();
        graph.addNode(new Node("Parent", "CLASS", "Parent"));
        graph.addNode(new Node("Child", "CLASS", "Child"));
        graph.addEdge(new Edge("p-c", "Parent", "Child", "EXTENDS"));

        engine.layout(graph);

        Node parent = graph.findNode("Parent");
        Node child = graph.findNode("Child");
        assertTrue(child.getY() > parent.getY(),
                "child should be placed lower (higher Y) than its parent");
    }

    @Test
    void siblingsOnSameRowHaveDifferentX() {
        Graph graph = new Graph();
        graph.addNode(new Node("Root", "CLASS", "Root"));
        graph.addNode(new Node("Left", "CLASS", "Left"));
        graph.addNode(new Node("Right", "CLASS", "Right"));
        graph.addEdge(new Edge("r-l", "Root", "Left", "EXTENDS"));
        graph.addEdge(new Edge("r-r", "Root", "Right", "EXTENDS"));

        engine.layout(graph);

        Node left = graph.findNode("Left");
        Node right = graph.findNode("Right");
        assertNotEquals(left.getX(), right.getX(),
                "siblings at the same depth must have different x");
        assertEquals(left.getY(), right.getY(),
                "siblings at the same depth must share the same y");
    }

    @Test
    void returnsGraphInstance() {
        Graph graph = new Graph();
        graph.addNode(new Node("X", "CLASS", "X"));
        Graph result = engine.layout(graph);
        assertSame(graph, result, "layout must return the same graph instance");
    }

    @Test
    void handlesEmptyGraph() {
        Graph graph = new Graph();
        assertDoesNotThrow(() -> engine.layout(graph));
    }

    @Test
    void handlesGraphWithNoEdges() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));
        graph.addNode(new Node("C", "CLASS", "C"));

        engine.layout(graph);

        for (Node node : graph.getNodes()) {
            assertNotNull(node.getX());
        }
    }

    @Test
    void compartmentNodesDoNotOverlapNextRow() {
        // Parent has 6 fields + 6 methods — estimated height:
        //   44 (header) + 13 + 6×15 (fields section) + 13 + 6×15 (methods section) = 250 px
        Graph graph = new Graph();
        Node parent = new Node("Parent", "CLASS", "Parent");
        parent.setProperties(Map.of(
            "fields",  "a: int\nb: int\nc: int\nd: int\ne: int\nf: int",
            "methods", "getA(): int\ngetB(): int\ngetC(): int\ngetD(): int\ngetE(): int\ngetF(): int"
        ));
        Node child = new Node("Child", "CLASS", "Child");
        graph.addNode(parent);
        graph.addNode(child);
        graph.addEdge(new Edge("p-c", "Parent", "Child", "EXTENDS"));

        SimpleLayoutEngine eng = new SimpleLayoutEngine();
        eng.layout(graph);

        double estimatedParentHeight = eng.estimateRenderHeight(parent); // 250
        double parentBottom = parent.getY() + estimatedParentHeight;
        assertTrue(child.getY() >= parentBottom,
                "child row top (" + child.getY() + ") must not overlap parent's rendered bottom ("
                        + parentBottom + ")");
    }

    @Test
    void estimateRenderHeightReturnsDefaultForPlainNode() {
        SimpleLayoutEngine eng = new SimpleLayoutEngine();
        Node node = new Node("X", "CLASS", "X");
        assertEquals(70.0, eng.estimateRenderHeight(node), 0.001,
                "plain node with no compartments should return NODE_HEIGHT (70)");
    }

    @Test
    void estimateRenderHeightIncludesFieldsAndMethods() {
        SimpleLayoutEngine eng = new SimpleLayoutEngine();
        Node node = new Node("X", "CLASS", "X");
        node.setProperties(Map.of(
            "fields",  "a: int\nb: int",      // 2 lines
            "methods", "foo(): void"           // 1 line
        ));
        // 44 + (13 + 2×15) + (13 + 1×15) = 44 + 43 + 28 = 115
        assertEquals(115.0, eng.estimateRenderHeight(node), 0.001);
    }
}
