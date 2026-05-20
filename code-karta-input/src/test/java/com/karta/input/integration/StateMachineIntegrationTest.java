package com.karta.input.integration;

import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import com.karta.input.parser.StateMachineParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: State Machine Diagrams
 *
 * <p>Parses the three example state machine files under
 * {@code example-shipping-system/src/main/java/com/karta/shipping/state/}
 * and verifies the STATE nodes and TRANSITION edges produced by
 * {@link StateMachineParser} for each of the three supported patterns:</p>
 *
 * <ol>
 *   <li>{@code ShipmentLifecycle} — switch-case transitions</li>
 *   <li>{@code PaymentWorkflow}   — explicit {@code transition(from, to, event)} DSL</li>
 *   <li>{@code InventoryReservation} — linear sequential state assignments</li>
 * </ol>
 */
class StateMachineIntegrationTest {

    private static final Path STATE_DIR = resolveExampleRoot()
            .resolve("src/main/java/com/karta/shipping/state");

    /** Parsed separately per file so name collisions can't obscure failures. */
    private static Graph shipmentGraph;
    private static Graph paymentGraph;
    private static Graph inventoryGraph;

    @BeforeAll
    static void parse() {
        assumeTrue(Files.exists(STATE_DIR),
                "Skipping: state directory not found at " + STATE_DIR);

        StateMachineParser parser = new StateMachineParser();
        shipmentGraph  = parser.parse(STATE_DIR.resolve("ShipmentLifecycle.java"));
        paymentGraph   = parser.parse(STATE_DIR.resolve("PaymentWorkflow.java"));
        inventoryGraph = parser.parse(STATE_DIR.resolve("InventoryReservation.java"));
    }

    // ── ShipmentLifecycle: switch-case pattern ───────────────────────────────

    @Test
    void shipmentEnumConstantsAreStateNodes() {
        for (String s : new String[]{"CREATED", "PROCESSING", "IN_TRANSIT", "DELIVERED", "FAILED", "CANCELLED"}) {
            Node n = shipmentGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void shipmentAdvanceHappyPath() {
        assertTransition(shipmentGraph, "CREATED",    "PROCESSING", "advance");
        assertTransition(shipmentGraph, "PROCESSING", "IN_TRANSIT", "advance");
        assertTransition(shipmentGraph, "IN_TRANSIT", "DELIVERED",  "advance");
    }

    @Test
    void shipmentFailTransitions() {
        assertTransition(shipmentGraph, "PROCESSING", "FAILED", "fail");
        assertTransition(shipmentGraph, "IN_TRANSIT", "FAILED", "fail");
    }

    @Test
    void shipmentCancelTransitions() {
        assertTransition(shipmentGraph, "CREATED",    "CANCELLED", "cancel");
        assertTransition(shipmentGraph, "PROCESSING", "CANCELLED", "cancel");
    }

    @Test
    void shipmentNoSpuriousTransitions() {
        // 7 expected: 3 advance + 2 fail + 2 cancel
        long count = transitionCount(shipmentGraph);
        assertEquals(7, count, "unexpected number of TRANSITION edges in shipment graph");
    }

    // ── PaymentWorkflow: explicit transition() DSL ───────────────────────────

    @Test
    void paymentEnumConstantsAreStateNodes() {
        for (String s : new String[]{"PENDING", "AUTHORIZING", "AUTHORIZED", "CAPTURING", "CAPTURED", "REFUNDED", "DECLINED"}) {
            Node n = paymentGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void paymentDslTransitionsWithEventLabels() {
        assertTransition(paymentGraph, "PENDING",     "AUTHORIZING", "initiate");
        assertTransition(paymentGraph, "AUTHORIZING", "AUTHORIZED",  "authOk");
        assertTransition(paymentGraph, "AUTHORIZING", "DECLINED",    "authDenied");
        assertTransition(paymentGraph, "AUTHORIZED",  "CAPTURING",   "capture");
        assertTransition(paymentGraph, "CAPTURING",   "CAPTURED",    "captureOk");
        assertTransition(paymentGraph, "CAPTURING",   "DECLINED",    "captureFailed");
        assertTransition(paymentGraph, "CAPTURED",    "REFUNDED",    "refund");
    }

    @Test
    void paymentTransitionCount() {
        assertEquals(7, transitionCount(paymentGraph),
                "configure() registers exactly 7 transitions");
    }

    // ── InventoryReservation: linear assignment pattern ──────────────────────

    @Test
    void inventoryEnumConstantsAreStateNodes() {
        for (String s : new String[]{"IDLE", "CHECKING", "RESERVED", "ALLOCATED", "COMMITTED", "RELEASED"}) {
            Node n = inventoryGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void inventoryReservationChain() {
        // Field initialiser IDLE drives the first link; each subsequent assignment extends the chain
        assertTransition(inventoryGraph, "IDLE",      "CHECKING",  "processReservation");
        assertTransition(inventoryGraph, "CHECKING",  "RESERVED",  "processReservation");
        assertTransition(inventoryGraph, "RESERVED",  "ALLOCATED", "processReservation");
        assertTransition(inventoryGraph, "ALLOCATED", "COMMITTED", "processReservation");
    }

    @Test
    void inventoryReleaseTransition() {
        // Field initialiser IDLE is the implicit prev for single-assignment methods
        assertTransition(inventoryGraph, "IDLE", "RELEASED", "release");
    }

    // ── Directory parse combines all three files ─────────────────────────────

    @Test
    void directoryParseContainsAllThreeMachines() {
        StateMachineParser parser = new StateMachineParser();
        Graph combined = parser.parse(STATE_DIR);

        // spot-check one node from each machine (names are unique across files)
        assertNotNull(combined.findNode("CREATED"),    "shipment state missing from combined graph");
        assertNotNull(combined.findNode("PENDING"),    "payment state missing from combined graph");
        assertNotNull(combined.findNode("CHECKING"),   "inventory state missing from combined graph");

        // at least 7+7+5 = 19 transitions
        assertTrue(transitionCount(combined) >= 19,
                "combined graph should have at least 19 TRANSITION edges");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Path resolveExampleRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path fromModule = cwd.resolve("../example-shipping-system").normalize();
        if (Files.exists(fromModule)) return fromModule;
        return cwd.resolve("example-shipping-system").normalize();
    }

    private void assertTransition(Graph graph, String src, String tgt, String label) {
        boolean found = graph.getEdges().stream()
                .filter(e -> "TRANSITION".equals(e.getType()))
                .anyMatch(e -> src.equals(e.getSourceId())
                        && tgt.equals(e.getTargetId())
                        && label.equals(e.getLabel()));
        assertTrue(found, "Missing transition " + src + " →[" + label + "]→ " + tgt);
    }

    private long transitionCount(Graph graph) {
        return graph.getEdges().stream()
                .filter(e -> "TRANSITION".equals(e.getType()))
                .count();
    }
}
