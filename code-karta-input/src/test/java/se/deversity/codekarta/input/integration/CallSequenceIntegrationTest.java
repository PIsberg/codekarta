package se.deversity.codekarta.input.integration;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.CallSequenceParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: Target 3 — Call/Function Sequence Diagram
 * Source: example-shipping-system/src/com/karta/shipping/core/OrderProcessor.java
 */
class CallSequenceIntegrationTest {

    private static final Path ORDER_PROCESSOR = resolveExampleRoot()
            .resolve("src/main/java/com/karta/shipping/core/OrderProcessor.java");
    private static Graph graph;

    @BeforeAll
    static void parse() {
        assumeTrue(Files.exists(ORDER_PROCESSOR),
                "Skipping: OrderProcessor.java not found at " + ORDER_PROCESSOR);
        graph = new CallSequenceParser().parse(ORDER_PROCESSOR);
    }

    // --- Node assertions ---

    @Test
    void orderProcessorClassNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor"),
                "OrderProcessor class node must be present");
    }

    @Test
    void submitMethodNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor.submit"),
                "OrderProcessor.submit method node must be present");
    }

    @Test
    void cancelMethodNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor.cancel"),
                "OrderProcessor.cancel method node must be present");
    }

    // --- Call edge assertions: submit() ---

    @Test
    void submitCallsCheckStock() {
        assertTrue(hasCallEdge("OrderProcessor.submit", "inventoryService.checkStock"),
                "submit() must call inventoryService.checkStock()");
    }

    @Test
    void submitCallsReserveStock() {
        assertTrue(hasCallEdge("OrderProcessor.submit", "inventoryService.reserveStock"),
                "submit() must call inventoryService.reserveStock()");
    }

    @Test
    void submitCallsAreOrderedCorrectly() {
        List<Edge> calls = callEdgesFrom("OrderProcessor.submit");
        assertEquals(2, calls.size(), "submit() must have exactly 2 outgoing CALLS edges");

        List<Edge> ordered = calls.stream()
                .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getLabel())))
                .toList();

        assertTrue(ordered.get(0).getTargetId().contains("checkStock"),
                "first call (label=1) must be checkStock");
        assertTrue(ordered.get(1).getTargetId().contains("reserveStock"),
                "second call (label=2) must be reserveStock");
    }

    // --- Call edge assertions: cancel() ---

    @Test
    void cancelCallsReleaseStock() {
        assertTrue(hasCallEdge("OrderProcessor.cancel", "inventoryService.releaseStock"),
                "cancel() must call inventoryService.releaseStock()");
    }

    @Test
    void callEdgesCarrySequenceLabels() {
        graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .forEach(e -> {
                    assertNotNull(e.getLabel(), "every CALLS edge must have a sequence label");
                    assertDoesNotThrow(() -> Integer.parseInt(e.getLabel()),
                            "sequence label must be an integer");
                });
    }

    // --- helpers ---

    private static Path resolveExampleRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path fromModule = cwd.resolve("../example-shipping-system").normalize();
        if (Files.exists(fromModule)) return fromModule;
        return cwd.resolve("example-shipping-system").normalize();
    }

    private boolean hasCallEdge(String caller, String callee) {
        return graph.getEdges().stream().anyMatch(e ->
                "CALLS".equals(e.getType())
                        && caller.equals(e.getSourceId())
                        && e.getTargetId().contains(callee.contains(".") ? callee.substring(callee.lastIndexOf('.') + 1) : callee)
                        && e.getTargetId().startsWith(callee.contains(".") ? callee.substring(0, callee.lastIndexOf('.')) : ""));
    }

    private List<Edge> callEdgesFrom(String caller) {
        return graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()) && caller.equals(e.getSourceId()))
                .toList();
    }
}
