package com.fp.foodorderingsystem.activities.customer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.ReceiptItemAdapter;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.NotificationService;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.RealtimePayloadUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class OrderDetailActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 100;
    
    private TextView tvOrderId, tvStatus, tvTotalAmount, tvPaymentMethod, tvDeliveryAddress, tvOrderDate;
    private RecyclerView rvOrderItems;
    private View layoutEmptyItems;
    private ProgressBar progressBar;
    private com.google.android.material.button.MaterialButton btnCancelOrder, btnOrderReceived, btnDownloadReceipt;
    
    private OrderService orderService;
    private AuthService authService;
    private NotificationService notificationService;
    private PreferenceUtil preferenceUtil;
    private ReceiptItemAdapter itemsAdapter;
    private Order currentOrder;
    private int orderId;
    private String orderIdString; // UUID string from Supabase
    private String userId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NumberFormat currencyFormat;
    private SupabaseRealtimeClient orderRealtimeClient;
    private SupabaseRealtimeClient orderItemsRealtimeClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        
        orderId = getIntent().getIntExtra("order_id", -1);
        orderIdString = getIntent().getStringExtra("order_id_string");
        
        if (orderId == -1 && (orderIdString == null || orderIdString.isEmpty())) {
            Toast.makeText(this, "Invalid order", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        orderService = new OrderService(this);
        authService = new AuthService(this);
        notificationService = new NotificationService(this);
        preferenceUtil = new PreferenceUtil(this);
        userId = preferenceUtil.getUserId();
        
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
        
        initViews();
        setupToolbar();
        setupRecyclerView();
        loadOrderDetails(true);
        subscribeToRealtimeUpdates();
    }
    
    private void initViews() {
        tvOrderId = findViewById(R.id.tvOrderId);
        tvStatus = findViewById(R.id.tvStatus);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        tvDeliveryAddress = findViewById(R.id.tvDeliveryAddress);
        tvOrderDate = findViewById(R.id.tvOrderDate);
        rvOrderItems = findViewById(R.id.rvOrderItems);
        layoutEmptyItems = findViewById(R.id.layoutEmptyItems);
        progressBar = findViewById(R.id.progressBar);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        btnOrderReceived = findViewById(R.id.btnOrderReceived);
        btnDownloadReceipt = findViewById(R.id.btnDownloadReceipt);
        
        btnCancelOrder.setOnClickListener(v -> showCancelOrderDialog());
        btnOrderReceived.setOnClickListener(v -> markOrderAsReceived());
        btnDownloadReceipt.setOnClickListener(v -> downloadReceipt());
    }
    
    private void setupToolbar() {
        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerView() {
        itemsAdapter = new ReceiptItemAdapter(new ArrayList<>());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setAutoMeasureEnabled(true);
        rvOrderItems.setLayoutManager(layoutManager);
        rvOrderItems.setAdapter(itemsAdapter);
        rvOrderItems.setHasFixedSize(false);
        rvOrderItems.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        rvOrderItems.setVisibility(View.VISIBLE);
        rvOrderItems.setNestedScrollingEnabled(false);
        android.util.Log.d("OrderDetailActivity", "RecyclerView setup complete. Adapter item count: " + itemsAdapter.getItemCount());
    }
    
    private void loadOrderDetails() {
        loadOrderDetails(true);
    }
    
    private void loadOrderDetails(boolean showSpinner) {
        if (showSpinner) {
            showLoading(true);
        }
        
        android.util.Log.d("OrderDetailActivity", "Loading order details. orderId: " + orderId + ", orderIdString: " + orderIdString);
        
        // Use UUID string if available (preferred), otherwise fall back to numeric ID
        if (orderIdString != null && !orderIdString.isEmpty()) {
            android.util.Log.d("OrderDetailActivity", "Using getOrderByIdString with: " + orderIdString);
            orderService.getOrderByIdString(orderIdString, userId, new OrderService.SingleOrderCallback() {
                @Override
                public void onSuccess(Order order) {
                    android.util.Log.d("OrderDetailActivity", "Order loaded successfully. Items count: " + (order.getItems() != null ? order.getItems().size() : "null"));
                    mainHandler.post(() -> {
                        currentOrder = order;
                        displayOrderDetails(order);
                            if (showSpinner) {
                                showLoading(false);
                            }
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        if (showSpinner) {
                            showLoading(false);
                        }
                        Toast.makeText(OrderDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } else {
            // Fallback to numeric ID query (less efficient but works)
            orderService.getOrderById(orderId, userId, new OrderService.SingleOrderCallback() {
                @Override
                public void onSuccess(Order order) {
                    mainHandler.post(() -> {
                        currentOrder = order;
                        displayOrderDetails(order);
                        if (showSpinner) {
                            showLoading(false);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        if (showSpinner) {
                            showLoading(false);
                        }
                        Toast.makeText(OrderDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        }
    }
    
    private void displayOrderDetails(Order order) {
        if (order == null) {
            return;
        }
        
        // Order ID
        tvOrderId.setText("#" + order.getId());
        
        // Status
        tvStatus.setText(capitalizeFirst(order.getStatus()));
        updateStatusColor(order.getStatus());
        
        // Total Amount
        tvTotalAmount.setText(currencyFormat.format(order.getTotalAmount()));
        
        // Payment Method
        String paymentMethod = order.getPaymentMethod();
        if (paymentMethod != null) {
            String paymentText = paymentMethod.equalsIgnoreCase("cod") ? "Cash on Delivery" :
                                paymentMethod.equalsIgnoreCase("gcash") ? "GCash" :
                                paymentMethod.equalsIgnoreCase("maya") ? "Maya" : paymentMethod;
            tvPaymentMethod.setText(paymentText);
        } else {
            tvPaymentMethod.setText("N/A");
        }
        
        // Delivery Address
        String address = order.getDeliveryAddress();
        tvDeliveryAddress.setText(TextUtils.isEmpty(address) ? "Not set" : address);
        
        // Order Date
        String createdAt = order.getCreatedAt();
        if (createdAt != null && !createdAt.isEmpty()) {
            try {
                // Parse ISO 8601 format
                String dateStr = createdAt.replace("T", " ").replace("Z", "");
                if (dateStr.contains("+")) {
                    dateStr = dateStr.substring(0, dateStr.indexOf("+"));
                }
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = inputFormat.parse(dateStr);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                tvOrderDate.setText(outputFormat.format(date));
            } catch (Exception e) {
                tvOrderDate.setText(createdAt);
            }
        } else {
            tvOrderDate.setText("N/A");
        }
        
        // Order Items
        android.util.Log.d("OrderDetailActivity", "displayOrderDetails called. Order items: " + (order.getItems() != null ? order.getItems().size() : "null"));
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            android.util.Log.d("OrderDetailActivity", "Displaying " + order.getItems().size() + " order items");
            for (int i = 0; i < order.getItems().size(); i++) {
                CartItem item = order.getItems().get(i);
                android.util.Log.d("OrderDetailActivity", "Item " + i + ": menuItemId=" + item.getMenuItemId() + ", quantity=" + item.getQuantity() + ", name=" + (item.getFoodItem() != null ? item.getFoodItem().getName() : "null"));
            }
            itemsAdapter.updateList(order.getItems());
            android.util.Log.d("OrderDetailActivity", "Adapter updated with " + itemsAdapter.getItemCount() + " items");
            // Show RecyclerView, hide empty state
            rvOrderItems.setVisibility(View.VISIBLE);
            if (layoutEmptyItems != null) {
                layoutEmptyItems.setVisibility(View.GONE);
            }
            // Force RecyclerView to measure and display all items
            rvOrderItems.post(() -> {
                rvOrderItems.requestLayout();
                rvOrderItems.invalidate();
                android.util.Log.d("OrderDetailActivity", "RecyclerView layout requested. Item count: " + itemsAdapter.getItemCount());
            });
        } else {
            android.util.Log.d("OrderDetailActivity", "No order items to display - items is " + (order.getItems() == null ? "null" : "empty"));
            itemsAdapter.updateList(new ArrayList<>());
            // Hide RecyclerView, show empty state
            rvOrderItems.setVisibility(View.GONE);
            if (layoutEmptyItems != null) {
                layoutEmptyItems.setVisibility(View.VISIBLE);
            }
        }
        
        // Show/Hide action buttons based on order status
        updateActionButtons(order);
    }
    
    private void updateStatusColor(String status) {
        int colorRes;
        if (status == null) {
            colorRes = R.color.text_secondary;
        } else {
            switch (status.toLowerCase()) {
                case "pending":
                    colorRes = R.color.warning;
                    break;
                case "confirmed":
                case "preparing":
                case "ready":
                    colorRes = R.color.info;
                    break;
                case "delivering":
                    colorRes = R.color.green_primary;
                    break;
                case "completed":
                    colorRes = R.color.green_primary;
                    break;
                case "cancelled":
                    colorRes = R.color.error;
                    break;
                default:
                    colorRes = R.color.text_secondary;
            }
        }
        tvStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }
    
    private void updateActionButtons(Order order) {
        String status = order.getStatus();
        if (status == null) {
            status = "";
        }
        
        // Cancel button - only show if order can be cancelled
        boolean canCancel = "pending".equalsIgnoreCase(status) || 
                          "confirmed".equalsIgnoreCase(status) ||
                          "preparing".equalsIgnoreCase(status);
        btnCancelOrder.setVisibility(canCancel ? View.VISIBLE : View.GONE);
        
        // Order Received button - only show if order is delivering
        boolean canMarkReceived = "delivering".equalsIgnoreCase(status);
        btnOrderReceived.setVisibility(canMarkReceived ? View.VISIBLE : View.GONE);
        
        // Download receipt button - only show if order is completed and has receipt
        boolean canDownload = "completed".equalsIgnoreCase(status) && 
                            !TextUtils.isEmpty(order.getReceiptUrl());
        btnDownloadReceipt.setVisibility(canDownload ? View.VISIBLE : View.GONE);
    }
    
    private void showCancelOrderDialog() {
        if (currentOrder == null) {
            return;
        }
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cancel_order, null);
        android.widget.RadioGroup rgCancelReasons = dialogView.findViewById(R.id.rgCancelReasons);
        com.google.android.material.textfield.TextInputLayout tilOtherReason = dialogView.findViewById(R.id.tilOtherReason);
        android.widget.EditText etReason = dialogView.findViewById(R.id.etCancelReason);
        
        // Show/hide "Other" text field based on selection
        rgCancelReasons.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbOther) {
                tilOtherReason.setVisibility(View.VISIBLE);
            } else {
                tilOtherReason.setVisibility(View.GONE);
                etReason.setText(""); // Clear text when switching away from "Other"
            }
        });
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.cancel_order, null)
            .setNegativeButton(R.string.keep_order, null)
            .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int selectedId = rgCancelReasons.getCheckedRadioButtonId();
                String reason = "";
                
                if (selectedId == -1) {
                    Toast.makeText(this, getString(R.string.cancel_order_select_reason), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (selectedId == R.id.rbChangedMind) {
                    reason = getString(R.string.cancel_reason_changed_mind);
                } else if (selectedId == R.id.rbBetterPrice) {
                    reason = getString(R.string.cancel_reason_better_price);
                } else if (selectedId == R.id.rbOrderedMistake) {
                    reason = getString(R.string.cancel_reason_ordered_mistake);
                } else if (selectedId == R.id.rbOutOfStock) {
                    reason = getString(R.string.cancel_reason_out_of_stock);
                } else if (selectedId == R.id.rbDeliverySlow) {
                    reason = getString(R.string.cancel_reason_delivery_slow);
                } else if (selectedId == R.id.rbOther) {
                    reason = etReason.getText().toString().trim();
                    if (TextUtils.isEmpty(reason)) {
                        Toast.makeText(this, getString(R.string.cancel_order_provide_reason), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                dialog.dismiss();
                cancelOrder(reason);
            });
        });
        
        dialog.show();
    }
    
    private void cancelOrder(String reason) {
        if (currentOrder == null) {
            return;
        }
        
        showLoading(true);
        
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (TextUtils.isEmpty(accessToken)) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use orderIdString if available (preferred), otherwise fall back to numeric ID
        String orderIdToCancel = orderIdString;
        if (TextUtils.isEmpty(orderIdToCancel) && currentOrder.getIdString() != null) {
            orderIdToCancel = currentOrder.getIdString();
        }
        
        if (TextUtils.isEmpty(orderIdToCancel)) {
            // Fallback to integer ID method (legacy support)
        orderService.cancelOrder(orderId, userId, reason, accessToken, notificationService, new OrderService.CancelOrderCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(OrderDetailActivity.this, "Order cancelled successfully", Toast.LENGTH_SHORT).show();
                    // Refresh order details
                    loadOrderDetails(true);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(OrderDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
        } else {
            // Use UUID string method (preferred)
            orderService.cancelOrder(orderIdToCancel, userId, reason, accessToken, notificationService, new OrderService.CancelOrderCallback() {
                @Override
                public void onSuccess() {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, "Order cancelled successfully", Toast.LENGTH_SHORT).show();
                        // Refresh order details
                        loadOrderDetails(true);
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    private void markOrderAsReceived() {
        if (currentOrder == null) {
            return;
        }
        
        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("Confirm Order Received")
            .setMessage("Have you received your order? This will mark the order as completed.")
            .setPositiveButton("Yes, I Received It", (dialog, which) -> {
                confirmOrderReceived();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void confirmOrderReceived() {
        if (currentOrder == null) {
            return;
        }
        
        showLoading(true);
        
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (TextUtils.isEmpty(accessToken)) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use orderIdString if available, otherwise use numeric ID
        String orderIdToUpdate = orderIdString;
        if (TextUtils.isEmpty(orderIdToUpdate) && currentOrder.getIdString() != null) {
            orderIdToUpdate = currentOrder.getIdString();
        }
        
        if (TextUtils.isEmpty(orderIdToUpdate)) {
            showLoading(false);
            Toast.makeText(this, "Invalid order ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update order status to "completed"
        orderService.updateOrderStatus(orderIdToUpdate, "completed", accessToken, 
            new OrderService.UpdateStatusCallback() {
                @Override
                public void onSuccess() {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, 
                            "✓ Order marked as received! Thank you for your order.", 
                            Toast.LENGTH_LONG).show();
                        // Refresh order details to show updated status
                        loadOrderDetails(false);
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, 
                            "Failed to update order: " + error, 
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void downloadReceipt() {
        if (currentOrder == null || TextUtils.isEmpty(currentOrder.getReceiptUrl())) {
            Toast.makeText(this, "Receipt not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check for storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                PERMISSION_REQUEST_WRITE_STORAGE);
            return;
        }
        
        downloadReceiptFile(currentOrder.getReceiptUrl());
    }
    
    private void downloadReceiptFile(String receiptUrl) {
        showLoading(true);
        
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url(receiptUrl)
                    .build();
                
                Response response = client.newCall(request).execute();
                
                if (response.isSuccessful() && response.body() != null) {
                    // Get file name from URL or use default
                    String fileName = "receipt_order_" + orderId + ".pdf";
                    if (receiptUrl.contains("/")) {
                        String urlFileName = receiptUrl.substring(receiptUrl.lastIndexOf("/") + 1);
                        if (!urlFileName.isEmpty() && urlFileName.contains(".")) {
                            fileName = urlFileName;
                        }
                    }
                    
                    // Save to Downloads folder
                    File downloadsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (downloadsDir == null) {
                        downloadsDir = new File(getFilesDir(), "Downloads");
                        downloadsDir.mkdirs();
                    }
                    
                    File file = new File(downloadsDir, fileName);
                    
                    try (InputStream inputStream = response.body().byteStream();
                         FileOutputStream outputStream = new FileOutputStream(file)) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, 
                            "Receipt downloaded to: " + file.getAbsolutePath(), 
                            Toast.LENGTH_LONG).show();
                    });
                } else {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(OrderDetailActivity.this, 
                            "Failed to download receipt", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("OrderDetailActivity", "Download receipt error", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(OrderDetailActivity.this, 
                        "Error downloading receipt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void subscribeToRealtimeUpdates() {
        if (orderId == -1 && TextUtils.isEmpty(orderIdString)) {
            return;
        }
        
        if (orderRealtimeClient == null) {
            orderRealtimeClient = new SupabaseRealtimeClient();
            orderRealtimeClient.subscribeToTable("public", "orders", new RealtimeListener() {
                @Override
                public void onOpen() { }
                
                @Override
                public void onChange(JsonObject payload) {
                    JsonObject record = RealtimePayloadUtil.getRelevantRecord(payload);
                    if (isTargetOrderRecord(record)) {
                        mainHandler.post(() -> loadOrderDetails(false));
                    }
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("OrderDetailActivity", "Realtime orders error: " + error);
                }
            });
        }
        
        if (orderItemsRealtimeClient == null) {
            orderItemsRealtimeClient = new SupabaseRealtimeClient();
            orderItemsRealtimeClient.subscribeToTable("public", "order_items", new RealtimeListener() {
                @Override
                public void onOpen() {
                    android.util.Log.d("OrderDetailActivity", "Realtime order_items connection opened");
                }
                
                @Override
                public void onChange(JsonObject payload) {
                    android.util.Log.d("OrderDetailActivity", "Realtime order_items change detected: " + payload);
                    JsonObject record = RealtimePayloadUtil.getRelevantRecord(payload);
                    if (isTargetOrderItemRecord(record)) {
                        android.util.Log.d("OrderDetailActivity", "Order item change matches current order. Reloading order details...");
                        mainHandler.post(() -> {
                            android.util.Log.d("OrderDetailActivity", "Reloading order details due to realtime update");
                            loadOrderDetails(false);
                        });
                    } else {
                        android.util.Log.d("OrderDetailActivity", "Order item change does not match current order. Ignoring.");
                    }
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("OrderDetailActivity", "Realtime order_items error: " + error);
                }
            });
        }
    }
    
    private boolean isTargetOrderRecord(JsonObject record) {
        if (record == null) {
            return false;
        }
        if (!TextUtils.isEmpty(orderIdString) && record.has("id")) {
            return orderIdString.equals(record.get("id").getAsString());
        }
        return false;
    }
    
    private boolean isTargetOrderItemRecord(JsonObject record) {
        if (record == null) {
            return false;
        }
        if (!TextUtils.isEmpty(orderIdString) && record.has("order_id")) {
            return orderIdString.equals(record.get("order_id").getAsString());
        }
        return false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orderRealtimeClient != null) {
            orderRealtimeClient.disconnect();
        }
        if (orderItemsRealtimeClient != null) {
            orderItemsRealtimeClient.disconnect();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentOrder != null && !TextUtils.isEmpty(currentOrder.getReceiptUrl())) {
                    downloadReceiptFile(currentOrder.getReceiptUrl());
                }
            } else {
                Toast.makeText(this, "Storage permission is required to download receipt", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnCancelOrder.setEnabled(!show);
        btnOrderReceived.setEnabled(!show);
        btnDownloadReceipt.setEnabled(!show);
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

