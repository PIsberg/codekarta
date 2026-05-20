package com.karta.shipping.state;

/**
 * Inventory reservation state machine — demonstrates the <b>linear state assignment</b>
 * pattern.
 *
 * <p>{@code StateMachineParser} uses the field initialiser of a variable whose name
 * contains {@code "state"} as the implicit starting point ({@code IDLE} here), then
 * walks each method's assignment statements in order.  Every consecutive pair of
 * recognised state values is emitted as a TRANSITION edge labelled with the method
 * name.</p>
 *
 * <pre>
 *   IDLE ──processReservation──▶ CHECKING ──processReservation──▶ RESERVED
 *        ──processReservation──▶ ALLOCATED ──processReservation──▶ COMMITTED
 *   IDLE ──release──▶ RELEASED
 * </pre>
 *
 * <p>Run with: {@code karta --input InventoryReservation.java --state-machine}</p>
 */
public class InventoryReservation {

    public enum ReservationState { IDLE, CHECKING, RESERVED, ALLOCATED, COMMITTED, RELEASED }

    /**
     * Field initialiser is read by the parser as the implicit "previous state" when
     * a method has no prior assignment to fall back on.  Change this to alter the
     * starting node of every method's transition chain.
     */
    private ReservationState reservationState = ReservationState.IDLE;

    /**
     * Reserve stock for a confirmed order.
     *
     * <p>Sequential assignments drive the parser:
     * IDLE → CHECKING → RESERVED → ALLOCATED → COMMITTED</p>
     */
    public void processReservation(String itemId, int quantity) {
        reservationState = ReservationState.CHECKING;
        validateStock(itemId, quantity);

        reservationState = ReservationState.RESERVED;
        holdItems(itemId, quantity);

        reservationState = ReservationState.ALLOCATED;
        assignWarehouseLocation(itemId);

        reservationState = ReservationState.COMMITTED;
        confirmReservation(itemId, quantity);
    }

    /**
     * Release held items back into available stock.
     *
     * <p>Single assignment: IDLE → RELEASED
     * (parser uses field initialiser IDLE as the previous state)</p>
     */
    public void release(String reservationId) {
        reservationState = ReservationState.RELEASED;
        notifyWarehouse(reservationId);
    }

    // ── private helpers (not parsed for state transitions) ──────────────────

    private void validateStock(String itemId, int quantity) {}
    private void holdItems(String itemId, int quantity) {}
    private void assignWarehouseLocation(String itemId) {}
    private void confirmReservation(String itemId, int quantity) {}
    private void notifyWarehouse(String reservationId) {}

    public ReservationState getReservationState() { return reservationState; }
}
