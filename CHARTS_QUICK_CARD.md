# Charts Quick Reference Card

## 🎨 Color Palette

```
PRIMARY_GREEN    → #198754    [████]  Main brand
SECONDARY_BLUE   → #0D6EFD    [████]  Data visualization
ACCENT_ORANGE    → #FF9800    [████]  Highlights

PENDING      → #FFC107    [████]  Amber/Yellow
CONFIRMED    → #0D6EFD    [████]  Blue
PREPARING    → #FF9800    [████]  Orange
DELIVERING   → #17A2B8    [████]  Cyan
COMPLETED    → #198754    [████]  Green
CANCELLED    → #DC3545    [████]  Red
```

## 📊 Chart Types

```
┌─────────────────────────────────────────────────┐
│ LINECHART         BARCHART         PIECHART    │
│ • Revenue         • Items          • Status    │
│ • Traffic         • Throughput     • Satisfact │
│ • Trends          • Comparison     • Split     │
└─────────────────────────────────────────────────┘
```

## 🚀 Setup (3 Lines)

```java
ChartStyleUtils.styleLineChart(chart);           // Style
chart.setData(new LineData(dataSet));            // Data
ChartStyleUtils.enableSmoothAnimations(chart);   // Animate
```

## 🎬 Animations

```
FAST       →  400ms   (< 10 data points)
NORMAL     →  800ms   (10-30 data points)
MEDIUM     → 1200ms   (30-60 data points)
SLOW       → 1500ms   (> 60 data points)
VERY_SLOW  → 2000ms   (Complex data)
```

## 💾 Code Snippets

### Apply Styling
```java
// Line charts
ChartStyleUtils.styleLineChart(chart);

// Bar charts
ChartStyleUtils.styleBarChart(chart);

// Pie charts
ChartStyleUtils.stylePieChart(chart);
```

### Format Values
```java
// Currency: ₱1,234
new ChartStyleUtils.CurrencyValueFormatter()

// Percentage: 45.5%
new ChartStyleUtils.PercentageValueFormatter()

// Integer: 42
new ChartStyleUtils.IntegerValueFormatter()
```

### Animate Charts
```java
// Smooth animation
ChartStyleUtils.enableSmoothAnimations(chart);

// Custom duration
ChartAnimationUtils.animateLineChartSmooth(chart,
    ChartAnimationUtils.AnimationDuration.SLOW);

// Stagger multiple
ChartAnimationUtils.staggerAnimateCharts(
    AnimationDuration.NORMAL, chart1, chart2, chart3);
```

### Style Data
```java
// Line data
ChartStyleUtils.styleLineDataSet(dataSet, 
    ChartStyleUtils.Colors.PRIMARY_GREEN);

// Bar data
ChartStyleUtils.styleBarDataSet(dataSet, 
    ChartStyleUtils.Colors.SECONDARY_BLUE);

// Pie data
ChartStyleUtils.stylePieDataSet(dataSet);
```

## ✅ Best Practices

```
DO ✅                          DON'T ❌
────────────────────────────────────────────
Check null              →  Forget null check
Use utilities           →  Hardcode colors
Format values           →  Raw numbers
Invalidate once         →  Loop invalidate
Limit visible points    →  Load all data
Background thread       →  Block main thread
Document code           →  No comments
```

## 🔧 Common Customizations

### Change Colors
```java
public static final int MY_COLOR = 0xFF6200EE;
dataSet.setColor(MY_COLOR);
```

### Faster Animations
```java
chart.animateXY(400, 400);  // 400ms
```

### Custom Formatter
```java
dataSet.setValueFormatter(new ValueFormatter() {
    @Override
    public String getFormattedValue(float value) {
        return "Custom: " + value;
    }
});
```

### Disable Touch
```java
chart.setTouchEnabled(false);
chart.setDragEnabled(false);
```

## 🐛 Troubleshooting

```
No Data Showing?
├─ Check: entries not empty
├─ Check: chart not null
├─ Check: called invalidate()
└─ Check: data properly set

Jerky Animation?
├─ Reduce duration
├─ Disable for large data
└─ Limit visible points

Memory Issues?
├─ setMaxVisibleValueCount(60)
├─ Disable unused features
└─ Load on background thread
```

## 📚 Documentation Files

```
For 5-minute read:     CHARTS_QUICK_REFERENCE.md
For complete guide:    CHARTS_DESIGN_GUIDE.md
For code examples:     ChartImplementationExamples.java
For quick lookup:      This file! 📄
```

