package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;

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
}
