package com.fp.foodorderingsystem.utils;

import android.graphics.Paint;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

/**
 * Utility class for modern chart styling and design
 * Provides reusable configurations for enhanced visual appearance
 */
public class ChartStyleUtils {

    // Modern Color Palette - Material Design 3 inspired
    public static class Colors {
        // Primary colors
        public static final int PRIMARY_GREEN = 0xFF198754;
        public static final int SECONDARY_BLUE = 0xFF0D6EFD;
        public static final int ACCENT_ORANGE = 0xFFFF9800;
        public static final int WARNING_AMBER = 0xFFFFC107;
        
        // Chart specific
        public static final int CHART_GRID = 0xFFE9ECEF;
        public static final int TEXT_DARK = 0xFF1B1B1B;
        public static final int TEXT_LIGHT = 0xFF6C757D;
        public static final int TEXT_MUTED = 0xFFADB5BD;
        public static final int BACKGROUND_LIGHT = 0xFFFAFAFA;
        
        // Status colors
        public static final int PENDING = 0xFFFFC107;
        public static final int CONFIRMED = 0xFF0D6EFD;
        public static final int PREPARING = 0xFFFF9800;
        public static final int DELIVERING = 0xFF17A2B8;
        public static final int COMPLETED = 0xFF198754;
        public static final int CANCELLED = 0xFFDC3545;
        
        // Gradient colors
        public static final int GRADIENT_GREEN_LIGHT = 0x4D198754;
        public static final int GRADIENT_GREEN_DARK = 0xFF0F5C2F;
        public static final int GRADIENT_BLUE_LIGHT = 0x330D6EFD;
        public static final int GRADIENT_BLUE_DARK = 0xFF004085;
    }

    /**
     * Apply modern styling to a LineChart
     */
    public static void styleLineChart(LineChart chart) {
        if (chart == null) return;

        // Chart configuration
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(0xFFFFFFFF);
        chart.setDrawGridBackground(false);
        chart.setMaxVisibleValueCount(60);

        // Legend styling
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Colors.TEXT_DARK);
        legend.setTextSize(12f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setFormSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(8f);

        // X Axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Colors.TEXT_LIGHT);
        xAxis.setTextSize(11f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(Colors.CHART_GRID);
        xAxis.setAxisLineWidth(1f);
        xAxis.setGranularity(1f);

        // Left Y Axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Colors.TEXT_LIGHT);
        leftAxis.setTextSize(11f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Colors.CHART_GRID);
        leftAxis.setGridLineWidth(1f);
        leftAxis.setAxisLineColor(Colors.CHART_GRID);
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);

        // Right Y Axis (disabled)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // Animation
        chart.animateX(800);
        chart.animateY(800);
    }

    /**
     * Apply modern styling to a BarChart
     */
    public static void styleBarChart(BarChart chart) {
        if (chart == null) return;

        // Chart configuration
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(0xFFFFFFFF);
        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(true);
        chart.setMaxVisibleValueCount(60);

        // Legend styling
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Colors.TEXT_DARK);
        legend.setTextSize(12f);
        legend.setForm(Legend.LegendForm.SQUARE);
        legend.setFormSize(10f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(8f);

        // X Axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Colors.TEXT_LIGHT);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(Colors.CHART_GRID);
        xAxis.setAxisLineWidth(1f);
        xAxis.setGranularity(1f);

        // Left Y Axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Colors.TEXT_LIGHT);
        leftAxis.setTextSize(11f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Colors.CHART_GRID);
        leftAxis.setGridLineWidth(1f);
        leftAxis.setAxisLineColor(Colors.CHART_GRID);
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);

