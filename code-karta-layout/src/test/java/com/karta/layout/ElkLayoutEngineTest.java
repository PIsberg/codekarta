package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElkLayoutEngineTest {

    private final LayoutEngine engine = new ElkLayoutEngine();

    @Test
    void assignsCoordinatesToSingleNode() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));

        engine.layout(graph);

        Node a = graph.findNode("A");
        assertNotNull(a.getX(),      "ELK must assign x");
        assertNotNull(a.getY(),      "ELK must assign y");
        assertNotNull(a.getWidth(),  "ELK must assign width");
        assertNotNull(a.getHeight(), "ELK must assign height");
    }

    @Test
    void assignsPositiveCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));
        graph.addEdge(new Edge("a-b", "A", "B", "EXTENDS"));

        engine.layout(graph);

        for (Node node : graph.getNodes()) {
            assertTrue(node.getX() >= 0, "x must be non-negative");
            assertTrue(node.getY() >= 0, "y must be non-negative");
            assertTrue(node.getWidth()  > 0, "width must be positive");
            assertTrue(node.getHeight() > 0, "height must be positive");
        }
    }

    @Test
    void parentPlacedAboveChild() {
        Graph graph = new Graph();
        graph.addNode(new Node("Parent", "CLASS", "Parent"));
        graph.addNode(new Node("Child",  "CLASS", "Child"));
        graph.addEdge(new Edge("p-c", "Parent", "Child", "EXTENDS"));

        engine.layout(graph);

        Node parent = graph.findNode("Parent");
        Node child  = graph.findNode("Child");
        assertNotEquals(parent.getY(), child.getY(),
                "ELK must place parent and child at different vertical positions");
    }

    @Test
    void siblingsReceiveDifferentXCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("Root",  "CLASS", "Root"));
        graph.addNode(new Node("Left",  "CLASS", "Left"));
        graph.addNode(new Node("Right", "CLASS", "Right"));
        graph.addEdge(new Edge("r-l", "Root", "Left",  "EXTENDS"));
        graph.addEdge(new Edge("r-r", "Root", "Right", "EXTENDS"));

        engine.layout(graph);

        Node left  = graph.findNode("Left");
        Node right = graph.findNode("Right");
        assertNotEquals(left.getX(), right.getX(),
                "siblings must have different x coordinates");
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
    void handlesDisconnectedNodes() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));
        graph.addNode(new Node("C", "CLASS", "C"));

        engine.layout(graph);

        for (Node node : graph.getNodes()) {
            assertNotNull(node.getX(), "every node must receive x after ELK layout");
        }
    }

    @Test
    void handlesSelfLoopEdgeWithoutCrashing() {
        Graph graph = new Graph();
        graph.addNode(new Node("Self", "CLASS", "Self"));
        graph.addEdge(new Edge("self-loop", "Self", "Self", "CALLS"));

        assertDoesNotThrow(() -> engine.layout(graph),
                "self-loop edge must not crash ELK layout");
    }

    @Test
    void groupMembersReceiveAbsoluteCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));
        graph.addEdge(new Edge("a-b", "A", "B", "EXTENDS"));
        Group group = new Group("pkg", "com.example");
        group.addMember("A");
        group.addMember("B");
        graph.addGroup(group);

        engine.layout(graph);

        Node a = graph.findNode("A");
        Node b = graph.findNode("B");
        assertNotNull(a.getX(), "grouped node A must receive x");
        assertNotNull(a.getY(), "grouped node A must receive y");
        assertNotNull(b.getX(), "grouped node B must receive x");
        assertNotNull(b.getY(), "grouped node B must receive y");
        assertTrue(a.getX() >= 0 && a.getY() >= 0, "A coordinates must be non-negative (absolute)");
        assertTrue(b.getX() >= 0 && b.getY() >= 0, "B coordinates must be non-negative (absolute)");
        // At least one axis must differ when there are two siblings in the group
        assertTrue(a.getX() != b.getX() || a.getY() != b.getY(),
                "group members must not occupy identical positions");
    }

    @Test
    void groupedAndUngroupedNodesAllReceiveCoordinates() {
        Graph graph = new Graph();
        graph.addNode(new Node("Grouped", "CLASS", "Grouped"));
        graph.addNode(new Node("Free",    "CLASS", "Free"));
        Group group = new Group("g1", "group-one");
        group.addMember("Grouped");
        graph.addGroup(group);

        engine.layout(graph);

        assertNotNull(graph.findNode("Grouped").getX(), "grouped node must receive x");
        assertNotNull(graph.findNode("Free").getX(),    "ungrouped node must receive x");
    }
}
