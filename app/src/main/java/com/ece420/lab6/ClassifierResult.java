package com.ece420.lab6;
import java.util.List;
public class ClassifierResult {
    private int index;
    private double distance;
    private List<ClassifierResult> topMatches;
    public ClassifierResult(int index, double distance) {
        this.index = index;
        this.distance = distance;
    }

    public ClassifierResult(int index, double distance, List<ClassifierResult> topMatches) {
        this.index = index;
        this.distance = distance;
        this.topMatches = topMatches;
    }

    public double getDistance() {
        return distance;
    }

    public int getIndex() {
        return index;
    }
    public List<ClassifierResult> getTopMatches() { return topMatches; }
}