        // Right Y Axis (disabled)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // Animation
        chart.animateX(600);
        chart.animateY(600);
    }

    /**
     * Apply modern styling to a PieChart
     */
    public static void stylePieChart(PieChart chart) {
        if (chart == null) return;

        // Chart configuration
        chart.getDescription().setEnabled(false);
        chart.setUsePercentValues(true);
        chart.setDrawEntryLabels(false);
        chart.setRotationEnabled(true);
        chart.setRotationAngle(0);
        chart.setHighlightPerTapEnabled(true);
        chart.setBackgroundColor(0xFFFFFFFF);

        // Hole styling
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setTransparentCircleColor(Colors.CHART_GRID);

        // Legend styling
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Colors.TEXT_DARK);
        legend.setTextSize(11f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(8f);

        // Animation
        chart.animateY(800);
    }

    /**
     * Apply enhanced styling to a LineDataSet with gradient
     */
    public static void styleLineDataSet(LineDataSet dataSet, int color) {
        if (dataSet == null) return;

        // Line styling
        dataSet.setColor(color);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setDrawCircleHole(true);

        // Gradient fill
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(25);

        // Value styling
        dataSet.setValueTextColor(Colors.TEXT_DARK);
        dataSet.setValueTextSize(9f);
        dataSet.setHighLightColor(0x40000000);
        dataSet.setDrawHorizontalHighlightIndicator(true);
        dataSet.setDrawVerticalHighlightIndicator(true);
    }

    /**
     * Apply enhanced styling to a BarDataSet
     */
    public static void styleBarDataSet(BarDataSet dataSet, int color) {
        if (dataSet == null) return;

        // Bar styling
        dataSet.setColor(color);
        dataSet.setBarShadowColor(0xFFCCCCCC);
        dataSet.setHighLightColor(0x40000000);

        // Value styling
        dataSet.setValueTextColor(Colors.TEXT_DARK);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawValues(true);

        // Animation
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
    }

    /**
     * Apply enhanced styling to a PieDataSet
     */
    public static void stylePieDataSet(PieDataSet dataSet) {
        if (dataSet == null) return;

        // Pie styling
        dataSet.setValueTextColor(0xFFFFFFFF);
        dataSet.setValueTextSize(11f);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setValueLineColor(Colors.TEXT_LIGHT);
        dataSet.setUsingSliceColorAsValueLineColor(true);
    }

    /**
     * Get modern color array for status distribution
     */
    public static int[] getStatusColors() {
        return new int[]{
            Colors.PENDING,      // Pending
            Colors.CONFIRMED,    // Confirmed
            Colors.PREPARING,    // Preparing
            Colors.DELIVERING,   // Delivering
            Colors.COMPLETED,    // Completed
            Colors.CANCELLED     // Cancelled
        };
    }

    /**
     * Get gradient color array for gradient effect
     */
    public static int[] getGradientColors() {
        return new int[]{
            Colors.GRADIENT_GREEN_LIGHT,
            Colors.PRIMARY_GREEN
        };
    }

    /**
     * Get revenue chart colors
     */
    public static int[] getRevenueColors() {
        return new int[]{
            Colors.PRIMARY_GREEN,
            Colors.SECONDARY_BLUE,
            Colors.ACCENT_ORANGE
        };
    }

    /**
     * Create a formatted value for currency display
     */
    public static class CurrencyValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return "₱" + String.format("%.0f", value);
        }
    }

    /**
     * Create a formatted value for percentage display
     */
    public static class PercentageValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return String.format("%.1f%%", value);
        }
    }

    /**
     * Create a formatted value for integer display
     */
    public static class IntegerValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return String.valueOf((int) value);
        }
    }

    /**
     * Enable smooth animations with easing
     */
    public static void enableSmoothAnimations(LineChart chart) {
        if (chart != null) {
            chart.animateXY(1200, 1200);
        }
    }

    /**
     * Enable smooth animations with easing
     */
    public static void enableSmoothAnimations(BarChart chart) {
        if (chart != null) {
            chart.animateXY(1000, 1000);
        }
    }

    /**
     * Enable smooth animations with easing
     */
    public static void enableSmoothAnimations(PieChart chart) {
        if (chart != null) {
            chart.animateY(1200);
        }
    }

    /**
     * Create shadow effect paint for elevated appearance
     */
    public static Paint createShadowPaint() {
        Paint paint = new Paint();
        paint.setShadowLayer(10f, 0, 5f, 0x33000000);
        return paint;
    }

    /**
     * Get contrasting text color based on background
     */
    public static int getContrastingTextColor(int backgroundColor) {
        int red = (backgroundColor >> 16) & 0xFF;
        int green = (backgroundColor >> 8) & 0xFF;
        int blue = backgroundColor & 0xFF;
        
        // Calculate luminance
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
        
        // Return white for dark backgrounds, dark for light backgrounds
        return luminance > 0.5 ? Colors.TEXT_DARK : 0xFFFFFFFF;
    }
}
