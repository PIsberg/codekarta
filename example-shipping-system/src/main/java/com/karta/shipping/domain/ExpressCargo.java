package com.karta.shipping.domain;

public class ExpressCargo extends Cargo {

    private int priorityLevel;

    public ExpressCargo(String trackingId, double weight, String destination, int priorityLevel) {
        super(trackingId, weight, destination);
        this.priorityLevel = priorityLevel;
    }

    public int getPriorityLevel() { return priorityLevel; }
}
