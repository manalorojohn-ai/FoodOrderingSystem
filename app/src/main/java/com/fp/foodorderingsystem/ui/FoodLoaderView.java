package com.fp.foodorderingsystem.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;

/**
 * Custom loader inspired by the provided CSS animation.
 * Draws a bowl-like circle with decorative dots and a sweeping mask effect.
 */
public class FoodLoaderView extends View {
    private static final int BORDER_COLOR = Color.parseColor("#d1914b");
    private static final int BOWL_COLOR = Color.parseColor("#f6d353");
    private static final int DOT_PRIMARY_COLOR = Color.parseColor("#d64123");
    private static final int DOT_SECONDARY_COLOR = Color.parseColor("#000000");
    private static final int MASK_COLOR = Color.parseColor("#fbe78a");

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bowlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bowlRect = new RectF();
    private ValueAnimator sweepAnimator;
    private float sweepAngle = 0f;

    // Dot definitions: {xPercent, yPercent, sizePercent, isPrimary(1/0)}
    private static final float[][] DOTS = new float[][]{
        {11f, 15f, 15f, 1f},
        {6f, 15f, 6f, 0f},
        {35f, 23f, 15f, 1f},
        {29f, 15f, 6f, 0f},
        {11f, 46f, 15f, 1f},
        {11f, 34f, 6f, 0f},
        {36f, 0f, 15f, 1f},
        {50f, 31f, 6f, 0f},
        {47f, 43f, 15f, 1f},
        {31f, 48f, 6f, 0f}
    };
    private static final float BASE_SIZE = 80f; // reference from CSS

    public FoodLoaderView(Context context) {
        super(context);
        init();
    }

    public FoodLoaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FoodLoaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(BORDER_COLOR);
        borderPaint.setStrokeWidth(dpToPx(4));

        bowlPaint.setStyle(Paint.Style.FILL);
        bowlPaint.setColor(BOWL_COLOR);

        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setColor(MASK_COLOR);

        primaryDotPaint.setStyle(Paint.Style.FILL);
        primaryDotPaint.setColor(DOT_PRIMARY_COLOR);

        secondaryDotPaint.setStyle(Paint.Style.FILL);
        secondaryDotPaint.setColor(DOT_SECONDARY_COLOR);

        setupAnimator();
    }

    private void setupAnimator() {
        sweepAnimator = ValueAnimator.ofFloat(0f, 360f);
        sweepAnimator.setDuration(3000);
        sweepAnimator.setInterpolator(new LinearInterpolator());
        sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnimator.addUpdateListener(animation -> {
            sweepAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = size / 2f - borderPaint.getStrokeWidth();

        bowlRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawCircle(cx, cy, radius, borderPaint);
        canvas.drawCircle(cx, cy, radius - borderPaint.getStrokeWidth() / 2f, bowlPaint);

        // Draw decorative dots
        for (float[] dot : DOTS) {
            float xPercent = dot[0] / BASE_SIZE;
            float yPercent = dot[1] / BASE_SIZE;
            float sizePercent = dot[2] / BASE_SIZE;
            boolean isPrimary = dot[3] == 1f;

            float dotRadius = (size * sizePercent) / 2f;
            float dx = xPercent * size - size / 2f;
            float dy = yPercent * size - size / 2f;

            Paint dotPaint = isPrimary ? primaryDotPaint : secondaryDotPaint;
            canvas.drawCircle(cx + dx, cy + dy, dotRadius, dotPaint);
        }

        // Draw sweeping mask
        canvas.drawArc(bowlRect, sweepAngle, 60f, true, maskPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    public void showLoader() {
        setVisibility(VISIBLE);
        startAnimation();
    }

    public void hideLoader() {
        stopAnimation();
        setVisibility(GONE);
    }

    private void startAnimation() {
        if (sweepAnimator != null && !sweepAnimator.isStarted()) {
            sweepAnimator.start();
        }
    }

    private void stopAnimation() {
        if (sweepAnimator != null && sweepAnimator.isRunning()) {
            sweepAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }
}

