package com.fp.foodorderingsystem.services;

import android.util.Log;
import com.fp.foodorderingsystem.models.Order;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RealtimeDashboardManager - Real-time dashboard analytics
 *
 * Provides real-time metrics:
 * - Total orders and revenue
 * - Order status tracking
 * - Revenue trends
 * - Peak hours analysis
 * - User activity
 */
public class RealtimeDashboardManager {
    private static final String TAG = "RealtimeDashboardManager";

    public interface DashboardMetricsListener {
        void onMetricsUpdated(DashboardMetrics metrics);
        void onConnectionStateChanged(boolean connected);
        void onError(String error);
    }

    public static class DashboardMetrics {
        public int totalOrders;
        public double totalRevenue;
        public int pendingOrders;
        public int completedOrders;
        public int cancelledOrders;
        public double averageOrderValue;
        public int newUsersToday;
        public double completionRate;
        public long lastSyncTime;

        @Override
        public String toString() {
            return "DashboardMetrics{" +
                    "totalOrders=" + totalOrders +
                    ", totalRevenue=" + totalRevenue +
                    ", pendingOrders=" + pendingOrders +
                    ", completedOrders=" + completedOrders +
                    ", cancelledOrders=" + cancelledOrders +
                    ", averageOrderValue=" + averageOrderValue +
                    ", newUsersToday=" + newUsersToday +
                    ", completionRate=" + completionRate +
                    '}';
        }
    }

    private final OrderService orderService;
    private final SupabaseRealtimeClient realtimeClient;
    private final Gson gson;
    private final CopyOnWriteArrayList<DashboardMetricsListener> listeners;
    private final AtomicBoolean isConnected;
    private final List<Order> cachedOrders;
    private DashboardMetrics cachedMetrics;

    public RealtimeDashboardManager(OrderService orderService) {
        this.orderService = orderService;
        this.realtimeClient = new SupabaseRealtimeClient();
        this.gson = new Gson();
        this.listeners = new CopyOnWriteArrayList<>();
        this.isConnected = new AtomicBoolean(false);
        this.cachedOrders = new ArrayList<>();
        this.cachedMetrics = new DashboardMetrics();
    }

    /**
     * Start real-time dashboard synchronization
     */
    public void start(String accessToken) {
        // Load initial metrics
        orderService.getAllOrders(new OrderService.OrderCallback() {
            @Override
            public void onSuccess(List<Order> orders) {
                synchronized (cachedOrders) {
                    cachedOrders.clear();
                    if (orders != null) {
                        cachedOrders.addAll(orders);
                    }
                }
                calculateMetrics();
                notifyListeners();
            }

            @Override
            public void onError(String error) {
                notifyError("Failed to load initial metrics: " + error);
            }
        });

        // Subscribe to realtime events
        realtimeClient.subscribeToTable("public", "orders", new SupabaseRealtimeClient.RealtimeListener() {
            @Override
            public void onOpen() {
                isConnected.set(true);
                notifyConnectionStateChanged(true);
                Log.d(TAG, "Connected to realtime updates");
            }

            @Override
            public void onChange(JsonObject payload) {
                handleRealtimeChange(payload);
            }

            @Override
            public void onError(String error) {
                isConnected.set(false);
                notifyConnectionStateChanged(false);
                notifyError("Realtime connection error: " + error);
                Log.e(TAG, "Realtime error: " + error);
            }
        });
    }

    /**
     * Stop real-time synchronization
     */
    public void stop() {
        realtimeClient.disconnect();
        isConnected.set(false);
        notifyConnectionStateChanged(false);
    }

