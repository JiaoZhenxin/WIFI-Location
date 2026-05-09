package com.nio.wifilocation.controller;

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

import com.nio.wifilocation.R;
import com.nio.wifilocation.model.entity.FingerprintDatabase;
import com.nio.wifilocation.model.entity.PositionEstimate;
import com.nio.wifilocation.model.scanner.WifiScanner;
import com.nio.wifilocation.model.service.PositioningSessionManager;
import com.nio.wifilocation.view.FloorMapView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements WifiScanner.Listener {
    private FloorMapView floorMapView;
    private TextView statusText;
    private TextView positionText;
    private TextView candidateText;
    private TextView scanText;
    private EditText pointNameInput;
    private EditText pointXInput;
    private EditText pointYInput;
    private Button startButton;
    private Button stopButton;
    private Button saveFingerprintButton;
    private Button resetDatabaseButton;

    private final PositioningSessionManager sessionManager = new PositioningSessionManager();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger scanGeneration = new AtomicInteger();
    private WifiScanner wifiScanner;

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
        bindViews();

        wifiScanner = new WifiScanner(this, this);
        setControlsEnabled(false);
        bindActions();
        loadDatabase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiScanner.stop();
        backgroundExecutor.shutdownNow();
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> statusText.setText(status));
    }

    @Override
    public void onScan(Map<String, Integer> levels, String scanSummary) {
        if (!sessionManager.hasDatabase()) {
            return;
        }
        final int generation = scanGeneration.incrementAndGet();
        backgroundExecutor.execute(() -> {
            PositionEstimate estimate = sessionManager.processScan(levels, System.currentTimeMillis());
            if (estimate == null) {
                return;
            }

            String positionMessage = buildPositionMessage(estimate);
            String candidateMessage = buildCandidateText(estimate.candidates);
            runOnUiThread(() -> {
                if (generation != scanGeneration.get()) {
                    return;
                }
                floorMapView.setEstimate(estimate);
                scanText.setText(scanSummary);
                positionText.setText(positionMessage);
                candidateText.setText(candidateMessage);
            });
        });
    }

    private void bindViews() {
        floorMapView = findViewById(R.id.floorMapView);
        statusText = findViewById(R.id.statusText);
        positionText = findViewById(R.id.positionText);
        candidateText = findViewById(R.id.candidateText);
        scanText = findViewById(R.id.scanText);
        pointNameInput = findViewById(R.id.pointNameInput);
        pointXInput = findViewById(R.id.pointXInput);
        pointYInput = findViewById(R.id.pointYInput);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        saveFingerprintButton = findViewById(R.id.saveFingerprintButton);
        resetDatabaseButton = findViewById(R.id.resetDatabaseButton);
    }

    private void bindActions() {
        startButton.setOnClickListener(v -> ensurePermissionsAndStart());
        stopButton.setOnClickListener(v -> wifiScanner.stop());
        saveFingerprintButton.setOnClickListener(v -> saveCurrentFingerprint());
        resetDatabaseButton.setOnClickListener(v -> resetDatabase());
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
        statusText.setText("状态: 正在加载指纹库");
        backgroundExecutor.execute(() -> {
            try {
                FingerprintDatabase database = sessionManager.loadDatabase(this);
                runOnUiThread(() -> renderDatabaseLoaded(database));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("状态: 指纹库加载失败");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void resetDatabase() {
        setControlsEnabled(false);
        statusText.setText("状态: 正在恢复示例指纹库");
        backgroundExecutor.execute(() -> {
            try {
                FingerprintDatabase database = sessionManager.resetDatabase(this);
                runOnUiThread(() -> {
                    floorMapView.setDatabase(database);
                    statusText.setText("状态: 已恢复示例指纹库");
                    setControlsEnabled(true);
                    Toast.makeText(this, "已恢复示例指纹库", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("状态: 恢复失败");
                    setControlsEnabled(true);
                    Toast.makeText(this, "恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveCurrentFingerprint() {
        if (!sessionManager.hasDatabase()) {
            Toast.makeText(this, "指纹库尚未加载完成", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!sessionManager.hasCollectedScans()) {
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

        setControlsEnabled(false);
        statusText.setText("状态: 正在保存指纹点");
        backgroundExecutor.execute(() -> {
            try {
                FingerprintDatabase database = sessionManager.saveFingerprint(this, pointName, x, y);
                runOnUiThread(() -> {
                    floorMapView.setDatabase(database);
                    statusText.setText("状态: 指纹点已保存");
                    setControlsEnabled(true);
                    Toast.makeText(this, "指纹点已保存: " + pointName, Toast.LENGTH_SHORT).show();
                });
            } catch (IllegalStateException e) {
                runOnUiThread(() -> {
                    statusText.setText("状态: 指纹库不可用");
                    setControlsEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusText.setText("状态: 保存失败");
                    setControlsEnabled(true);
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderDatabaseLoaded(FingerprintDatabase database) {
        floorMapView.setDatabase(database);
        statusText.setText("状态: 指纹库已加载，共 " + database.fingerprints.size() + " 个参考点");
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        startButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        saveFingerprintButton.setEnabled(enabled);
        resetDatabaseButton.setEnabled(enabled);
    }

    private String buildPositionMessage(PositionEstimate estimate) {
        return String.format(
                "位置: 原始(%.2f, %.2f)m  稳定(%.2f, %.2f)m  半径≈%.2fm",
                estimate.rawX, estimate.rawY, estimate.filteredX, estimate.filteredY, estimate.confidenceMeters);
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
