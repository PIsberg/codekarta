package com.karta.shipping.core;

public class InventoryService {

    public boolean checkStock(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            throw new InventoryException("Item not found: " + itemId);
        }
        return true;
    }

    public void reserveStock(String itemId, int quantity) {
        // reserve stock for the given item
    }

    public void releaseStock(String itemId, int quantity) {
        // release previously reserved stock
    }
}
