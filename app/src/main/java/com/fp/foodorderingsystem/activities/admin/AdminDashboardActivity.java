package com.fp.foodorderingsystem.activities.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.navigation.NavigationView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.fp.foodorderingsystem.utils.ChartStyleUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.JsonObject;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminDashboardActivity extends AppCompatActivity {
    private TextView tvTotalOrders, tvTotalRevenue, tvPendingOrders, tvCompletedOrders;
    private TextView tvNavHeaderName, tvNavHeaderEmail, tvNavHeaderLevel;
    private TextView tvNotificationBadge;
    private ShapeableImageView imgSidebarAvatar;
    private OrderService orderService;
    private PreferenceUtil preferenceUtil;
    private AuthService authService;
    private com.fp.foodorderingsystem.services.NotificationService notificationService;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ExecutorService executorService;
    private Handler mainHandler;
    private View sidebarHeaderView;
    private SupabaseRealtimeClient ordersRealtimeClient;
    private SupabaseRealtimeClient menuRealtimeClient;
    private SupabaseRealtimeClient notificationRealtimeClient;
    
    // Chart views
    private LineChart chartRevenue;
    private PieChart chartOrders;
    private BarChart chartPopularItems;
    // New insight charts
    private BarChart chartThroughput;
    private LineChart chartTraffic;
    private PieChart chartSatisfaction;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        
        // Initialize services
        orderService = new OrderService(this);
        preferenceUtil = new PreferenceUtil(this);
        authService = new AuthService(this);
        notificationService = new com.fp.foodorderingsystem.services.NotificationService(this);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Check authentication
        try {
            if (!preferenceUtil.isLoggedIn() || !preferenceUtil.getUserType().equals("admin")) {
                finish();
                return;
            }
            
            initViews();
            setupDrawer();
            setupClickListeners();
            bindSidebarHeader();
            subscribeToRealtimeStreams();
            loadNotifications();
            
            // Delay data loading to ensure UI is fully initialized
            if (mainHandler != null) {
                mainHandler.postDelayed(() -> {
                    try {
                        loadDashboardData();
                    } catch (Exception e) {
                        android.util.Log.e("AdminDashboard", "Error loading dashboard data", e);
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    }
                }, 200);
            } else {
                loadDashboardData();
            }
        } catch (Exception e) {
            android.util.Log.e("AdminDashboard", "Error in onCreate", e);
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    ToastUtil.show(this, "Error initializing dashboard: " + e.getMessage(), Toast.LENGTH_LONG);
                    finish();
                });
            } else {
                finish();
            }
        }
    }
    
    private void initViews() {
        try {
            toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
            }
            
            tvTotalOrders = findViewById(R.id.tvTotalOrders);
            tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
            tvPendingOrders = findViewById(R.id.tvPendingOrders);
            tvCompletedOrders = findViewById(R.id.tvCompletedOrders);
            tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
            
            // Initialize charts
            chartRevenue = findViewById(R.id.chartRevenue);
            chartOrders = findViewById(R.id.chartOrders);
            chartPopularItems = findViewById(R.id.chartPopularItems);
            chartThroughput = findViewById(R.id.chartThroughput);
            chartTraffic = findViewById(R.id.chartTraffic);
            chartSatisfaction = findViewById(R.id.chartSatisfaction);
            
            // Setup charts
            setupRevenueChart();
            setupOrdersChart();
            setupPopularItemsChart();
            setupThroughputChart();
            setupTrafficChart();
            setupSatisfactionChart();
            
            drawerLayout = findViewById(R.id.drawerLayout);
            navigationView = findViewById(R.id.navView);
            swipeRefreshLayout = findViewById(R.id.swipeRefresh);
            
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setOnRefreshListener(() -> {
                    try {
                        loadDashboardData();
                    } catch (Exception e) {
                        android.util.Log.e("AdminDashboard", "Error in refresh listener", e);
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
                swipeRefreshLayout.setColorSchemeResources(
                    R.color.green_primary,
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light
                );
            }
            
            // Initialize nav header views with delay to avoid ANR
            bindSidebarHeader();
        } catch (Exception e) {
            android.util.Log.e("AdminDashboard", "Error initializing views", e);
        }
    }
    
    private void setupDrawer() {
        try {
            if (drawerLayout == null || toolbar == null) {
                return;
            }
            
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            );
            drawerLayout.addDrawerListener(toggle);
            
            // Use post to ensure toolbar is ready before syncing
            if (toolbar != null) {
                toolbar.post(() -> {
                    try {
                        toggle.syncState();
                    } catch (Exception e) {
                        android.util.Log.e("AdminDashboard", "Error syncing drawer toggle", e);
                    }
                });
            } else {
                toggle.syncState();
            }
            
            bindSidebarHeader();
        } catch (Exception e) {
            android.util.Log.e("AdminDashboard", "Error setting up drawer", e);
        }
    }
    
    private void updateNavHeader() {
        if (tvNavHeaderName == null || tvNavHeaderEmail == null) {
            return;
        }
        
        String userName = preferenceUtil.getUserName();
        String userEmail = preferenceUtil.getUserEmail();
        String userLevel = preferenceUtil.getUserType();
        
        if (userName != null && !userName.isEmpty()) {
            tvNavHeaderName.setText(userName);
        } else {
            tvNavHeaderName.setText("Admin");
        }
        
        if (userEmail != null && !userEmail.isEmpty()) {
            tvNavHeaderEmail.setText(userEmail);
        } else {
            tvNavHeaderEmail.setText("admin@foodorder.com");
        }
        
        if (tvNavHeaderLevel != null) {
            if (userLevel != null && !userLevel.isEmpty()) {
                tvNavHeaderLevel.setText(userLevel.toUpperCase(Locale.US));
            } else {
                tvNavHeaderLevel.setText("ADMINISTRATOR");
            }
        }
        
        if (imgSidebarAvatar != null) {
            imgSidebarAvatar.setImageResource(R.drawable.logo);
        }
    }
    
    private void setupClickListeners() {
        // Notifications button
        View btnNotifications = findViewById(R.id.btnNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> {
                Intent intent = new Intent(this, AdminNotificationsActivity.class);
                startActivity(intent);
            });
        }
        
        // Quick action cards
        View cardManageOrders = findViewById(R.id.cardManageOrders);
        if (cardManageOrders != null) {
            cardManageOrders.setOnClickListener(v -> {
                navigateToActivity(ManageOrdersActivity.class);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }
        
        View cardManageItems = findViewById(R.id.cardManageItems);
        if (cardManageItems != null) {
            cardManageItems.setOnClickListener(v -> {
                navigateToActivity(ManageItemsActivity.class);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }
        
        View cardManageCategories = findViewById(R.id.cardManageCategories);
        if (cardManageCategories != null) {
            cardManageCategories.setOnClickListener(v -> {
                navigateToActivity(ManageCategoriesActivity.class);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }
        
        View cardManageUsers = findViewById(R.id.cardManageUsers);
        if (cardManageUsers != null) {
            cardManageUsers.setOnClickListener(v -> {
                navigateToActivity(ManageUsersActivity.class);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }
        
        // cardFakeBookingTracker removed from layout - navigation moved to drawer menu
        setupSidebarInteractions();
    }
    
    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }
    
    private void loadDashboardData() {
        if (tvTotalOrders == null || tvTotalRevenue == null || 
            tvPendingOrders == null || tvCompletedOrders == null) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        // Show loading state on main thread
        if (mainHandler != null) {
            mainHandler.post(() -> {
                try {
                    tvTotalOrders.setText("...");
                    tvTotalRevenue.setText("...");
                    tvPendingOrders.setText("...");
                    tvCompletedOrders.setText("...");
                } catch (Exception e) {
                    android.util.Log.e("AdminDashboard", "Error setting loading state", e);
                }
            });
        }
        
        // Load data in background thread for better performance
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(() -> {
                try {
                    if (orderService == null) {
                        if (mainHandler != null) {
                            mainHandler.post(() -> {
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                        }
                        return;
                    }
                    
                    orderService.getAllOrders(new OrderService.OrderCallback() {
                    @Override
                    public void onSuccess(List<Order> orders) {
                        if (orders == null) {
                            return;
                        }
                        
                        // Process data in background
                        int totalOrders = orders.size();
                        int pendingCount = 0;
                        int completedCount = 0;
                        double totalRevenue = 0.0;
                        
                        for (Order order : orders) {
                            if (order == null) continue;
                            
                            String status = order.getStatus();
                            if (status != null) {
                                if ("pending".equalsIgnoreCase(status)) {
                                    pendingCount++;
                                } else if ("completed".equalsIgnoreCase(status)) {
                                    completedCount++;
                                    totalRevenue += order.getTotalAmount();
                                }
                            }
                        }
                        
                        final int finalTotalOrders = totalOrders;
                        final int finalPendingCount = pendingCount;
                        final int finalCompletedCount = completedCount;
                        final double finalTotalRevenue = totalRevenue;
                        
                        // Update UI on main thread
                        if (mainHandler != null) {
                            mainHandler.post(() -> {
                                if (tvTotalOrders != null) {
                                    tvTotalOrders.setText(String.valueOf(finalTotalOrders));
                                }
                                if (tvPendingOrders != null) {
                                    tvPendingOrders.setText(String.valueOf(finalPendingCount));
                                }
                                if (tvCompletedOrders != null) {
                                    tvCompletedOrders.setText(String.valueOf(finalCompletedCount));
                                }
                                if (tvTotalRevenue != null) {
                                    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
                                    currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
                                    tvTotalRevenue.setText(currencyFormat.format(finalTotalRevenue));
                                }
                                
                                // Update charts with real-time data
                                updateCharts(orders);
                                
                                // Stop refresh animation
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        if (mainHandler != null) {
                            mainHandler.post(() -> {
                                ToastUtil.show(AdminDashboardActivity.this, "Error loading data: " + error);
                                // Reset to default values on error
                                if (tvTotalOrders != null) {
                                    tvTotalOrders.setText("0");
                                }
                                if (tvTotalRevenue != null) {
                                    tvTotalRevenue.setText("₱0");
                                }
                                if (tvPendingOrders != null) {
                                    tvPendingOrders.setText("0");
                                }
                                if (tvCompletedOrders != null) {
                                    tvCompletedOrders.setText("0");
                                }
                                // Stop refresh animation
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                        }
                    }
                });
                } catch (Exception e) {
                    android.util.Log.e("AdminDashboard", "Error in executor", e);
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                            ToastUtil.show(AdminDashboardActivity.this,
                                "Error loading data: " + e.getMessage());
                        });
                    }
                }
            });
        } else {
            // Fallback if executor is not available
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }
    }
    
    private void bindSidebarHeader() {
        if (navigationView == null) {
            return;
        }
        
        if (sidebarHeaderView == null) {
            if (navigationView.getHeaderCount() == 0) {
                navigationView.inflateHeaderView(R.layout.nav_header_admin);
            }
            sidebarHeaderView = navigationView.getHeaderView(0);
        }
        
        View headerView = sidebarHeaderView;
        if (headerView == null) {
            return;
        }
        
        tvNavHeaderName = headerView.findViewById(R.id.tvNavHeaderName);
        tvNavHeaderEmail = headerView.findViewById(R.id.tvNavHeaderEmail);
        tvNavHeaderLevel = headerView.findViewById(R.id.tvNavHeaderLevel);
        imgSidebarAvatar = headerView.findViewById(R.id.imgSidebarAvatar);
        
        updateNavHeader();
    }
    
    private void setupSidebarInteractions() {
        if (sidebarHeaderView == null) {
            return;
        }
        
        // Dashboard
        setNavigationAction(R.id.btnNavDashboard, () -> navigateToActivity(AdminDashboardActivity.class));
        setNavigationAction(R.id.tvNavDashboardLabel, () -> navigateToActivity(AdminDashboardActivity.class));

        // Management -> Manage Items
        setNavigationAction(R.id.btnNavManagement, () -> navigateToActivity(ManageItemsActivity.class));
        setNavigationAction(R.id.tvNavManagementLabel, () -> navigateToActivity(ManageItemsActivity.class));

        // Live Orders
        setNavigationAction(R.id.btnNavLiveOrders, () -> navigateToActivity(ManageOrdersActivity.class));
        setNavigationAction(R.id.tvNavLiveOrdersLabel, () -> navigateToActivity(ManageOrdersActivity.class));

        // Users Management
        setNavigationAction(R.id.btnNavUsersManagement, () -> navigateToActivity(ManageUsersActivity.class));
        setNavigationAction(R.id.tvNavUsersManagementLabel, () -> navigateToActivity(ManageUsersActivity.class));

        // Notifications
        setNavigationAction(R.id.btnNavNotifications, () -> navigateToActivity(AdminNotificationsActivity.class));
        setNavigationAction(R.id.tvNavNotificationsLabel, () -> navigateToActivity(AdminNotificationsActivity.class));

        // Fake Booking Tracker
        setNavigationAction(R.id.btnNavFakeBooking, () -> navigateToActivity(FakeBookingTrackerActivity.class));
        setNavigationAction(R.id.tvNavFakeBookingLabel, () -> navigateToActivity(FakeBookingTrackerActivity.class));

        // Logout
        setNavigationAction(R.id.btnNavLogout, this::showLogoutDialog);
        setNavigationAction(R.id.tvNavLogoutLabel, this::showLogoutDialog);
    }
    
    private void setNavigationAction(int viewId, Runnable action) {
        if (sidebarHeaderView == null) {
            return;
        }
        View navItem = sidebarHeaderView.findViewById(viewId);
        if (navItem != null) {
            navItem.setOnClickListener(v -> {
                if (action != null) {
                    action.run();
                }
                closeDrawer();
            });
        }
    }
    
    private void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes", (dialog, which) -> {
                authService.logout();
                Intent intent = new Intent(this, com.fp.foodorderingsystem.activities.common.HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("No", null)
            .show();
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    // Menu removed - three-dot button no longer needed
    // @Override
    // public boolean onCreateOptionsMenu(Menu menu) {
    //     getMenuInflater().inflate(R.menu.admin_menu, menu);
    //     return true;
    // }
    
    // @Override
    // public boolean onOptionsItemSelected(MenuItem item) {
    //     if (item.getItemId() == R.id.menuLogout) {
    //         showLogoutDialog();
    //         return true;
    //     }
    //     return super.onOptionsItemSelected(item);
    // }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to dashboard - delay to avoid ANR
        if (preferenceUtil != null && preferenceUtil.isLoggedIn() && mainHandler != null) {
            loadNotifications(); // Load notifications immediately
            mainHandler.postDelayed(() -> {
                try {
                    loadDashboardData();
                } catch (Exception e) {
                    android.util.Log.e("AdminDashboard", "Error in onResume", e);
                }
            }, 300);
        }
    }
    
    private void setupRevenueChart() {
        if (chartRevenue == null) return;
        
        // Apply modern chart styling
        ChartStyleUtils.styleLineChart(chartRevenue);
        chartRevenue.setExtraOffsets(12f, 12f, 12f, 12f);
        chartRevenue.getLegend().setEnabled(false);
        
        // Set currency value formatter
        YAxis leftAxis = chartRevenue.getAxisLeft();
        leftAxis.setValueFormatter(new ChartStyleUtils.CurrencyValueFormatter());
    }
    
    private void setupOrdersChart() {
        if (chartOrders == null) return;
        
        // Apply modern chart styling
        ChartStyleUtils.stylePieChart(chartOrders);
        chartOrders.setCenterText("Orders");
        chartOrders.setCenterTextSize(16f);
        chartOrders.setCenterTextColor(ChartStyleUtils.Colors.TEXT_DARK);
        chartOrders.setExtraOffsets(8f, 8f, 8f, 8f);
    }
    
    private void setupPopularItemsChart() {
        if (chartPopularItems == null) return;
        
        // Apply modern chart styling
        ChartStyleUtils.styleBarChart(chartPopularItems);
        chartPopularItems.setExtraOffsets(12f, 12f, 12f, 12f);
        chartPopularItems.getLegend().setEnabled(false);
        
        // Customize X axis label rotation
        XAxis xAxis = chartPopularItems.getXAxis();
        xAxis.setLabelRotationAngle(-45f);
    }
    
    private void updateCharts(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            updateRevenueChart(new ArrayList<>());
            updateOrdersChart(new ArrayList<>());
            updatePopularItemsChart(new ArrayList<>());
            updateThroughputChart(new ArrayList<>());
            updateTrafficChart(new ArrayList<>());
            updateSatisfactionChart(new ArrayList<>());
            return;
        }
        
        updateRevenueChart(orders);
        updateOrdersChart(orders);
        updatePopularItemsChart(orders);
        updateThroughputChart(orders);
        updateTrafficChart(orders);
        updateSatisfactionChart(orders);
    }
    
    private void updateRevenueChart(List<Order> orders) {
        if (chartRevenue == null) return;
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        
        List<Entry> entries = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        
        for (int i = 0; i < 7; i++) {
            Date dayStart = calendar.getTime();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            Date dayEnd = calendar.getTime();
            
            double dayRevenue = 0.0;
            for (Order order : orders) {
                if (order == null || !"completed".equalsIgnoreCase(order.getStatus())) continue;
                
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date orderDate = sdf.parse(order.getCreatedAt());
                    if (orderDate != null && orderDate.after(dayStart) && orderDate.before(dayEnd)) {
                        dayRevenue += order.getTotalAmount();
                    }
                } catch (Exception e) {
                    // Skip invalid dates
                }
            }
            
            entries.add(new Entry(i, (float) dayRevenue));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "Revenue");
        // Apply modern styling with green primary color
        ChartStyleUtils.styleLineDataSet(dataSet, ChartStyleUtils.Colors.PRIMARY_GREEN);
        dataSet.setValueFormatter(new ChartStyleUtils.CurrencyValueFormatter());
        
        LineData lineData = new LineData(dataSet);
        chartRevenue.setData(lineData);
        
        // Set X-axis labels
        List<String> labels = new ArrayList<>();
        calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            labels.add(dateFormat.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        chartRevenue.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });
        
        // Enable smooth animation
        ChartStyleUtils.enableSmoothAnimations(chartRevenue);
        chartRevenue.invalidate();
    }
    
    private void updateOrdersChart(List<Order> orders) {
        if (chartOrders == null) return;
        
        Map<String, Integer> statusCount = new HashMap<>();
        statusCount.put("Pending", 0);
        statusCount.put("Confirmed", 0);
        statusCount.put("Preparing", 0);
        statusCount.put("Delivering", 0);
        statusCount.put("Completed", 0);
        statusCount.put("Cancelled", 0);
        
        for (Order order : orders) {
            if (order == null || order.getStatus() == null) continue;
            String status = order.getStatus();
            String statusKey = status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
            statusCount.put(statusKey, statusCount.getOrDefault(statusKey, 0) + 1);
        }
        
        List<PieEntry> entries = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : statusCount.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            }
        }
        
        if (entries.isEmpty()) {
            entries.add(new PieEntry(1f, "No Orders"));
        }
        
        PieDataSet dataSet = new PieDataSet(entries, "");
        // Use modern status colors
        dataSet.setColors(ChartStyleUtils.getStatusColors());
        // Apply modern pie chart styling
        ChartStyleUtils.stylePieDataSet(dataSet);
        
        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new ChartStyleUtils.IntegerValueFormatter());
        
        chartOrders.setData(pieData);
        // Enable smooth animation
        ChartStyleUtils.enableSmoothAnimations(chartOrders);
        chartOrders.invalidate();
    }
    
    private void updatePopularItemsChart(List<Order> orders) {
        if (chartPopularItems == null) return;
        
        Map<String, Integer> itemCount = new HashMap<>();
        for (Order order : orders) {
            if (order == null || !"completed".equalsIgnoreCase(order.getStatus())) continue;
            if (order.getItems() != null) {
                for (CartItem item : order.getItems()) {
                    if (item != null && item.getFoodItem() != null) {
                        String itemName = item.getFoodItem().getName();
                        int quantity = item.getQuantity();
                        itemCount.put(itemName, itemCount.getOrDefault(itemName, 0) + quantity);
                    }
                }
            }
        }
        
        List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemCount.entrySet());
        sortedItems.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        
        int count = Math.min(5, sortedItems.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = sortedItems.get(i);
            entries.add(new BarEntry(i, entry.getValue()));
            labels.add(entry.getKey().length() > 15 ? entry.getKey().substring(0, 15) + "..." : entry.getKey());
        }
        
        if (entries.isEmpty()) {
            entries.add(new BarEntry(0, 0));
            labels.add("No Data");
        }
        
        BarDataSet dataSet = new BarDataSet(entries, "Quantity Sold");
        // Apply modern bar chart styling with secondary blue color
        ChartStyleUtils.styleBarDataSet(dataSet, ChartStyleUtils.Colors.SECONDARY_BLUE);
        dataSet.setValueFormatter(new ChartStyleUtils.IntegerValueFormatter());
        
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        chartPopularItems.setData(barData);
        
        chartPopularItems.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });
        
        // Enable smooth animation
        ChartStyleUtils.enableSmoothAnimations(chartPopularItems);
        chartPopularItems.invalidate();
    }

    private void setupThroughputChart() {
        if (chartThroughput == null) return;
        
        // Apply modern bar chart styling
        ChartStyleUtils.styleBarChart(chartThroughput);
        chartThroughput.setExtraOffsets(8f, 8f, 8f, 8f);
        chartThroughput.getLegend().setEnabled(false);
    }

    private void updateThroughputChart(List<Order> orders) {
        if (chartThroughput == null) return;
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // Last 12 hours throughput
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat hourFmt = new SimpleDateFormat("ha", Locale.getDefault());
        for (int i = 11; i >= 0; i--) {
            Calendar slotStart = (Calendar) cal.clone();
            slotStart.add(Calendar.HOUR_OF_DAY, -i);
            Calendar slotEnd = (Calendar) slotStart.clone();
            slotEnd.add(Calendar.HOUR_OF_DAY, 1);

            int count = 0;
            for (Order o : orders) {
                if (o == null || o.getCreatedAt() == null) continue;
                try {
                    Date created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(o.getCreatedAt());
                    if (created != null && !created.before(slotStart.getTime()) && created.before(slotEnd.getTime())) {
                        count++;
                    }
                } catch (Exception ignored) { }
            }
            int index = 11 - i;
            entries.add(new BarEntry(index, count));
            labels.add(hourFmt.format(slotStart.getTime()));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Orders/hour");
        // Use accent orange color for throughput
        ChartStyleUtils.styleBarDataSet(dataSet, ChartStyleUtils.Colors.ACCENT_ORANGE);
        
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        
        chartThroughput.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < labels.size()) ? labels.get(idx) : "";
            }
        });

        chartThroughput.setData(barData);
        ChartStyleUtils.enableSmoothAnimations(chartThroughput);
        chartThroughput.invalidate();
    }

    private void setupTrafficChart() {
        if (chartTraffic == null) return;
        
        // Apply modern line chart styling
        ChartStyleUtils.styleLineChart(chartTraffic);
        chartTraffic.setExtraOffsets(10f, 10f, 10f, 10f);
        chartTraffic.getLegend().setEnabled(false);
    }

    private void updateTrafficChart(List<Order> orders) {
        if (chartTraffic == null) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -6);

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("MMM dd", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            Date start = cal.getTime();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            Date end = cal.getTime();

            int count = 0;
            for (Order o : orders) {
                if (o == null || o.getCreatedAt() == null) continue;
                try {
                    Date created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(o.getCreatedAt());
                    if (created != null && created.after(start) && created.before(end)) {
                        count++;
                    }
                } catch (Exception ignored) { }
            }
            entries.add(new Entry(i, count));
            labels.add(df.format(start));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Orders (7d)");
        // Use secondary blue color for traffic chart
        ChartStyleUtils.styleLineDataSet(dataSet, ChartStyleUtils.Colors.SECONDARY_BLUE);
        dataSet.setValueFormatter(new ChartStyleUtils.IntegerValueFormatter());

        chartTraffic.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < labels.size()) ? labels.get(idx) : "";
            }
        });

        chartTraffic.setData(new LineData(dataSet));
        ChartStyleUtils.enableSmoothAnimations(chartTraffic);
        chartTraffic.invalidate();
    }

    private void setupSatisfactionChart() {
        if (chartSatisfaction == null) return;
        
        // Apply modern pie chart styling
        ChartStyleUtils.stylePieChart(chartSatisfaction);
        chartSatisfaction.setCenterText("Status");
        chartSatisfaction.setCenterTextSize(16f);
        chartSatisfaction.setCenterTextColor(ChartStyleUtils.Colors.TEXT_DARK);
    }

    private void updateSatisfactionChart(List<Order> orders) {
        if (chartSatisfaction == null) return;
        Map<String, Integer> buckets = new HashMap<>();
        buckets.put("Completed", 0);
        buckets.put("Pending", 0);
        buckets.put("Cancelled", 0);

        for (Order o : orders) {
            if (o == null || o.getStatus() == null) continue;
            String status = o.getStatus().toLowerCase(Locale.US);
            if (status.contains("cancel")) {
                buckets.put("Cancelled", buckets.get("Cancelled") + 1);
            } else if (status.contains("complete")) {
                buckets.put("Completed", buckets.get("Completed") + 1);
            } else {
                buckets.put("Pending", buckets.get("Pending") + 1);
            }
        }

        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : buckets.entrySet()) {
            if (e.getValue() > 0) {
                entries.add(new PieEntry(e.getValue(), e.getKey()));
            }
        }
        if (entries.isEmpty()) {
            entries.add(new PieEntry(1f, "No Data"));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        // Use status colors from ChartStyleUtils
        dataSet.setColors(ChartStyleUtils.getStatusColors());
        ChartStyleUtils.stylePieDataSet(dataSet);
        dataSet.setValueFormatter(new ChartStyleUtils.IntegerValueFormatter());

        chartSatisfaction.setData(new PieData(dataSet));
        ChartStyleUtils.enableSmoothAnimations(chartSatisfaction);
        chartSatisfaction.invalidate();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        disconnectRealtime();
    }

    private void subscribeToRealtimeStreams() {
        ordersRealtimeClient = new SupabaseRealtimeClient();
        menuRealtimeClient = new SupabaseRealtimeClient();
        notificationRealtimeClient = new SupabaseRealtimeClient();

        RealtimeListener orderListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                if (mainHandler != null) {
                    mainHandler.post(AdminDashboardActivity.this::loadDashboardData);
                }
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("AdminDashboard", "Order realtime error: " + error);
            }
        };

        RealtimeListener menuListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                if (mainHandler != null) {
                    mainHandler.post(AdminDashboardActivity.this::loadDashboardData);
                }
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("AdminDashboard", "Menu realtime error: " + error);
            }
        };

        RealtimeListener notificationListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                if (mainHandler != null) {
                    mainHandler.post(AdminDashboardActivity.this::loadNotifications);
                }
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("AdminDashboard", "Notification realtime error: " + error);
            }
        };

        ordersRealtimeClient.subscribeToTable("public", "orders", orderListener);
        menuRealtimeClient.subscribeToTable("public", "menu_items", menuListener);
        notificationRealtimeClient.subscribeToTable("public", "notifications", notificationListener);
    }

    private void loadNotifications() {
        String userId = preferenceUtil.getUserId();
        if (android.text.TextUtils.isEmpty(userId)) {
            updateNotificationBadge(0);
            return;
        }
        
        notificationService.getAllNotifications(new com.fp.foodorderingsystem.services.NotificationService.NotificationCallback() {
            @Override
            public void onSuccess(List<com.fp.foodorderingsystem.models.Notification> notifications) {
                mainHandler.post(() -> {
                    int unreadCount = 0;
                    if (notifications != null) {
                        for (com.fp.foodorderingsystem.models.Notification notification : notifications) {
                            if (notification != null && !notification.isRead()) {
                                unreadCount++;
                            }
                        }
                    }
                    updateNotificationBadge(unreadCount);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    updateNotificationBadge(0);
                });
            }
        });
    }
    
    private void updateNotificationBadge(int count) {
        if (tvNotificationBadge != null) {
            if (count > 0) {
                tvNotificationBadge.setVisibility(TextView.VISIBLE);
                tvNotificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                tvNotificationBadge.setVisibility(TextView.GONE);
            }
        }
    }

    private void disconnectRealtime() {
        if (ordersRealtimeClient != null) {
            ordersRealtimeClient.disconnect();
        }
        if (menuRealtimeClient != null) {
            menuRealtimeClient.disconnect();
        }
        if (notificationRealtimeClient != null) {
            notificationRealtimeClient.disconnect();
        }
    }
}

