package com.karta.input.integration;

import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.input.parser.ExceptionFlowParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: Phase 3 — Exception Flow Diagram
 * Source: example-shipping-system/src/…/core/OrderProcessor.java
 *
 * Verified exception chain:
 *   inventoryService.checkStock → (InventoryException caught at catch boundary)
 *   → processOrder re-throws OrderValidationException
 *   → propagates to submitOrder
 *   → propagates out of scope (no in-file caller)
 */
class ExceptionFlowIntegrationTest {

    private static final Path ORDER_PROCESSOR = resolveExampleRoot()
            .resolve("src/main/java/com/karta/shipping/core/OrderProcessor.java");

    private static Graph graph;

    @BeforeAll
    static void parse() {
        assumeTrue(Files.exists(ORDER_PROCESSOR),
                "Skipping: OrderProcessor.java not found at " + ORDER_PROCESSOR);
        graph = new ExceptionFlowParser().parse(ORDER_PROCESSOR);
    }

    // ── Node presence ────────────────────────────────────────────────────────

    @Test
    void orderProcessorClassNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor"),
                "OrderProcessor class node must be present");
    }

    @Test
    void processOrderMethodNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor.processOrder"),
                "OrderProcessor.processOrder method node must be present");
    }

    @Test
    void submitOrderMethodNodePresent() {
        assertNotNull(graph.findNode("OrderProcessor.submitOrder"),
                "OrderProcessor.submitOrder method node must be present");
    }

    @Test
    void exceptionTypeNodePresentForUncaughtPropagation() {
        assertNotNull(graph.findNode("exception:OrderValidationException"),
                "Synthetic exception node for OrderValidationException must be created");
        assertEquals("EXCEPTION",
                graph.findNode("exception:OrderValidationException").getType());
    }

    // ── Catch boundary group ─────────────────────────────────────────────────

    @Test
    void catchBoundaryGroupExistsForProcessOrder() {
        assertFalse(graph.getGroups().isEmpty(),
                "At least one catch boundary group must exist");
    }

    @Test
    void catchBoundaryGroupContainsCheckStock() {
        List<Group> groups = graph.getGroups();
        boolean found = groups.stream().anyMatch(g ->
                g.getMemberIds().stream().anyMatch(m -> m.contains("checkStock")));
        assertTrue(found,
                "A catch boundary group must contain inventoryService.checkStock");
    }

    @Test
    void catchBoundaryGroupLabelMentionsInventoryException() {
        boolean found = graph.getGroups().stream()
                .anyMatch(g -> g.getLabel().contains("InventoryException"));
        assertTrue(found,
                "Catch boundary group label must mention InventoryException");
    }

    // ── EXCEPTION_PROPAGATION edges (the chain) ──────────────────────────────

    @Test
    void processOrderPropagatesExceptionToSubmitOrder() {
        assertTrue(
                hasExceptionEdge("OrderProcessor.processOrder", "OrderProcessor.submitOrder"),
                "processOrder() must propagate OrderValidationException to its caller submitOrder()");
    }

    @Test
    void submitOrderPropagatesExceptionOutOfScope() {
        assertTrue(
                hasExceptionEdge("OrderProcessor.submitOrder", "exception:OrderValidationException"),
                "submitOrder() must propagate exception to the synthetic exception-type node");
    }

    // ── Backward-compat: CALLS edges still present (Phase 2) ─────────────────

    @Test
    void submitStillCallsCheckStock() {
        assertTrue(hasCallEdge("OrderProcessor.submit", "inventoryService.checkStock"),
                "Phase 2 submit() must still have a CALLS edge to checkStock()");
    }

    @Test
    void submitStillCallsReserveStock() {
        assertTrue(hasCallEdge("OrderProcessor.submit", "inventoryService.reserveStock"),
                "Phase 2 submit() must still have a CALLS edge to reserveStock()");
    }

    @Test
    void cancelStillCallsReleaseStock() {
        assertTrue(hasCallEdge("OrderProcessor.cancel", "inventoryService.releaseStock"),
                "Phase 2 cancel() must still have a CALLS edge to releaseStock()");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Path resolveExampleRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path fromModule = cwd.resolve("../example-shipping-system").normalize();
        if (Files.exists(fromModule)) return fromModule;
        return cwd.resolve("example-shipping-system").normalize();
    }

    private boolean hasCallEdge(String caller, String callee) {
        return graph.getEdges().stream().anyMatch(e ->
                EdgeType.CALLS.equals(e.getType())
                        && caller.equals(e.getSourceId())
                        && callee.equals(e.getTargetId()));
    }

    private boolean hasExceptionEdge(String source, String target) {
        return graph.getEdges().stream().anyMatch(e ->
                EdgeType.EXCEPTION_PROPAGATION.equals(e.getType())
                        && source.equals(e.getSourceId())
                        && target.equals(e.getTargetId()));
    }
}
