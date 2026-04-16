package com.nio.wifilocation;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FingerprintStore {
    private static final String FILE_NAME = "fingerprint_db.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FingerprintDatabase load(Context context) throws IOException {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            resetToSeed(context);
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return gson.fromJson(readAll(inputStream), FingerprintDatabase.class);
        }
    }

    public void save(Context context, FingerprintDatabase database) throws IOException {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(gson.toJson(database).getBytes(StandardCharsets.UTF_8));
        }
    }

    public void resetToSeed(Context context) throws IOException {
        try (InputStream inputStream = context.getAssets().open("fingerprint_seed.json")) {
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
                outputStream.write(readAll(inputStream).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
