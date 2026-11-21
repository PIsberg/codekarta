package com.example;

public class Order {
    private Customer customer;

    public void submit() {
        validate();
        customer.notifyPlaced();
    }

    private void validate() {
    }
}
