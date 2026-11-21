package se.deversity.codekarta.input.integration;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.parser.StateMachineParser;
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
        for (String s : new String[]{"ShipmentLifecycle.State.CREATED", "ShipmentLifecycle.State.PROCESSING", "ShipmentLifecycle.State.IN_TRANSIT", "ShipmentLifecycle.State.DELIVERED", "ShipmentLifecycle.State.FAILED", "ShipmentLifecycle.State.CANCELLED"}) {
            Node n = shipmentGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void shipmentAdvanceHappyPath() {
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.CREATED",    "ShipmentLifecycle.State.PROCESSING", "advance");
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.PROCESSING", "ShipmentLifecycle.State.IN_TRANSIT", "advance");
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.IN_TRANSIT", "ShipmentLifecycle.State.DELIVERED",  "advance");
    }

    @Test
    void shipmentFailTransitions() {
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.PROCESSING", "ShipmentLifecycle.State.FAILED", "fail");
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.IN_TRANSIT", "ShipmentLifecycle.State.FAILED", "fail");
    }

    @Test
    void shipmentCancelTransitions() {
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.CREATED",    "ShipmentLifecycle.State.CANCELLED", "cancel");
        assertTransition(shipmentGraph, "ShipmentLifecycle.State.PROCESSING", "ShipmentLifecycle.State.CANCELLED", "cancel");
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
        for (String s : new String[]{"PaymentWorkflow.State.PENDING", "PaymentWorkflow.State.AUTHORIZING", "PaymentWorkflow.State.AUTHORIZED", "PaymentWorkflow.State.CAPTURING", "PaymentWorkflow.State.CAPTURED", "PaymentWorkflow.State.REFUNDED", "PaymentWorkflow.State.DECLINED"}) {
            Node n = paymentGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void paymentDslTransitionsWithEventLabels() {
        assertTransition(paymentGraph, "PaymentWorkflow.State.PENDING",     "PaymentWorkflow.State.AUTHORIZING", "initiate");
        assertTransition(paymentGraph, "PaymentWorkflow.State.AUTHORIZING", "PaymentWorkflow.State.AUTHORIZED",  "authOk");
        assertTransition(paymentGraph, "PaymentWorkflow.State.AUTHORIZING", "PaymentWorkflow.State.DECLINED",    "authDenied");
        assertTransition(paymentGraph, "PaymentWorkflow.State.AUTHORIZED",  "PaymentWorkflow.State.CAPTURING",   "capture");
        assertTransition(paymentGraph, "PaymentWorkflow.State.CAPTURING",   "PaymentWorkflow.State.CAPTURED",    "captureOk");
        assertTransition(paymentGraph, "PaymentWorkflow.State.CAPTURING",   "PaymentWorkflow.State.DECLINED",    "captureFailed");
        assertTransition(paymentGraph, "PaymentWorkflow.State.CAPTURED",    "PaymentWorkflow.State.REFUNDED",    "refund");
    }

    @Test
    void paymentTransitionCount() {
        assertEquals(7, transitionCount(paymentGraph),
                "configure() registers exactly 7 transitions");
    }

    // ── InventoryReservation: linear assignment pattern ──────────────────────

    @Test
    void inventoryEnumConstantsAreStateNodes() {
        for (String s : new String[]{"InventoryReservation.ReservationState.IDLE", "InventoryReservation.ReservationState.CHECKING", "InventoryReservation.ReservationState.RESERVED", "InventoryReservation.ReservationState.ALLOCATED", "InventoryReservation.ReservationState.COMMITTED", "InventoryReservation.ReservationState.RELEASED"}) {
            Node n = inventoryGraph.findNode(s);
            assertNotNull(n, "Expected STATE node: " + s);
            assertEquals("STATE", n.getType());
        }
    }

    @Test
    void inventoryReservationChain() {
        // Field initialiser IDLE drives the first link; each subsequent assignment extends the chain
        assertTransition(inventoryGraph, "InventoryReservation.ReservationState.IDLE",      "InventoryReservation.ReservationState.CHECKING",  "processReservation");
        assertTransition(inventoryGraph, "InventoryReservation.ReservationState.CHECKING",  "InventoryReservation.ReservationState.RESERVED",  "processReservation");
        assertTransition(inventoryGraph, "InventoryReservation.ReservationState.RESERVED",  "InventoryReservation.ReservationState.ALLOCATED", "processReservation");
        assertTransition(inventoryGraph, "InventoryReservation.ReservationState.ALLOCATED", "InventoryReservation.ReservationState.COMMITTED", "processReservation");
    }

    @Test
    void inventoryReleaseTransition() {
        // Field initialiser IDLE is the implicit prev for single-assignment methods
        assertTransition(inventoryGraph, "InventoryReservation.ReservationState.IDLE", "InventoryReservation.ReservationState.RELEASED", "release");
    }

    // ── Directory parse combines all three files ─────────────────────────────

    @Test
    void directoryParseContainsAllThreeMachines() {
        StateMachineParser parser = new StateMachineParser();
        Graph combined = parser.parse(STATE_DIR);

        // spot-check one node from each machine; ids carry the enum, so ShipmentLifecycle.State
        // and PaymentWorkflow.State stay apart even though both enums are called State
        assertNotNull(combined.findNode("ShipmentLifecycle.State.CREATED"),    "shipment state missing from combined graph");
        assertNotNull(combined.findNode("PaymentWorkflow.State.PENDING"),    "payment state missing from combined graph");
        assertNotNull(combined.findNode("InventoryReservation.ReservationState.CHECKING"),   "inventory state missing from combined graph");

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
