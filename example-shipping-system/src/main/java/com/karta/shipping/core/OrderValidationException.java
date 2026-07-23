package se.deversity.codekarta.shipping.core;

/** Checked exception thrown when an order fails business validation. */
public class OrderValidationException extends Exception {

    public OrderValidationException(String message) {
        super(message);
    }

    public OrderValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