    /**
     * Handle real-time changes from Supabase
     */
    private void handleRealtimeChange(JsonObject payload) {
        String eventType = getEventType(payload);
        JsonObject newRecord = getNewRecord(payload);
        JsonObject oldRecord = getOldRecord(payload);

        synchronized (cachedOrders) {
            switch (eventType.toUpperCase()) {
                case "INSERT":
                    if (newRecord != null) {
                        try {
                            Order order = gson.fromJson(newRecord, Order.class);
                            if (order != null) {
                                String orderId = order.getIdString();
                                if (orderId != null && !orderExists(orderId)) {
                                    cachedOrders.add(0, order);
                                    Log.d(TAG, "Order added: " + orderId);
                                    calculateMetrics();
                                    notifyListeners();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing INSERT", e);
                        }
                    }
                    break;

                case "UPDATE":
                    if (newRecord != null) {
                        try {
                            Order order = gson.fromJson(newRecord, Order.class);
                            if (order != null) {
                                updateOrder(order);
                                Log.d(TAG, "Order updated: " + order.getIdString());
                                calculateMetrics();
                                notifyListeners();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing UPDATE", e);
                        }
                    }
                    break;

                case "DELETE":
                    if (oldRecord != null && oldRecord.has("id")) {
                        try {
                            String orderId = oldRecord.get("id").getAsString();
                            if (orderId != null && !orderId.isEmpty()) {
                                removeOrder(orderId);
                                Log.d(TAG, "Order deleted: " + orderId);
                                calculateMetrics();
                                notifyListeners();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing DELETE", e);
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Calculate dashboard metrics from cached orders
     */
    private void calculateMetrics() {
        synchronized (cachedOrders) {
            DashboardMetrics metrics = new DashboardMetrics();
            metrics.totalOrders = cachedOrders.size();
            metrics.lastSyncTime = System.currentTimeMillis();

            double totalRevenue = 0;
            int pending = 0;
            int completed = 0;
            int cancelled = 0;

            for (Order order : cachedOrders) {
                if (order.getTotalAmount() > 0) {
                    totalRevenue += order.getTotalAmount();
                }

                String status = order.getStatus();
                if (status != null) {
                    switch (status.toLowerCase()) {
                        case "pending":
                        case "preparing":
                            pending++;
                            break;
                        case "completed":
                        case "delivered":
                            completed++;
                            break;
                        case "cancelled":
                            cancelled++;
                            break;
                    }
                }
            }

            metrics.totalRevenue = totalRevenue;
            metrics.pendingOrders = pending;
            metrics.completedOrders = completed;
            metrics.cancelledOrders = cancelled;

            if (metrics.totalOrders > 0) {
                metrics.averageOrderValue = totalRevenue / metrics.totalOrders;
                metrics.completionRate = (completed * 100.0) / metrics.totalOrders;
            }

            this.cachedMetrics = metrics;
        }
    }

    /**
     * Update order in cache
     */
    private void updateOrder(Order newOrder) {
        String newOrderId = newOrder.getIdString();
        if (newOrderId == null) return;

        for (int i = 0; i < cachedOrders.size(); i++) {
            String cachedOrderId = cachedOrders.get(i).getIdString();
            if (newOrderId.equals(cachedOrderId)) {
                cachedOrders.set(i, newOrder);
                return;
            }
        }
    }

    /**
     * Remove order from cache
     */
    private void removeOrder(String orderId) {
        if (orderId == null) return;

        for (int i = 0; i < cachedOrders.size(); i++) {
            String cachedOrderId = cachedOrders.get(i).getIdString();
            if (orderId.equals(cachedOrderId)) {
                cachedOrders.remove(i);
                return;
            }
        }
    }

    /**
     * Check if order exists in cache
     */
    private boolean orderExists(String orderId) {
        if (orderId == null) return false;

        for (Order order : cachedOrders) {
            if (orderId.equals(order.getIdString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get event type from payload
     */
    private String getEventType(JsonObject payload) {
        if (payload != null && payload.has("type")) {
            return payload.get("type").getAsString();
        }
        return "";
    }

    /**
     * Get new record from payload
     */
    private JsonObject getNewRecord(JsonObject payload) {
        if (payload != null && payload.has("new")) {
            return payload.getAsJsonObject("new");
        }
        return null;
    }

    /**
     * Get old record from payload
     */
    private JsonObject getOldRecord(JsonObject payload) {
        if (payload != null && payload.has("old")) {
            return payload.getAsJsonObject("old");
        }
        return null;
    }

    /**
     * Add listener for metric updates
     */
    public void addListener(DashboardMetricsListener listener) {
        if (listener != null) {
            listeners.add(listener);
            listener.onMetricsUpdated(cachedMetrics);
        }
    }

    /**
     * Remove listener
     */
    public void removeListener(DashboardMetricsListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Notify all listeners of metrics update
     */
    private void notifyListeners() {
        for (DashboardMetricsListener listener : listeners) {
            try {
                listener.onMetricsUpdated(cachedMetrics);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    /**
     * Notify listeners of connection state change
     */
    private void notifyConnectionStateChanged(boolean connected) {
        for (DashboardMetricsListener listener : listeners) {
            try {
                listener.onConnectionStateChanged(connected);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying connection change", e);
            }
        }
    }

    /**
     * Notify listeners of error
     */
    private void notifyError(String error) {
        for (DashboardMetricsListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying error", e);
            }
        }
    }

    /**
     * Get cached metrics
     */
    public DashboardMetrics getMetrics() {
        return cachedMetrics;
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected.get();
    }
}
