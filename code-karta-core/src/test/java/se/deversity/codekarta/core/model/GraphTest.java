package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
