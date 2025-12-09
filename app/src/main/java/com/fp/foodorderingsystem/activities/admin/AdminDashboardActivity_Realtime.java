package com.fp.foodorderingsystem.activities.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.navigation.NavigationView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.RealtimeDashboardManager;
import com.fp.foodorderingsystem.services.RealtimeDashboardManager.DashboardMetrics;
import com.fp.foodorderingsystem.services.RealtimeDashboardManager.DashboardMetricsListener;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * AdminDashboardActivity_Realtime - Real-time Analytics Dashboard
 *
 * Features:
 * - Real-time metrics updates (orders, revenue, completion rate)
 * - Live connection status indicator
 * - Automatic data synchronization
 * - Offline support with local cache
 * - Beautiful Material Design 3 UI
 */
public class AdminDashboardActivity_Realtime extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "AdminDashboard";

    // UI Views
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View statusIndicator;

    // Dashboard Metrics Views
    private TextView tvTotalOrders;
    private TextView tvTotalRevenue;
    private TextView tvPendingOrders;
    private TextView tvCompletedOrders;
    private TextView tvCompletionRate;
    private TextView tvSyncTime;

    // Services
    private OrderService orderService;
    private AuthService authService;
    private PreferenceUtil preferenceUtil;
    private RealtimeDashboardManager realtimeDashboardManager;

    // Utilities
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());

    private final DashboardMetricsListener metricsListener = new DashboardMetricsListener() {
        @Override
        public void onMetricsUpdated(DashboardMetrics metrics) {
            mainHandler.post(() -> updateMetricsUI(metrics));
        }

        @Override
        public void onConnectionStateChanged(boolean connected) {
            mainHandler.post(() -> updateConnectionStatus(connected));
        }

        @Override
        public void onError(String error) {
            mainHandler.post(() -> {
                Log.e(TAG, "Realtime error: " + error);
                if (error != null && !error.contains("connection")) {
                    Toast.makeText(AdminDashboardActivity_Realtime.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Check authentication
        if (!preferenceUtil.isLoggedIn() || !preferenceUtil.getUserType().equals("admin")) {
            finish();
            return;
        }

        // Initialize services
        orderService = new OrderService(this);
        authService = new AuthService(this);
        preferenceUtil = new PreferenceUtil(this);
        realtimeDashboardManager = new RealtimeDashboardManager(orderService);

        // Initialize UI
        initViews();
        setupDrawer();
        setupSwipeRefresh();

        // Start real-time sync
        startRealtimeSync();
    }

    /**
     * Initialize UI views
     */
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Dashboard");
                getSupportActionBar().setSubtitle("Real-time Analytics");
            }
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Metrics views
        tvTotalOrders = findViewById(R.id.tvTotalOrders);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvPendingOrders = findViewById(R.id.tvPendingOrders);
        tvCompletedOrders = findViewById(R.id.tvCompletedOrders);
        tvCompletionRate = findViewById(R.id.tvCompletionRate);
        tvSyncTime = findViewById(R.id.tvSyncTime);
    }

    /**
     * Setup drawer navigation
     */
    private void setupDrawer() {
        if (drawerLayout == null || toolbar == null) {
            return;
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                
                if (id == R.id.nav_dashboard) {
                    // Already on dashboard
                } else if (id == R.id.nav_orders) {
                    startActivity(new Intent(AdminDashboardActivity_Realtime.this, ManageOrdersActivity.class));
                } else if (id == R.id.nav_items) {
                    startActivity(new Intent(AdminDashboardActivity_Realtime.this, ManageItemsActivity.class));
                } else if (id == R.id.nav_categories) {
                    startActivity(new Intent(AdminDashboardActivity_Realtime.this, ManageCategoriesActivity.class));
                } else if (id == R.id.nav_users) {
                    startActivity(new Intent(AdminDashboardActivity_Realtime.this, ManageUsersActivity_Realtime.class));
                } else if (id == R.id.nav_bookings) {
                    Toast.makeText(this, "Fake Booking Feature", Toast.LENGTH_SHORT).show();
                    // Navigate to BookingActivity when available
                } else if (id == R.id.nav_profile) {
                    Toast.makeText(this, "Profile Settings", Toast.LENGTH_SHORT).show();
                    // Navigate to ProfileActivity when available
                } else if (id == R.id.nav_logout) {
                    logout();
                }
                
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }
    }

    /**
     * Setup swipe to refresh
     */
    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this);
            swipeRefreshLayout.setColorSchemeResources(
                    R.color.green_primary,
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light
            );
        }
    }

    /**
     * Start real-time synchronization
     */
    private void startRealtimeSync() {
        String accessToken = authService.getAccessToken();
        realtimeDashboardManager.addListener(metricsListener);
        realtimeDashboardManager.start(accessToken);

        Log.d(TAG, "Real-time dashboard synchronization started");
    }

    /**
     * Update metrics UI with latest data
     */
    private void updateMetricsUI(DashboardMetrics metrics) {
        if (metrics == null) return;

        // Update text views
        if (tvTotalOrders != null) {
            tvTotalOrders.setText(String.valueOf(metrics.totalOrders));
        }

        if (tvTotalRevenue != null) {
            tvTotalRevenue.setText(currencyFormat.format(metrics.totalRevenue));
        }

        if (tvPendingOrders != null) {
            tvPendingOrders.setText(String.valueOf(metrics.pendingOrders));
        }

        if (tvCompletedOrders != null) {
            tvCompletedOrders.setText(String.valueOf(metrics.completedOrders));
        }

        if (tvCompletionRate != null) {
            tvCompletionRate.setText(String.format(Locale.getDefault(), "%.1f%%", metrics.completionRate));
        }

        if (tvSyncTime != null) {
            long syncTime = System.currentTimeMillis() - metrics.lastSyncTime;
            if (syncTime < 1000) {
                tvSyncTime.setText("Just now");
            } else if (syncTime < 60000) {
                tvSyncTime.setText((syncTime / 1000) + "s ago");
            } else {
                tvSyncTime.setText((syncTime / 60000) + "m ago");
            }
        }

        Log.d(TAG, "Metrics updated: " + metrics);
    }

    /**
     * Update connection status indicator
     */
    private void updateConnectionStatus(boolean connected) {
        if (statusIndicator == null) return;

        if (connected) {
            statusIndicator.setBackgroundColor(Color.GREEN);
            Toast.makeText(this, "Connected to real-time", Toast.LENGTH_SHORT).show();
        } else {
            statusIndicator.setBackgroundColor(Color.RED);
            Toast.makeText(this, "Using offline cache", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Logout and return to login
     */
    private void logout() {
        authService.logout();
        preferenceUtil.logout();
        finish();
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "Manual refresh triggered");
        // Real-time manager handles continuous updates
        mainHandler.postDelayed(() -> {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }, 500);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeDashboardManager != null) {
            realtimeDashboardManager.removeListener(metricsListener);
            realtimeDashboardManager.stop();
        }
    }
}
