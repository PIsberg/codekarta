package com.karta.shipping.state;

/**
 * Payment workflow state machine — demonstrates the <b>explicit {@code transition()} DSL</b>
 * pattern.
 *
 * <p>{@code StateMachineParser} scans for method calls named {@code transition} that accept
 * at least two arguments.  The first two arguments resolve to the source and target STATE
 * nodes; an optional third string argument becomes the edge label (event name).  No
 * switch statement is required.</p>
 *
 * <pre>
 *   PENDING ──initiate──▶ AUTHORIZING ──authOk──▶ AUTHORIZED ──capture──▶ CAPTURING
 *                     AUTHORIZING ──authDenied──▶ DECLINED
 *                                             CAPTURING ──captureOk──▶ CAPTURED
 *                                             CAPTURING ──captureFailed──▶ DECLINED
 *                                                          CAPTURED ──refund──▶ REFUNDED
 * </pre>
 *
 * <p>Run with: {@code karta --input PaymentWorkflow.java --state-machine}</p>
 */
public class PaymentWorkflow {

    public enum State { PENDING, AUTHORIZING, AUTHORIZED, CAPTURING, CAPTURED, REFUNDED, DECLINED }

    /**
     * Registers all allowed state transitions.
     *
     * <p>Each {@code transition(from, to, event)} call maps to one TRANSITION edge in the
     * generated diagram.  The event string becomes the edge label.</p>
     */
    void configure() {
        transition(State.PENDING,     State.AUTHORIZING, "initiate");
        transition(State.AUTHORIZING, State.AUTHORIZED,  "authOk");
        transition(State.AUTHORIZING, State.DECLINED,    "authDenied");
        transition(State.AUTHORIZED,  State.CAPTURING,   "capture");
        transition(State.CAPTURING,   State.CAPTURED,    "captureOk");
        transition(State.CAPTURING,   State.DECLINED,    "captureFailed");
        transition(State.CAPTURED,    State.REFUNDED,    "refund");
    }

    /**
     * DSL method consumed by {@code StateMachineParser}.
     * At runtime this would wire {@code from}→{@code to} into a transition table.
     */
    private void transition(State from, State to, String event) {
        // runtime wiring omitted for brevity
    }
}
