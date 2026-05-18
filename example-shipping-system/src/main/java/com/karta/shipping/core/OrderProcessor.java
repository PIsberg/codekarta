package com.karta.shipping.core;

public class OrderProcessor {

    private final InventoryService inventoryService = new InventoryService();

    // ── Phase 2: Call sequence (preserved for backward-compat) ──────────────

    public void submit(String orderId, String itemId) {
        inventoryService.checkStock(itemId);
        inventoryService.reserveStock(itemId, 1);
    }

    public void cancel(String orderId, String itemId) {
        inventoryService.releaseStock(itemId, 1);
    }

    // ── Phase 3: Exception flow chain ───────────────────────────────────────

    /**
     * Catch boundary: checkStock may throw InventoryException (unchecked).
     * The catch block translates it into a checked OrderValidationException —
     * a classic exception chaining / wrapping pattern.
     *
     * In the IR graph the try block becomes a GraphGroup catch boundary
     * containing the checkStock node, and an EXCEPTION_PROPAGATION edge
     * exits this method toward its callers.
     */
    public void processOrder(String orderId) throws OrderValidationException {
        try {
            inventoryService.checkStock(orderId);
        } catch (InventoryException e) {
            throw new OrderValidationException("Order cannot be processed: " + orderId, e);
        }
    }

    /**
     * Uncaught propagation: calls processOrder which may throw
     * OrderValidationException.  The throws declaration causes an
     * EXCEPTION_PROPAGATION edge from this method to its callers in the IR.
     */
    public void submitOrder(String orderId) throws OrderValidationException {
        processOrder(orderId);
    }
}
