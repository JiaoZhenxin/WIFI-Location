package com.nio.wifilocation.model.entity;

import java.util.ArrayList;
import java.util.List;

public class FingerprintDatabase {
    public double siteWidthMeters = 30.0;
    public double siteHeightMeters = 18.0;
    public List<FingerprintPoint> fingerprints = new ArrayList<>();
}
