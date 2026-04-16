package com.nio.wifilocation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FloorMapView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint referencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint estimatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FingerprintDatabase database;
    private PositionEstimate estimate;

    public FloorMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(Color.parseColor("#DCE3EA"));
        gridPaint.setStrokeWidth(2f);
        referencePaint.setColor(Color.parseColor("#1F6FEB"));
        estimatePaint.setColor(Color.parseColor("#E5532D"));
        stablePaint.setColor(Color.parseColor("#2DA44E"));
        textPaint.setColor(Color.parseColor("#0F1720"));
        textPaint.setTextSize(28f);
    }

    public void setDatabase(FingerprintDatabase database) {
        this.database = database;
        invalidate();
    }

    public void setEstimate(PositionEstimate estimate) {
        this.estimate = estimate;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        for (int i = 1; i < 6; i++) {
            float x = width * i / 6f;
            float y = height * i / 6f;
            canvas.drawLine(x, 0, x, height, gridPaint);
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        if (database == null) {
            return;
        }

        for (FingerprintPoint point : database.fingerprints) {
            float px = toViewX(point.x);
            float py = toViewY(point.y);
            canvas.drawCircle(px, py, 12f, referencePaint);
            canvas.drawText(point.id, px + 12f, py - 12f, textPaint);
        }

        if (estimate != null) {
            canvas.drawCircle(toViewX(estimate.rawX), toViewY(estimate.rawY), 16f, estimatePaint);
            canvas.drawCircle(toViewX(estimate.filteredX), toViewY(estimate.filteredY), 12f, stablePaint);
        }
    }

    private float toViewX(double meters) {
        double width = database == null ? 30.0 : database.siteWidthMeters;
        return (float) (getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) * meters / width);
    }

    private float toViewY(double meters) {
        double height = database == null ? 18.0 : database.siteHeightMeters;
        return (float) (getHeight() - getPaddingBottom()
                - (getHeight() - getPaddingTop() - getPaddingBottom()) * meters / height);
    }
}
