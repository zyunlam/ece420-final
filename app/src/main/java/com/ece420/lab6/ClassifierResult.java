package com.ece420.lab6;

public class ClassifierResult {
    private int index;
    private double distance;

    public ClassifierResult(int index, double distance) {
        this.index = index;
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }

    public int getIndex() {
        return index;
    }
}
