package com.nio.wifilocation.model.entity;

public class AccessPointStats {
    public double mean;
    public double stdDev;
    public int count;

    public AccessPointStats() {
    }

    public AccessPointStats(double mean, double stdDev, int count) {
        this.mean = mean;
        this.stdDev = stdDev;
        this.count = count;
    }
}
