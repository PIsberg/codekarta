package se.deversity.codekarta.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRendererTest {

    private final JsonRenderer renderer = new JsonRenderer();

    private static Graph sampleGraph() {
        Graph graph = new Graph();
        Node order = new Node("com.example.Order", "CLASS", "Order");
        order.getProperties().put("zeta", "last");
        order.getProperties().put("alpha", "first");
        graph.addNode(order);
        graph.addNode(new Node("com.example.Line", "CLASS", "Line"));
        graph.addEdge(new Edge("e1", "com.example.Order", "com.example.Line", "HAS"));
        Group pkg = new Group("com.example", "com.example");
        pkg.addMember("com.example.Order");
        graph.addGroup(pkg);
        return graph;
    }

    @Test
    void writesTheNodesEdgesAndGroups() throws Exception {
        String json = renderer.render(sampleGraph());

        Map<?, ?> parsed = new ObjectMapper().readValue(json, Map.class);
        assertEquals(2, ((java.util.List<?>) parsed.get("nodes")).size());
        assertEquals(1, ((java.util.List<?>) parsed.get("edges")).size());
        assertEquals(1, ((java.util.List<?>) parsed.get("groups")).size());
    }

    @Test
    void roundTripsBackIntoAGraph() throws Exception {
        String json = renderer.render(sampleGraph());

        Graph back = new ObjectMapper().readValue(json, Graph.class);

        assertEquals(2, back.getNodes().size());
        assertNotNull(back.findNode("com.example.Order"), "ids must survive the round trip");
        assertEquals("Order", back.findNode("com.example.Order").getLabel());
        assertEquals("HAS", back.getEdges().get(0).getType());
        assertEquals("com.example", back.getGroups().get(0).getLabel());
    }

    @Test
    void omitsCoordinatesUntilLayoutHasRun() {
        String json = renderer.render(sampleGraph());

        assertFalse(json.contains("\"x\""),
                "an unlaid-out graph has null coordinates, and the model omits nulls");
    }

    @Test
    void includesCoordinatesOnceTheyAreSet() {
        Graph graph = sampleGraph();
        graph.findNode("com.example.Order").setX(12.5);
        graph.findNode("com.example.Order").setY(34.0);

        String json = renderer.render(graph);

        assertTrue(json.contains("\"x\" : 12.5"), "actual: " + json);
        assertTrue(json.contains("\"y\" : 34.0"));
    }

    // The CLI writes this to a file that a repository can commit, and KartaCli.run is documented
    // as idempotent. Two things would quietly break that: HashMap iteration order for the
    // properties maps, and DefaultPrettyPrinter's system line separator.

    @Test
    void isByteIdenticalAcrossRuns() {
        Graph graph = sampleGraph();

        assertEquals(renderer.render(graph), new JsonRenderer().render(graph),
                "two renders of the same graph must be byte-identical");
    }

    @Test
    void sortsPropertyKeysRatherThanUsingHashOrder() {
        String json = renderer.render(sampleGraph());

        assertTrue(json.indexOf("\"alpha\"") < json.indexOf("\"zeta\""),
                "map keys must be sorted, not in HashMap iteration order. actual: " + json);
    }

    @Test
    void usesUnixLineEndingsRegardlessOfPlatform() {
        String json = renderer.render(sampleGraph());

        assertFalse(json.contains("\r"),
                "a \r would make the output differ between a Windows and a Linux build");
        assertTrue(json.endsWith("\n"), "files end with a newline");
    }

    @Test
    void rendersAnEmptyGraphAsEmptyCollections() throws Exception {
        String json = renderer.render(new Graph());

        Map<?, ?> parsed = new ObjectMapper().readValue(json, Map.class);
        assertTrue(((java.util.List<?>) parsed.get("nodes")).isEmpty());
        assertTrue(((java.util.List<?>) parsed.get("edges")).isEmpty());
        assertTrue(((java.util.List<?>) parsed.get("groups")).isEmpty());
    }

    @Test
    void keepsPropertiesThroughTheRoundTrip() throws Exception {
        Graph graph = new Graph();
        Node node = new Node("A", "CLASS", "A");
        node.setProperties(new LinkedHashMap<>(Map.of("stereotype", "entity")));
        graph.addNode(node);

        Graph back = new ObjectMapper().readValue(renderer.render(graph), Graph.class);

        assertEquals("entity", back.findNode("A").getProperties().get("stereotype"));
    }
}
