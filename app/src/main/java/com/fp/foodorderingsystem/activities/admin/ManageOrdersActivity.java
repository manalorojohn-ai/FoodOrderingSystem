package com.fp.foodorderingsystem.activities.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.OrderAdapter;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ManageOrdersActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    private RecyclerView rvOrders;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextInputLayout inputSearchLayout;
    private TextInputEditText inputSearch;
    private ChipGroup chipGroupStatus;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvEmptyState;
    
    private OrderService orderService;
    private AuthService authService;
    private SupabaseRealtimeClient realtimeClient;
    private OrderAdapter orderAdapter;
    private List<Order> allOrders = new ArrayList<>();
    private List<Order> filteredOrders = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    
    private String currentSearchQuery = "";
    private String currentFilterStatus = "all";
    
    private static final String[] ORDER_STATUSES = {
        "pending", "confirmed", "preparing", "ready", "delivering", "completed", "cancelled"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_orders);
        
        orderService = new OrderService(this);
        authService = new AuthService(this);
        realtimeClient = new SupabaseRealtimeClient();
        
        initViews();
        setupRecyclerView();
        setupListeners();
        loadOrders(true);
        subscribeToRealtimeUpdates();
    }
    
    private void initViews() {
        rvOrders = findViewById(R.id.rvOrders);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        inputSearchLayout = findViewById(R.id.inputSearchLayout);
        inputSearch = findViewById(R.id.inputSearch);
        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void setupRecyclerView() {
        orderAdapter = new OrderAdapter(filteredOrders);
        rvOrders.setAdapter(orderAdapter);
        
        // Add click listener to view and control orders
        orderAdapter.setOnItemClickListener(order -> {
            showOrderControlDialog(order);
        });
        
        // Add quick action listener
        orderAdapter.setOnQuickActionClickListener(order -> {
            showStatusUpdateDialog(order);
        });
    }
    
    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(this);
        
        // Search removed from UI; guard in case layout still missing the view
        if (inputSearch != null) {
            inputSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s != null ? s.toString().trim() : "";
                    applyFilters();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        } else {
            currentSearchQuery = "";
        }
        
        // Status filter listener
        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            Chip checkedChip = group.findViewById(group.getCheckedChipId());
            if (checkedChip != null) {
                String chipText = checkedChip.getText().toString().toLowerCase();
                if ("all".equals(chipText)) {
                    currentFilterStatus = "all";
                } else {
                    currentFilterStatus = chipText;
                }
            } else {
                currentFilterStatus = "all";
            }
            applyFilters();
        });
    }
    
    private void applyFilters() {
        filteredOrders.clear();
        
        for (Order order : allOrders) {
            // Search removed from UI; always match
            boolean matchesSearch = true;

            // Status filter
            boolean matchesStatus = true;
            if (!"all".equals(currentFilterStatus)) {
                matchesStatus = order.getStatus() != null &&
                              order.getStatus().toLowerCase().equals(currentFilterStatus);
            }

            if (matchesSearch && matchesStatus) {
                filteredOrders.add(order);
            }
        }
        
        sortOrders();
        orderAdapter.updateList(new ArrayList<>(filteredOrders));
        updateEmptyState();
    }
    
    private void updateEmptyState() {
        boolean isEmpty = filteredOrders.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvOrders.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        if (isEmpty) {
            if (allOrders.isEmpty()) {
                tvEmptyState.setText("No orders yet");
            } else {
                tvEmptyState.setText("No orders match your filters");
            }
        }
    }
    
    private void showOrderControlDialog(Order order) {
        if (order == null) {
            return;
        }
        
        // Create a more visually appealing dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Order #" + order.getId());
        
        // Build formatted order details
        StringBuilder message = new StringBuilder();
        message.append("📦 Status: ").append(capitalize(order.getStatus())).append("\n\n");
        message.append("💰 Total: ₱").append(String.format("%.2f", order.getTotalAmount())).append("\n");
        message.append("💳 Payment: ").append(capitalize(order.getPaymentMethod() != null ? order.getPaymentMethod() : "N/A")).append("\n");
        if (order.getDeliveryAddress() != null) {
            message.append("📍 Address: ").append(order.getDeliveryAddress()).append("\n");
        }
        if (order.getCreatedAt() != null) {
            String dateStr = order.getCreatedAt();
            if (dateStr.length() > 10) {
                dateStr = dateStr.substring(0, 10);
            }
            message.append("📅 Date: ").append(dateStr).append("\n");
        }
        
        builder.setMessage(message.toString());
        
        // Add action buttons with icons
        builder.setPositiveButton("👁️ View Details", (dialog, which) -> {
            Intent intent = new Intent(ManageOrdersActivity.this, 
                com.fp.foodorderingsystem.activities.customer.OrderDetailActivity.class);
            intent.putExtra("order_id", order.getId());
            startActivity(intent);
        });
        
        builder.setNeutralButton("✏️ Update Status", (dialog, which) -> {
            showStatusUpdateDialog(order);
        });
        
        builder.setNegativeButton("Close", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.green_primary));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getColor(R.color.green_primary));
    }
    
    private void showStatusUpdateDialog(Order order) {
        if (order == null) {
            return;
        }
        
        String statusValue = order.getStatus();
        if (statusValue == null) {
            statusValue = "pending";
        }
        final String currentStatus = statusValue;
        
        // Create list of statuses with emojis for better UX
        String[] statusArray = {
            "⏳ Pending",
            "✅ Confirmed", 
            "👨‍🍳 Preparing",
            "📦 Ready",
            "🚚 Delivering",
            "✔️ Completed",
            "❌ Cancelled"
        };
        
        String[] statusValues = ORDER_STATUSES;
        
        // Find current status index
        int currentIndex = -1;
        for (int i = 0; i < statusValues.length; i++) {
            if (statusValues[i].equalsIgnoreCase(currentStatus)) {
                currentIndex = i;
                break;
            }
        }
        
        // Build display items - show all statuses
        String[] displayItems = new String[statusArray.length];
        for (int i = 0; i < statusArray.length; i++) {
            displayItems[i] = statusArray[i];
        }
        
        // Create dialog with items list - remove message to ensure items are visible
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Order Status\nCurrent: " + capitalize(currentStatus));
        
        builder.setItems(displayItems, (dialog, which) -> {
            String newStatus = statusValues[which];
            if (!newStatus.equalsIgnoreCase(currentStatus)) {
                updateOrderStatus(order, newStatus);
                dialog.dismiss();
            } else {
                Toast.makeText(ManageOrdersActivity.this, 
                    "Status is already " + capitalize(newStatus), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void updateOrderStatus(Order order, String newStatus) {
        String accessToken = authService.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            // Try to refresh token first
            authService.refreshAccessToken(new AuthService.TokenCallback() {
                @Override
                public void onSuccess(String newAccessToken) {
                    performStatusUpdate(order, newStatus, newAccessToken);
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageOrdersActivity.this, 
                            "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
                    });
                }
            });
            return;
        }
        
        performStatusUpdate(order, newStatus, accessToken);
    }
    
    private void performStatusUpdate(Order order, String newStatus, String accessToken) {
        // Show loading
        showLoading(true);
        
        // Use UUID string for Supabase realtime database
        String orderIdString = order.getIdString();
        if (orderIdString == null || orderIdString.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Invalid order ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        orderService.updateOrderStatus(orderIdString, newStatus, accessToken, 
            new OrderService.UpdateStatusCallback() {
                @Override
                public void onSuccess() {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(ManageOrdersActivity.this, 
                            "✓ Order status updated to " + capitalize(newStatus), Toast.LENGTH_SHORT).show();
                        // The realtime update will automatically refresh the list
                        // But also refresh manually to ensure UI is updated
                        loadOrders(false);
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(ManageOrdersActivity.this, 
                            "✗ " + error, Toast.LENGTH_LONG).show();
                        android.util.Log.e("ManageOrdersActivity", "Status update error: " + error);
                    });
                }
            });
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    private void loadOrders(boolean showProgress) {
        if (showProgress) {
            showLoading(true);
        }
        
        orderService.getAllOrders(new OrderService.OrderCallback() {
            @Override
            public void onSuccess(List<Order> orderList) {
                mainHandler.post(() -> {
                    allOrders.clear();
                    if (orderList != null) {
                        allOrders.addAll(orderList);
                    }
                    applyFilters();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageOrdersActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    updateEmptyState();
                });
            }
        });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
    
    @Override
    public void onRefresh() {
        loadOrders(false);
    }
    
    private void sortOrders() {
        Collections.sort(filteredOrders, (a, b) -> {
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
            return dateB.compareTo(dateA); // Most recent first
        });
    }
    
    private void subscribeToRealtimeUpdates() {
        realtimeClient.subscribeToTable("public", "orders", new RealtimeListener() {
            @Override
            public void onOpen() {
                // Connection established
            }

            @Override
            public void onChange(JsonObject payload) {
                handleRealtimePayload(payload);
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() ->
                    Toast.makeText(ManageOrdersActivity.this, "Realtime error: " + error, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
    
    private void handleRealtimePayload(JsonObject payload) {
        if (payload == null) {
            return;
        }
        
        String eventTypeValue = "";
        if (payload.has("eventType")) {
            eventTypeValue = payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            eventTypeValue = payload.get("type").getAsString();
        }

        final String eventType = eventTypeValue;

        JsonObject newRecord = null;
        if (payload.has("new")) {
            newRecord = payload.getAsJsonObject("new");
        } else if (payload.has("new_record")) {
            newRecord = payload.getAsJsonObject("new_record");
        }

        JsonObject oldRecord = null;
        if (payload.has("old")) {
            oldRecord = payload.getAsJsonObject("old");
        } else if (payload.has("old_record")) {
            oldRecord = payload.getAsJsonObject("old_record");
        }

        switch (eventType.toUpperCase()) {
            case "INSERT":
                if (newRecord != null) {
                    try {
                        Order newOrder = gson.fromJson(newRecord, Order.class);
                        if (newOrder != null) {
                            mainHandler.post(() -> {
                                upsertLocalOrder(newOrder);
                                android.util.Log.d("ManageOrdersActivity", "Realtime insert: order " + newOrder.getId());
                            });
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ManageOrdersActivity", "Error parsing order from realtime payload", e);
                    }
                }
                break;
            case "UPDATE":
                if (newRecord != null) {
                    try {
                        Order updatedOrder = gson.fromJson(newRecord, Order.class);
                        if (updatedOrder != null) {
                            mainHandler.post(() -> {
                                upsertLocalOrder(updatedOrder);
                                android.util.Log.d("ManageOrdersActivity", "Realtime update: order " + updatedOrder.getId());
                            });
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ManageOrdersActivity", "Error parsing order from realtime payload", e);
                    }
                }
                break;
            case "DELETE":
                if (oldRecord != null && oldRecord.has("id")) {
                    try {
                        int orderId = oldRecord.get("id").getAsInt();
                        mainHandler.post(() -> {
                            removeLocalOrder(orderId);
                            android.util.Log.d("ManageOrdersActivity", "Realtime delete: order " + orderId);
                        });
                    } catch (Exception e) {
                        android.util.Log.e("ManageOrdersActivity", "Error parsing order ID from delete payload", e);
                    }
                }
                break;
            default:
                // Ignore other events
                android.util.Log.d("ManageOrdersActivity", "Ignoring realtime event: " + eventType);
        }
    }
    
    private void upsertLocalOrder(Order order) {
        if (order == null) {
            return;
        }
        boolean found = false;
        for (int i = 0; i < allOrders.size(); i++) {
            if (allOrders.get(i).getId() == order.getId()) {
                allOrders.set(i, order);
                found = true;
                break;
            }
        }
        if (!found) {
            allOrders.add(0, order);
        }
        applyFilters();
    }
    
    private void removeLocalOrder(int orderId) {
        for (int i = 0; i < allOrders.size(); i++) {
            if (allOrders.get(i).getId() == orderId) {
                allOrders.remove(i);
                break;
            }
        }
        applyFilters();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
    }
}

