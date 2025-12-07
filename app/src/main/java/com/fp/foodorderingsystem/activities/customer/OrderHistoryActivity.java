package com.fp.foodorderingsystem.activities.customer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.CustomerOrderAdapter;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.RealtimePayloadUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {
    private RecyclerView rvOrders;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ChipGroup chipStatusFilters;
    private TextView tvPendingCount, tvPreparingCount, tvDeliveringCount, tvCompletedCount;
    private View emptyStateLayout;
    private CustomerOrderAdapter orderAdapter;
    private final List<Order> orders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();
    private OrderService orderService;
    private PreferenceUtil preferenceUtil;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String userId;
    private SupabaseRealtimeClient orderRealtimeClient;
    private String currentStatusFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_order);

        orderService = new OrderService(this);
        preferenceUtil = new PreferenceUtil(this);
        userId = preferenceUtil.getUserId();

        if (!preferenceUtil.isLoggedIn() || TextUtils.isEmpty(userId)) {
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupBottomNavigation();
        loadOrders(true);
        subscribeToRealtimeOrders();
    }

    private void initViews() {
        rvOrders = findViewById(R.id.rvOrders);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        chipStatusFilters = findViewById(R.id.chipStatusFilters);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvPreparingCount = findViewById(R.id.tvPreparingCount);
        tvDeliveringCount = findViewById(R.id.tvDeliveringCount);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        emptyStateLayout = findViewById(R.id.layoutEmptyState);

        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, CustomerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadOrders(false));
            swipeRefreshLayout.setColorSchemeResources(
                R.color.green_primary,
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light
            );
        }

        if (chipStatusFilters != null) {
            chipStatusFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds == null || checkedIds.isEmpty()) {
                    currentStatusFilter = "all";
                } else {
                    int checkedId = checkedIds.get(0);
                    if (checkedId == R.id.chipPending) {
                        currentStatusFilter = "pending";
                    } else if (checkedId == R.id.chipPreparing) {
                        currentStatusFilter = "preparing";
                    } else if (checkedId == R.id.chipDelivering) {
                        currentStatusFilter = "delivering";
                    } else if (checkedId == R.id.chipCompleted) {
                        currentStatusFilter = "completed";
                    } else {
                        currentStatusFilter = "all";
                    }
                }
                applyFilter();
            });
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.menuOrders);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menuHome) {
                startActivity(new Intent(this, CustomerDashboardActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuMenu) {
                startActivity(new Intent(this, MenuActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuCart) {
                startActivity(new Intent(this, CartActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuOrders) {
                return true;
            } else if (itemId == R.id.menuProfile) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        orderAdapter = new CustomerOrderAdapter(filteredOrders);
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        rvOrders.setAdapter(orderAdapter);
        orderAdapter.setOnItemClickListener(order -> {
            Intent intent = new Intent(OrderHistoryActivity.this, OrderDetailActivity.class);
            intent.putExtra("order_id", order.getId());
            intent.putExtra("order_id_string", order.getIdString());
            startActivity(intent);
        });
    }

    private void loadOrders(boolean showToast) {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            if (showToast) {
                ToastUtil.show(this, "No internet connection");
            }
            updateEmptyState();
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        orderService.getOrders(userId, new OrderService.OrderCallback() {
            @Override
            public void onSuccess(List<Order> orderList) {
                mainHandler.post(() -> {
                    orders.clear();
                    if (orderList != null) {
                        orders.addAll(orderList);
                    }
                    sortOrders();
                    updateSummary();
                    applyFilter();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    if (showToast) {
                        ToastUtil.show(OrderHistoryActivity.this, error);
                    }
                    updateEmptyState();
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        });
    }

    private void subscribeToRealtimeOrders() {
        orderRealtimeClient = new SupabaseRealtimeClient();
        orderRealtimeClient.subscribeToTable("public", "orders", new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                if (belongsToCurrentUser(payload)) {
                    mainHandler.post(() -> loadOrders(false));
                }
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("OrderHistory", "Realtime order error: " + error);
            }
        });
    }

    private boolean belongsToCurrentUser(JsonObject payload) {
        JsonObject record = RealtimePayloadUtil.getRelevantRecord(payload);
        if (record != null && record.has("customer_id")) {
            return userId != null && userId.equals(record.get("customer_id").getAsString());
        }
        return false;
    }

    private void sortOrders() {
        Collections.sort(orders, (a, b) -> {
            String dateA = a != null ? a.getCreatedAt() : null;
            String dateB = b != null ? b.getCreatedAt() : null;
            if (dateA == null && dateB == null) {
                return 0;
            }
            if (dateA == null) {
                return 1;
            }
            if (dateB == null) {
                return -1;
            }
            return dateB.compareTo(dateA);
        });
    }

    private void updateSummary() {
        int pending = 0, preparing = 0, delivering = 0, completed = 0;
        for (Order order : orders) {
            if (order == null) continue;
            String status = order.getStatus();
            if ("pending".equalsIgnoreCase(status)) {
                pending++;
            } else if ("preparing".equalsIgnoreCase(status)) {
                preparing++;
            } else if ("delivering".equalsIgnoreCase(status)) {
                delivering++;
            } else if ("completed".equalsIgnoreCase(status)) {
                completed++;
            }
        }

        if (tvPendingCount != null) tvPendingCount.setText(String.valueOf(pending));
        if (tvPreparingCount != null) tvPreparingCount.setText(String.valueOf(preparing));
        if (tvDeliveringCount != null) tvDeliveringCount.setText(String.valueOf(delivering));
        if (tvCompletedCount != null) tvCompletedCount.setText(String.valueOf(completed));
    }

    private void applyFilter() {
        filteredOrders.clear();
        for (Order order : orders) {
            if (order == null) continue;
            if ("all".equalsIgnoreCase(currentStatusFilter)) {
                filteredOrders.add(order);
            } else if (currentStatusFilter.equalsIgnoreCase(order.getStatus())) {
                filteredOrders.add(order);
            }
        }
        orderAdapter.updateList(new ArrayList<>(filteredOrders));
        updateEmptyState();
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateEmptyState() {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!preferenceUtil.isLoggedIn() || TextUtils.isEmpty(userId)) {
            finish();
            return;
        }
        loadOrders(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orderRealtimeClient != null) {
            orderRealtimeClient.disconnect();
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}


