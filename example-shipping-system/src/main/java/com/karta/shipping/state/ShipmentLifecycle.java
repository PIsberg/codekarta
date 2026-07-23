package se.deversity.codekarta.shipping.state;

/**
 * Shipment lifecycle state machine — demonstrates the <b>switch-case transition</b> pattern.
 *
 * <p>{@code StateMachineParser} inspects each {@code switch} statement:
 * the case labels become source states and assignments to the {@code state} field
 * (or {@code return}/yield expressions) become target states.  Each (source, target,
 * method-name) triple is emitted as a {@code TRANSITION} edge.</p>
 *
 * <pre>
 *   CREATED ──advance──▶ PROCESSING ──advance──▶ IN_TRANSIT ──advance──▶ DELIVERED
 *             PROCESSING ──fail──▶ FAILED
 *             IN_TRANSIT ──fail──▶ FAILED
 *               CREATED ──cancel──▶ CANCELLED
 *            PROCESSING ──cancel──▶ CANCELLED
 * </pre>
 *
 * <p>Run with: {@code karta --input ShipmentLifecycle.java --state-machine}</p>
 */
public class ShipmentLifecycle {

    public enum State { CREATED, PROCESSING, IN_TRANSIT, DELIVERED, FAILED, CANCELLED }

    // No initialiser: parser starts with prev=null so linear-assignment fallback
    // won't create a spurious CREATED→X edge from the field itself.
    private State state;

    public ShipmentLifecycle() {
        this.state = State.CREATED;
    }

    /**
     * Happy-path advance — moves the shipment one step forward.
     * Produces: CREATED→PROCESSING, PROCESSING→IN_TRANSIT, IN_TRANSIT→DELIVERED
     */
    public void advance() {
        switch (state) {
            case CREATED    -> state = State.PROCESSING;
            case PROCESSING -> state = State.IN_TRANSIT;
            case IN_TRANSIT -> state = State.DELIVERED;
            default         -> {}
        }
    }

    /**
     * Mark as failed during active processing or transit.
     * Produces: PROCESSING→FAILED, IN_TRANSIT→FAILED
     */
    public void fail(String reason) {
        switch (state) {
            case PROCESSING, IN_TRANSIT -> state = State.FAILED;
            default -> {}
        }
    }

    /**
     * Cancel before the shipment leaves the warehouse.
     * Produces: CREATED→CANCELLED, PROCESSING→CANCELLED
     */
    public void cancel() {
        switch (state) {
            case CREATED, PROCESSING -> state = State.CANCELLED;
            default -> {}
        }
    }

    public State getState() { return state; }
}
