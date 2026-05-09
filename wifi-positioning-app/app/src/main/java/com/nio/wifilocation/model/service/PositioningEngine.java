package com.nio.wifilocation.model.service;

import com.nio.wifilocation.model.entity.AccessPointStats;
import com.nio.wifilocation.model.entity.FingerprintDatabase;
import com.nio.wifilocation.model.entity.FingerprintPoint;
import com.nio.wifilocation.model.entity.PositionEstimate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PositioningEngine {
    private static final int TOP_K = 4;
    private static final double MIN_STD = 2.0;
    private static final double MISSING_PENALTY = 3.2;
    private static final double TAU = 1.6;
    private final TemporalPositionFilter filter = new TemporalPositionFilter();

    public PositionEstimate estimate(FingerprintDatabase database,
                                     Map<String, Double> observation,
                                     long timestampMs) {
        List<PositionEstimate.CandidateScore> scores = new ArrayList<>();
        for (FingerprintPoint point : database.fingerprints) {
            double overlapCount = 0.0;
            double availableCount = Math.max(1, point.samples.size());
            double distance = 0.0;

            for (Map.Entry<String, AccessPointStats> entry : point.samples.entrySet()) {
                Double measured = observation.get(entry.getKey());
                AccessPointStats stats = entry.getValue();
                if (measured != null) {
                    overlapCount += 1.0;
                    double sigma = Math.max(MIN_STD, stats.stdDev);
                    double normalized = (measured - stats.mean) / sigma;
                    distance += normalized * normalized;
                } else {
                    distance += MISSING_PENALTY;
                }
            }

            for (String bssid : observation.keySet()) {
                if (!point.samples.containsKey(bssid)) {
                    distance += 0.8;
                }
            }

            double overlapRatio = overlapCount / availableCount;
            if (overlapRatio < 0.25) {
                continue;
            }

            double score = Math.exp(-Math.sqrt(distance) / TAU) * Math.pow(overlapRatio, 1.5);
            scores.add(new PositionEstimate.CandidateScore(point.id, point.x, point.y, score, overlapRatio));
        }

        if (scores.isEmpty()) {
            return new PositionEstimate(0.0, 0.0, 0.0, 0.0, 99.0, Collections.emptyList());
        }

        scores.sort(Comparator.comparingDouble((PositionEstimate.CandidateScore score) -> score.score).reversed());
        List<PositionEstimate.CandidateScore> top = scores.subList(0, Math.min(TOP_K, scores.size()));

        double weightSum = 0.0;
        double rawX = 0.0;
        double rawY = 0.0;
        for (PositionEstimate.CandidateScore candidate : top) {
            weightSum += candidate.score;
            rawX += candidate.x * candidate.score;
            rawY += candidate.y * candidate.score;
        }
        rawX /= Math.max(weightSum, 1e-6);
        rawY /= Math.max(weightSum, 1e-6);

        double dispersion = 0.0;
        for (PositionEstimate.CandidateScore candidate : top) {
            double dx = candidate.x - rawX;
            double dy = candidate.y - rawY;
            dispersion += candidate.score * Math.sqrt(dx * dx + dy * dy);
        }
        dispersion /= Math.max(weightSum, 1e-6);

        double[] filtered = filter.update(rawX, rawY, Math.max(0.8, dispersion), timestampMs,
                database.siteWidthMeters, database.siteHeightMeters);
        return new PositionEstimate(rawX, rawY, filtered[0], filtered[1], Math.max(0.8, dispersion),
                new ArrayList<>(top));
    }

    public Map<String, Double> buildStableObservation(List<Map<String, Integer>> scanWindow) {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (Map<String, Integer> scan : scanWindow) {
            for (Map.Entry<String, Integer> entry : scan.entrySet()) {
                grouped.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue());
            }
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            List<Integer> values = entry.getValue();
            if (values.size() < 2) {
                continue;
            }
            Collections.sort(values);
            double median = median(values);
            double mad = medianAbsoluteDeviation(values, median);
            double sum = 0.0;
            int count = 0;
            double threshold = Math.max(2.5, 2.5 * mad);
            for (int value : values) {
                if (Math.abs(value - median) <= threshold) {
                    sum += value;
                    count++;
                }
            }
            if (count > 0) {
                result.put(entry.getKey(), sum / count);
            }
        }
        return result;
    }

    public AccessPointStats summarizeSamples(List<Integer> rssiValues) {
        List<Integer> copy = new ArrayList<>(rssiValues);
        Collections.sort(copy);
        double mean = 0.0;
        for (int value : copy) {
            mean += value;
        }
        mean /= Math.max(1, copy.size());

        double variance = 0.0;
        for (int value : copy) {
            double diff = value - mean;
            variance += diff * diff;
        }
        variance /= Math.max(1, copy.size());
        return new AccessPointStats(mean, Math.max(MIN_STD, Math.sqrt(variance)), copy.size());
    }

    private double median(List<Integer> values) {
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    private double medianAbsoluteDeviation(List<Integer> values, double median) {
        List<Integer> deltas = new ArrayList<>();
        for (int value : values) {
            deltas.add((int) Math.round(Math.abs(value - median)));
        }
        Collections.sort(deltas);
        return Math.max(1.0, median(deltas));
    }
}
