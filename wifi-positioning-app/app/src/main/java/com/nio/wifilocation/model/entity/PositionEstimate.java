package com.nio.wifilocation.model.entity;

import java.util.ArrayList;
import java.util.List;

public class PositionEstimate {
    public final double rawX;
    public final double rawY;
    public final double filteredX;
    public final double filteredY;
    public final double confidenceMeters;
    public final List<CandidateScore> candidates;

    public PositionEstimate(double rawX, double rawY, double filteredX, double filteredY,
                            double confidenceMeters, List<CandidateScore> candidates) {
        this.rawX = rawX;
        this.rawY = rawY;
        this.filteredX = filteredX;
        this.filteredY = filteredY;
        this.confidenceMeters = confidenceMeters;
        this.candidates = new ArrayList<>(candidates);
    }

    public static class CandidateScore {
        public final String pointId;
        public final double x;
        public final double y;
        public final double score;
        public final double overlapRatio;

        public CandidateScore(String pointId, double x, double y, double score, double overlapRatio) {
            this.pointId = pointId;
            this.x = x;
            this.y = y;
            this.score = score;
            this.overlapRatio = overlapRatio;
        }
    }
}
