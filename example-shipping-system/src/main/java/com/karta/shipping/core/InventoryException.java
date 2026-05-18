package com.karta.shipping.core;

/** Unchecked exception thrown when inventory stock cannot be confirmed. */
public class InventoryException extends RuntimeException {

    public InventoryException(String message) {
        super(message);
    }

    public InventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
