package com.karta.shipping.domain;

public class Cargo implements ShippingUnit {

    private String trackingId;
    private double weight;
    private String destination;

    public Cargo(String trackingId, double weight, String destination) {
        this.trackingId = trackingId;
        this.weight = weight;
        this.destination = destination;
    }

    @Override
    public String getTrackingId() { return trackingId; }

    @Override
    public double getWeight() { return weight; }

    public String getDestination() { return destination; }
}
