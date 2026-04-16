package com.nio.wifilocation;

import java.util.LinkedHashMap;
import java.util.Map;

public class FingerprintPoint {
    public String id;
    public double x;
    public double y;
    public Map<String, AccessPointStats> samples = new LinkedHashMap<>();
}
