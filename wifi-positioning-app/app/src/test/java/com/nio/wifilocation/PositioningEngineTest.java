package com.nio.wifilocation;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PositioningEngineTest {
    @Test
    public void stableObservationShouldSuppressOutlier() {
        PositioningEngine engine = new PositioningEngine();
        List<Map<String, Integer>> window = new ArrayList<>();
        window.add(single("ap1", -50));
        window.add(single("ap1", -51));
        window.add(single("ap1", -50));
        window.add(single("ap1", -82));
        window.add(single("ap1", -49));

        Map<String, Double> observation = engine.buildStableObservation(window);
        Assert.assertTrue(observation.containsKey("ap1"));
        Assert.assertTrue(observation.get("ap1") > -55.0);
    }

    private Map<String, Integer> single(String key, int value) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
