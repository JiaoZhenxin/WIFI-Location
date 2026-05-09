package com.nio.wifilocation.model.service;

public class TemporalPositionFilter {
    private final double[][] p = new double[4][4];
    private final double[] x = new double[4];
    private boolean initialized;
    private long lastTimestampMs;

    public TemporalPositionFilter() {
        reset();
    }

    public void reset() {
        initialized = false;
        lastTimestampMs = 0L;
        for (int i = 0; i < 4; i++) {
            x[i] = 0.0;
            for (int j = 0; j < 4; j++) {
                p[i][j] = (i == j) ? 4.0 : 0.0;
            }
        }
    }

    public double[] update(double measuredX, double measuredY, double measurementStd, long timestampMs,
                           double maxWidth, double maxHeight) {
        if (!initialized) {
            initialized = true;
            x[0] = measuredX;
            x[1] = measuredY;
            x[2] = 0.0;
            x[3] = 0.0;
            lastTimestampMs = timestampMs;
            return new double[]{clamp(x[0], 0.0, maxWidth), clamp(x[1], 0.0, maxHeight)};
        }

        double dt = Math.max(0.5, (timestampMs - lastTimestampMs) / 1000.0);
        lastTimestampMs = timestampMs;

        predict(dt);

        double r = Math.max(0.8, measurementStd * measurementStd);
        double s00 = p[0][0] + r;
        double s01 = p[0][1];
        double s10 = p[1][0];
        double s11 = p[1][1] + r;
        double det = s00 * s11 - s01 * s10;
        if (Math.abs(det) < 1e-6) {
            det = 1e-6;
        }

        double inv00 = s11 / det;
        double inv01 = -s01 / det;
        double inv10 = -s10 / det;
        double inv11 = s00 / det;

        double innovationX = measuredX - x[0];
        double innovationY = measuredY - x[1];
        double mahalanobis = innovationX * (inv00 * innovationX + inv01 * innovationY)
                + innovationY * (inv10 * innovationX + inv11 * innovationY);

        if (mahalanobis > 9.21) {
            return new double[]{clamp(x[0], 0.0, maxWidth), clamp(x[1], 0.0, maxHeight)};
        }

        double[][] k = new double[4][2];
        for (int row = 0; row < 4; row++) {
            k[row][0] = p[row][0] * inv00 + p[row][1] * inv10;
            k[row][1] = p[row][0] * inv01 + p[row][1] * inv11;
        }

        for (int row = 0; row < 4; row++) {
            x[row] += k[row][0] * innovationX + k[row][1] * innovationY;
        }

        limitVelocity(1.2);

        double[][] newP = new double[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                newP[row][col] = p[row][col] - k[row][0] * p[0][col] - k[row][1] * p[1][col];
            }
        }

        for (int row = 0; row < 4; row++) {
            System.arraycopy(newP[row], 0, p[row], 0, 4);
        }

        x[0] = clamp(x[0], 0.0, maxWidth);
        x[1] = clamp(x[1], 0.0, maxHeight);
        return new double[]{x[0], x[1]};
    }

    private void predict(double dt) {
        x[0] += dt * x[2];
        x[1] += dt * x[3];

        double processPosition = 0.35;
        double processVelocity = 0.6;

        p[0][0] += dt * (p[2][0] + p[0][2]) + dt * dt * p[2][2] + processPosition;
        p[0][1] += dt * (p[2][1] + p[0][3]) + dt * dt * p[2][3];
        p[1][0] += dt * (p[3][0] + p[1][2]) + dt * dt * p[3][2];
        p[1][1] += dt * (p[3][1] + p[1][3]) + dt * dt * p[3][3] + processPosition;
        p[2][2] += processVelocity;
        p[3][3] += processVelocity;
    }

    private void limitVelocity(double maxSpeedMetersPerSecond) {
        double speed = Math.sqrt(x[2] * x[2] + x[3] * x[3]);
        if (speed <= maxSpeedMetersPerSecond || speed < 1e-6) {
            return;
        }
        double scale = maxSpeedMetersPerSecond / speed;
        x[2] *= scale;
        x[3] *= scale;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