## 🎯 Implementation Steps

```
Step 1: Import utilities
└─ import ChartStyleUtils;

Step 2: Style chart
└─ ChartStyleUtils.styleLineChart(chart);

Step 3: Prepare data
└─ List<Entry> entries = new ArrayList<>();

Step 4: Create dataset
└─ LineDataSet dataSet = new LineDataSet(...);

Step 5: Style dataset
└─ ChartStyleUtils.styleLineDataSet(dataSet, color);

Step 6: Set data
└─ chart.setData(new LineData(dataSet));

Step 7: Animate
└─ ChartStyleUtils.enableSmoothAnimations(chart);

Step 8: Refresh
└─ chart.invalidate();

Done! ✅
```

## 📊 Current Charts

```
Chart Name        Type    Color   Animation   Desc
─────────────────────────────────────────────────
Revenue           Line    Green   1200ms      7-day trend
Orders Status     Pie     Multi   800ms       Distribution
Popular Items     Bar     Blue    1000ms      Top 5 items
Throughput        Bar     Orange  1000ms      12-hour trend
Traffic           Line    Blue    1200ms      7-day count
Satisfaction      Pie     Multi   800ms       Completion
```

## 🔍 Method Reference

```
ChartStyleUtils Methods:
├─ styleLineChart()        →  Configure line chart
├─ styleBarChart()         →  Configure bar chart
├─ stylePieChart()         →  Configure pie chart
├─ styleLineDataSet()      →  Style line data
├─ styleBarDataSet()       →  Style bar data
├─ stylePieDataSet()       →  Style pie data
├─ enableSmoothAnimations() → Apply animations
├─ getStatusColors()       →  Get color array
├─ getRevenueColors()      →  Get gradient colors
└─ Colors.*                →  Color constants

ChartAnimationUtils Methods:
├─ animateLineChartSmooth()  → Line chart animation
├─ animateBarChartSmooth()   → Bar chart animation
├─ animatePieChartSmooth()   → Pie chart animation
├─ staggerAnimateCharts()    → Sequential animation
├─ cascadeAnimateCharts()    → Timed sequence
├─ disableAnimations()       → Remove animations
├─ bounceAnimateChart()      → Bounce effect
├─ fadeInChart()             → Fade effect
├─ slideUpChart()            → Slide effect
└─ rotateChart()             → Rotate effect
```

## ⏱️ Performance Tips

```
1. Limit visible: chart.setMaxVisibleValueCount(60)
2. Disable unused: chart.getAxisRight().setEnabled(false)
3. Use threading: executorService.execute(() -> {})
4. Invalidate once: Chart.invalidate() at the end
5. Preload data: Load before animation starts
6. Monitor memory: Use Android Profiler
7. Test on device: Don't rely on emulator
8. Batch updates: Update all data, then refresh
```

## 🎓 Learning Quick Path

```
5 min:    Read CHARTS_QUICK_REFERENCE.md
10 min:   Review Examples #1, #3
5 min:    Apply to your chart
2 min:    Test on device
────────────────────────
22 min:   Done! Have beautiful charts ✅
```

## 🌟 Key Takeaways

```
✓ Use ChartStyleUtils for styling
✓ Use ChartAnimationUtils for animations
✓ Format with value formatters
✓ Check for null always
✓ Call invalidate() once
✓ Test on real device
✓ Customize colors easily
✓ Reference documentation
```

## 📞 Quick Help

```
"How do I...?"

Apply modern styling?
→ ChartStyleUtils.styleLineChart(chart);

Format as currency?
→ new ChartStyleUtils.CurrencyValueFormatter();

Animate smoothly?
→ ChartStyleUtils.enableSmoothAnimations(chart);

Change colors?
→ ChartStyleUtils.Colors.PRIMARY_GREEN

Use status colors?
→ ChartStyleUtils.getStatusColors();

Stagger animations?
→ ChartAnimationUtils.staggerAnimateCharts();

Get more help?
→ CHARTS_DESIGN_GUIDE.md
```

---

## 🎉 You're All Set!

Print this card or bookmark it for quick reference while coding.

**Status**: ✅ Ready to Code  
**Time to Beautiful Charts**: ~22 minutes  
**Quality**: ⭐⭐⭐⭐⭐ Professional  

**Happy Charting!** 📊✨
