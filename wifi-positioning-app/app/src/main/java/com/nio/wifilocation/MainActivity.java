package com.nio.wifilocation;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements WifiScanner.Listener {
    private static final int WINDOW_SIZE = 6;

    private FloorMapView floorMapView;
    private TextView statusText;
    private TextView positionText;
    private TextView candidateText;
    private TextView scanText;
    private EditText pointNameInput;
    private EditText pointXInput;
    private EditText pointYInput;

    private final FingerprintStore fingerprintStore = new FingerprintStore();
    private final PositioningEngine positioningEngine = new PositioningEngine();
    private WifiScanner wifiScanner;
    private FingerprintDatabase database;
    private final Deque<Map<String, Integer>> scanWindow = new ArrayDeque<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (wifiScanner.hasRequiredPermissions()) {
                    wifiScanner.start();
                } else {
                    Toast.makeText(this, "需要 WiFi 与定位权限才能扫描", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        floorMapView = findViewById(R.id.floorMapView);
        statusText = findViewById(R.id.statusText);
        positionText = findViewById(R.id.positionText);
        candidateText = findViewById(R.id.candidateText);
        scanText = findViewById(R.id.scanText);
        pointNameInput = findViewById(R.id.pointNameInput);
        pointXInput = findViewById(R.id.pointXInput);
        pointYInput = findViewById(R.id.pointYInput);

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button saveFingerprintButton = findViewById(R.id.saveFingerprintButton);
        Button resetDatabaseButton = findViewById(R.id.resetDatabaseButton);

        wifiScanner = new WifiScanner(this, this);
        loadDatabase();

        startButton.setOnClickListener(v -> ensurePermissionsAndStart());
        stopButton.setOnClickListener(v -> wifiScanner.stop());
        saveFingerprintButton.setOnClickListener(v -> saveCurrentFingerprint());
        resetDatabaseButton.setOnClickListener(v -> resetDatabase());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiScanner.stop();
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> statusText.setText(status));
    }

    @Override
    public void onScan(Map<String, Integer> levels, String scanSummary) {
        if (database == null) {
            return;
        }
        scanWindow.addLast(levels);
        while (scanWindow.size() > WINDOW_SIZE) {
            scanWindow.removeFirst();
        }

        Map<String, Double> stableObservation = positioningEngine.buildStableObservation(new ArrayList<>(scanWindow));
        PositionEstimate estimate = positioningEngine.estimate(database, stableObservation, System.currentTimeMillis());

        runOnUiThread(() -> {
            floorMapView.setEstimate(estimate);
            scanText.setText(scanSummary);
            positionText.setText(String.format("位置: 原始(%.2f, %.2f)m  稳定(%.2f, %.2f)m  半径≈%.2fm",
                    estimate.rawX, estimate.rawY, estimate.filteredX, estimate.filteredY, estimate.confidenceMeters));
            candidateText.setText(buildCandidateText(estimate.candidates));
        });
    }

    private void ensurePermissionsAndStart() {
        if (wifiScanner.hasRequiredPermissions()) {
            wifiScanner.start();
            return;
        }

        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void loadDatabase() {
        try {
            database = fingerprintStore.load(this);
            floorMapView.setDatabase(database);
            statusText.setText("状态: 指纹库已加载，共 " + database.fingerprints.size() + " 个参考点");
        } catch (IOException e) {
            statusText.setText("状态: 指纹库加载失败");
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetDatabase() {
        try {
            fingerprintStore.resetToSeed(this);
            scanWindow.clear();
            loadDatabase();
            Toast.makeText(this, "已恢复示例指纹库", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveCurrentFingerprint() {
        if (scanWindow.isEmpty()) {
            Toast.makeText(this, "请先开始扫描并收集至少 2 帧数据", Toast.LENGTH_SHORT).show();
            return;
        }
        String pointName = pointNameInput.getText().toString().trim();
        String xText = pointXInput.getText().toString().trim();
        String yText = pointYInput.getText().toString().trim();
        if (TextUtils.isEmpty(pointName) || TextUtils.isEmpty(xText) || TextUtils.isEmpty(yText)) {
            Toast.makeText(this, "请填写点位名称与坐标", Toast.LENGTH_SHORT).show();
            return;
        }

        double x;
        double y;
        try {
            x = Double.parseDouble(xText);
            y = Double.parseDouble(yText);
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "坐标格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (Map<String, Integer> scan : scanWindow) {
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

        database.fingerprints.add(point);
        try {
            fingerprintStore.save(this, database);
            floorMapView.setDatabase(database);
            Toast.makeText(this, "指纹点已保存: " + pointName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private String buildCandidateText(List<PositionEstimate.CandidateScore> candidates) {
        if (candidates.isEmpty()) {
            return "候选点: 无";
        }
        StringBuilder builder = new StringBuilder("候选点: ");
        for (int i = 0; i < candidates.size(); i++) {
            PositionEstimate.CandidateScore candidate = candidates.get(i);
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(candidate.pointId)
                    .append(" score=")
                    .append(String.format("%.3f", candidate.score))
                    .append(" overlap=")
                    .append(String.format("%.2f", candidate.overlapRatio));
        }
        return builder.toString();
    }
}
