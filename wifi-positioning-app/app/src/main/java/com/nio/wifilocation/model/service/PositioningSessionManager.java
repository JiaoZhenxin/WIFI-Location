package com.nio.wifilocation.model.service;

import android.content.Context;

import com.nio.wifilocation.model.entity.FingerprintDatabase;
import com.nio.wifilocation.model.entity.FingerprintPoint;
import com.nio.wifilocation.model.entity.PositionEstimate;
import com.nio.wifilocation.model.repository.FingerprintStore;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PositioningSessionManager {
    private static final int WINDOW_SIZE = 6;

    private final FingerprintStore fingerprintStore = new FingerprintStore();
    private final PositioningEngine positioningEngine = new PositioningEngine();
    private final Deque<Map<String, Integer>> scanWindow = new ArrayDeque<>();
    private final Object scanWindowLock = new Object();
    private volatile FingerprintDatabase database;

    public boolean hasDatabase() {
        return database != null;
    }

    public boolean hasCollectedScans() {
        synchronized (scanWindowLock) {
            return !scanWindow.isEmpty();
        }
    }

    public FingerprintDatabase loadDatabase(Context context) throws IOException {
        FingerprintDatabase loadedDatabase = fingerprintStore.load(context);
        database = loadedDatabase;
        return loadedDatabase;
    }

    public FingerprintDatabase resetDatabase(Context context) throws IOException {
        fingerprintStore.resetToSeed(context);
        clearScanWindow();
        FingerprintDatabase loadedDatabase = fingerprintStore.load(context);
        database = loadedDatabase;
        return loadedDatabase;
    }

    public PositionEstimate processScan(Map<String, Integer> levels, long timestampMs) {
        FingerprintDatabase currentDatabase = database;
        if (currentDatabase == null) {
            return null;
        }

        List<Map<String, Integer>> windowSnapshot;
        synchronized (scanWindowLock) {
            scanWindow.addLast(levels);
            while (scanWindow.size() > WINDOW_SIZE) {
                scanWindow.removeFirst();
            }
            windowSnapshot = new ArrayList<>(scanWindow);
        }

        Map<String, Double> stableObservation = positioningEngine.buildStableObservation(windowSnapshot);
        return positioningEngine.estimate(currentDatabase, stableObservation, timestampMs);
    }

    public FingerprintDatabase saveFingerprint(Context context, String pointName, double x, double y)
            throws IOException {
        FingerprintDatabase currentDatabase = database;
        if (currentDatabase == null) {
            throw new IllegalStateException("指纹库不可用");
        }

        List<Map<String, Integer>> windowSnapshot;
        synchronized (scanWindowLock) {
            windowSnapshot = new ArrayList<>(scanWindow);
        }

        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (Map<String, Integer> scan : windowSnapshot) {
            for (Map.Entry<String, Integer> entry : scan.entrySet()) {
                grouped.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue());
            }
        }

        FingerprintPoint point = new FingerprintPoint();
        point.id = pointName;
        point.x = x;
        point.y = y;
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= 2) {
                point.samples.put(entry.getKey(), positioningEngine.summarizeSamples(entry.getValue()));
            }
        }

        currentDatabase.fingerprints.add(point);
        fingerprintStore.save(context, currentDatabase);
        return currentDatabase;
    }

    private void clearScanWindow() {
        synchronized (scanWindowLock) {
            scanWindow.clear();
        }
    }
}
