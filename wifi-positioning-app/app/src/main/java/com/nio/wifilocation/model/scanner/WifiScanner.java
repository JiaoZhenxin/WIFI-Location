package com.nio.wifilocation.model.scanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WifiScanner {
    public interface Listener {
        void onStatus(String status);
        void onScan(Map<String, Integer> levels, String scanSummary);
    }

    private final Context context;
    private final WifiManager wifiManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final long scanIntervalMs = 2200L;
    private boolean running;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> results = new ArrayList<>(wifiManager.getScanResults());
            Map<String, Integer> levels = new LinkedHashMap<>();
            StringBuilder summary = new StringBuilder();
            results.sort((left, right) -> Integer.compare(right.level, left.level));
            for (int i = 0; i < Math.min(12, results.size()); i++) {
                ScanResult result = results.get(i);
                levels.put(result.BSSID, result.level);
                summary.append(result.BSSID)
                        .append("  ")
                        .append(result.level)
                        .append(" dBm  ")
                        .append(result.SSID == null ? "" : result.SSID)
                        .append('\n');
            }
            listener.onScan(levels, summary.toString());
            if (running) {
                scheduleNext();
            }
        }
    };

    public WifiScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public boolean hasRequiredPermissions() {
        boolean locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearbyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                    == PackageManager.PERMISSION_GRANTED;
            return locationGranted && nearbyGranted;
        }
        return locationGranted;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        listener.onStatus("状态: 扫描循环启动");
        triggerScan();
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        handler.removeCallbacksAndMessages(null);
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        listener.onStatus("状态: 扫描已停止");
    }

    private void scheduleNext() {
        handler.postDelayed(this::triggerScan, scanIntervalMs);
    }

    private void triggerScan() {
        if (!wifiManager.isWifiEnabled()) {
            listener.onStatus("状态: WiFi 未开启");
            scheduleNext();
            return;
        }
        boolean started = wifiManager.startScan();
        listener.onStatus(started ? "状态: 正在采集 WiFi 指纹" : "状态: startScan 被系统限流，等待重试");
        if (!started) {
            scheduleNext();
        }
    }
}
