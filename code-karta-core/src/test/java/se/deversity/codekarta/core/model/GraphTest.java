package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    @Test
    void startsEmpty() {
        Graph graph = new Graph();
        assertTrue(graph.getNodes().isEmpty());
        assertTrue(graph.getEdges().isEmpty());
        assertTrue(graph.getGroups().isEmpty());
    }

    @Test
    void addNodeAppendsNode() {
        Graph graph = new Graph();
        graph.addNode(new Node("n1", "CLASS", "A"));
        assertEquals(1, graph.getNodes().size());
    }

    @Test
    void addNodeIfAbsentSkipsDuplicateId() {
        Graph graph = new Graph();
        graph.addNode(new Node("n1", "CLASS", "A"));
        graph.addNodeIfAbsent(new Node("n1", "CLASS", "A-duplicate"));
        assertEquals(1, graph.getNodes().size());
        assertEquals("A", graph.findNode("n1").getLabel());
    }

    @Test
    void addNodeIfAbsentAddsNewId() {
        Graph graph = new Graph();
        graph.addNode(new Node("n1", "CLASS", "A"));
        graph.addNodeIfAbsent(new Node("n2", "CLASS", "B"));
        assertEquals(2, graph.getNodes().size());
    }

    @Test
    void findNodeReturnsCorrectNode() {
        Graph graph = new Graph();
        graph.addNode(new Node("n1", "CLASS", "A"));
        graph.addNode(new Node("n2", "INTERFACE", "B"));
        Node found = graph.findNode("n2");
        assertNotNull(found);
        assertEquals("INTERFACE", found.getType());
    }

    @Test
    void findNodeReturnsNullForMissingId() {
        Graph graph = new Graph();
        assertNull(graph.findNode("nonexistent"));
    }

    @Test
    void addEdgeAppendsEdge() {
        Graph graph = new Graph();
        graph.addEdge(new Edge("e1", "A", "B", "EXTENDS"));
        assertEquals(1, graph.getEdges().size());
    }

    @Test
    void addGroupAppendsGroup() {
        Graph graph = new Graph();
        graph.addGroup(new Group("g1", "pkg"));
        assertEquals(1, graph.getGroups().size());
    }

    @Test
    void jsonRoundTripPreservesStructure() throws Exception {
        Graph original = new Graph();
        original.addNode(new Node("n1", "CLASS", "A"));
        original.addEdge(new Edge("e1", "n1", "n2", "EXTENDS"));
        Group group = new Group("g1", "pkg");
        group.addMember("n1");
        original.addGroup(group);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(original);
        Graph restored = mapper.readValue(json, Graph.class);

        assertEquals(1, restored.getNodes().size());
        assertEquals("n1", restored.getNodes().get(0).getId());
        assertEquals(1, restored.getEdges().size());
        assertEquals(1, restored.getGroups().size());
    }

    // The index behind findNode and addNodeIfAbsent is derived state. These pin the behaviour a
    // caller can observe, so the index can be reshaped without the tests needing to change.

    @Test
    void findNodeReturnsTheFirstOfTwoNodesSharingAnId() {
        Graph graph = new Graph();
        Node first = new Node("dup", "CLASS", "first");
        Node second = new Node("dup", "CLASS", "second");
        graph.addNode(first);
        graph.addNode(second);

        assertSame(first, graph.findNode("dup"),
                "findNode has always returned the first match, and addNode permits duplicate ids");
        assertEquals(2, graph.getNodes().size(), "both nodes stay in the list");
    }

    @Test
    void addNodeIfAbsentIgnoresASecondNodeWithTheSameId() {
        Graph graph = new Graph();
        Node first = new Node("A", "CLASS", "first");
        graph.addNodeIfAbsent(first);
        graph.addNodeIfAbsent(new Node("A", "INTERFACE", "second"));

        assertEquals(1, graph.getNodes().size());
        assertSame(first, graph.findNode("A"), "the first node wins");
    }

    @Test
    void findNodeSeesNodesAddedThroughTheListReturnedByGetNodes() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));

        graph.getNodes().add(new Node("B", "CLASS", "B"));

        assertNotNull(graph.findNode("B"),
                "a node appended to the exposed list must still be findable");
        assertNotNull(graph.findNode("A"), "and the earlier node must not be lost");
    }

    @Test
    void findNodeSeesNodesRemovedThroughTheListReturnedByGetNodes() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));

        graph.getNodes().removeIf(n -> "A".equals(n.getId()));

        assertNull(graph.findNode("A"), "a removed node must not still be findable");
        assertNotNull(graph.findNode("B"));
    }

    @Test
    void setNodesReplacesWhatFindNodeSees() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));
        graph.addNode(new Node("B", "CLASS", "B"));

        // Same length as before, so length alone cannot tell the graph anything changed.
        graph.setNodes(new java.util.ArrayList<>(List.of(
                new Node("C", "CLASS", "C"), new Node("D", "CLASS", "D"))));

        assertNull(graph.findNode("A"), "nodes replaced by setNodes must be gone");
        assertNull(graph.findNode("B"));
        assertNotNull(graph.findNode("C"));
        assertNotNull(graph.findNode("D"));
    }

    @Test
    void findNodeReturnsNullForAnUnknownId() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", "CLASS", "A"));

        assertNull(graph.findNode("nope"));
    }

    // The index exists to keep graph construction linear. Every parser calls addNodeIfAbsent once
    // per class, method and call site, and both layout engines call findNode once per node, so a
    // linear scan made a large codebase quadratic: measured on this machine at 20k nodes, the
    // scan took 1417 ms to build and 7492 ms to look up every node.
    //
    // The bound is deliberately loose. It is not a performance assertion, it is a guard that fails
    // if someone reintroduces the scan; quadratic at this size is tens of seconds, not five.
    @Test
    void buildingAndSearchingALargeGraphStaysLinear() {
        int size = 20_000;
        Graph graph = new Graph();

        long start = System.nanoTime();
        for (int i = 0; i < size; i++) {
            graph.addNodeIfAbsent(new Node("n" + i, "CLASS", "n" + i));
        }
        for (int i = 0; i < size; i++) {
            assertNotNull(graph.findNode("n" + i));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(size, graph.getNodes().size());
        assertTrue(elapsedMs < 5_000,
                "building and searching " + size + " nodes took " + elapsedMs
                        + " ms, which means lookups are no longer O(1)");
    }
}
