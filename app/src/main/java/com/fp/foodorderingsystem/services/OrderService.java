package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.NotificationHelper;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.services.NotificationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OrderService {
    private static final String TAG = "OrderService";
    private SupabaseService supabaseService;
    private Context context;
    private PreferenceUtil preferenceUtil;
    private Gson gson;
    private AuthService authService;
    
    public OrderService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.gson = new Gson();
        this.preferenceUtil = new PreferenceUtil(this.context);
        this.authService = new AuthService(this.context);
    }
    
    public interface OrderCallback {
        void onSuccess(List<Order> orders);
        void onError(String error);
    }
    
    public interface SingleOrderCallback {
        void onSuccess(Order order);
        void onError(String error);
    }
    
    public void getOrderById(int orderId, String customerId, SingleOrderCallback callback) {
        // Since Supabase uses UUID strings, we need to query by customer_id and filter by numeric ID
        // Query all orders for the customer and find the one matching the numeric ID
        getOrders(customerId, new OrderCallback() {
            @Override
            public void onSuccess(List<Order> orders) {
                // Find order with matching numeric ID
                for (Order order : orders) {
                    if (order.getId() == orderId) {
                        // Load order items for this order
                        if (order.getIdString() != null && !order.getIdString().isEmpty()) {
                            String accessTokenForItems = preferenceUtil != null ? preferenceUtil.getAccessToken() : null;
                            loadOrderItems(order.getIdString(), order, accessTokenForItems, callback);
                        } else {
                            callback.onSuccess(order);
                        }
                        return;
                    }
                }
                callback.onError("Order not found");
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
    
    /**
     * Get order by UUID string (preferred method for Supabase)
     */
    public void getOrderByIdString(String orderIdString, String customerId, SingleOrderCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                // Strip any existing quotes from UUIDs
                String cleanOrderId = orderIdString.replace("\"", "").trim();
                String cleanCustomerId = customerId.replace("\"", "").trim();
                
                // For UUIDs in PostgREST, first try without quotes (standard format)
                String url = "orders?id=eq." + cleanOrderId + "&customer_id=eq." + cleanCustomerId;
                Log.d(TAG, "Fetching order with URL: " + url);
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                Log.d(TAG, "Order fetch response code: " + response.code());
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Order response body: " + responseBody);
                    // Parse as JsonArray to handle UUID properly
                    com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                    if (jsonArray != null && jsonArray.size() > 0) {
                        Order order = gson.fromJson(jsonArray.get(0).getAsJsonObject(), Order.class);
                        if (order != null) {
                            order.getId(); // Trigger UUID to int conversion
                            
                            // Use the order's actual ID string (might be different from what was passed)
                            String actualOrderIdString = order.getIdString();
                            Log.d(TAG, "Order loaded. Requested ID: " + cleanOrderId + ", Actual order ID: " + actualOrderIdString);
                            
                            // Get access token for loading order items
                            String accessToken = preferenceUtil != null ? preferenceUtil.getAccessToken() : null;
                            // Load order items using the actual order ID
                            loadOrderItems(actualOrderIdString != null ? actualOrderIdString : cleanOrderId, order, accessToken, callback);
                        } else {
                            callback.onError("Failed to parse order");
                        }
                    } else {
                        callback.onError("Order not found");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Failed to load order: " + response.code() + " - " + errorBody);
                    // If 400 error, try with quotes around UUIDs
                    if (response.code() == 400) {
                        try {
                            // Retry with quotes around UUIDs
                            url = "orders?id=eq.\"" + cleanOrderId + "\"&customer_id=eq.\"" + cleanCustomerId + "\"";
                            Log.d(TAG, "Retrying with quoted UUIDs: " + url);
                            request = supabaseService.createRequest(url).get().build();
                            response = supabaseService.executeRequest(request);
                            if (response.isSuccessful()) {
                                String responseBody = response.body().string();
                                com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                                if (jsonArray != null && jsonArray.size() > 0) {
                                    Order order = gson.fromJson(jsonArray.get(0).getAsJsonObject(), Order.class);
                                    if (order != null) {
                                        order.getId();
                                        String actualOrderIdString = order.getIdString();
                                        Log.d(TAG, "Order loaded (with quotes). Actual order ID: " + actualOrderIdString);
                                        String accessToken = preferenceUtil != null ? preferenceUtil.getAccessToken() : null;
                                        loadOrderItems(actualOrderIdString != null ? actualOrderIdString : cleanOrderId, order, accessToken, callback);
                                        return;
                                    }
                                }
                            } else {
                                Log.e(TAG, "Retry with quotes also failed: " + response.code());
                            }
                        } catch (Exception e2) {
                            Log.e(TAG, "Error retrying with quoted UUIDs", e2);
                        }
                    }
                    callback.onError("Failed to load order: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "Get order by ID string error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Load order items from order_items table
     */
    private void loadOrderItems(String orderIdString, Order order, SingleOrderCallback callback) {
        loadOrderItems(orderIdString, order, null, callback);
    }
    
    /**
     * Load order items from order_items table (with optional access token)
     */
    private void loadOrderItems(String orderIdString, Order order, String accessToken, SingleOrderCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Loading order items for order ID: " + orderIdString);
                Log.d(TAG, "Order object ID string: " + (order != null ? order.getIdString() : "null"));
                
                // Use the order's actual ID string if available, otherwise use the passed one
                String actualOrderId = (order != null && order.getIdString() != null) ? order.getIdString() : orderIdString;
                Log.d(TAG, "Using order ID for query: " + actualOrderId);
                
                if (actualOrderId == null || actualOrderId.isEmpty()) {
                    Log.w(TAG, "Order ID is null or empty, cannot load order items. Setting empty list.");
                    order.setItems(new ArrayList<>());
                    callback.onSuccess(order);
                    return;
                }
                
                // Strip any existing quotes from the UUID
                actualOrderId = actualOrderId.replace("\"", "").trim();
                
                // Try to fetch from order_items table
                // First try without quotes (standard PostgREST format for UUIDs)
                String orderItemsUrl = "order_items?order_id=eq." + actualOrderId + 
                    "&select=id,order_id,menu_item_id,quantity,unit_price,total_price&order=created_at.asc";
                Log.d(TAG, "Order items URL (no quotes): " + orderItemsUrl);
                
                // Use authenticated request if token is provided, otherwise use createRequestWithAuth
                Request orderItemsRequest;
                if (accessToken != null && !accessToken.isEmpty()) {
                    orderItemsRequest = supabaseService.createAuthenticatedRequest(orderItemsUrl, accessToken)
                    .get()
                    .build();
                } else {
                    orderItemsRequest = createRequestWithAuth(orderItemsUrl)
                        .get()
                        .build();
                }
                
                Response orderItemsResponse = supabaseService.executeRequest(orderItemsRequest);
                Log.d(TAG, "Order items response code: " + orderItemsResponse.code());
                
                // If 400 error, retry with quotes around UUID
                if (orderItemsResponse.code() == 400) {
                    String errorBody = orderItemsResponse.body() != null ? orderItemsResponse.body().string() : "";
                    Log.w(TAG, "Got 400 error, retrying with quoted UUID. Error: " + errorBody);
                    
                    // Retry with quotes around UUID
                    orderItemsUrl = "order_items?order_id=eq.\"" + actualOrderId + "\"" + 
                        "&select=id,order_id,menu_item_id,quantity,unit_price,total_price&order=created_at.asc";
                    Log.d(TAG, "Order items URL (with quotes): " + orderItemsUrl);
                    
                    if (accessToken != null && !accessToken.isEmpty()) {
                        orderItemsRequest = supabaseService.createAuthenticatedRequest(orderItemsUrl, accessToken)
                        .get()
                        .build();
                    } else {
                        orderItemsRequest = createRequestWithAuth(orderItemsUrl)
                            .get()
                            .build();
                    }
                    
                    orderItemsResponse = supabaseService.executeRequest(orderItemsRequest);
                    Log.d(TAG, "Order items response code (retry): " + orderItemsResponse.code());
                }
                
                if (orderItemsResponse.isSuccessful()) {
                    ResponseBody responseBody = orderItemsResponse.body();
                    if (responseBody == null) {
                        Log.w(TAG, "Order items response body is null");
                        order.setItems(new ArrayList<>());
                        callback.onSuccess(order);
                        return;
                    }
                    
                    String itemsBody = responseBody.string();
                    Log.d(TAG, "Order items response body: " + itemsBody);
                    Log.d(TAG, "Response body length: " + (itemsBody != null ? itemsBody.length() : 0));
                    Log.d(TAG, "Response headers: " + orderItemsResponse.headers());
                    
                    // Check if response is empty array
                    if (itemsBody == null || itemsBody.trim().isEmpty() || itemsBody.trim().equals("[]")) {
                        Log.w(TAG, "Order items response is empty array for order: " + actualOrderId);
                        // Return order with empty list (not null)
                        order.setItems(new ArrayList<>());
                        callback.onSuccess(order);
                        return;
                    }
                    
                    com.google.gson.JsonArray itemsArray = gson.fromJson(itemsBody, com.google.gson.JsonArray.class);
                    Log.d(TAG, "Parsed items array size: " + (itemsArray != null ? itemsArray.size() : "null"));
                    
                    if (itemsArray != null && itemsArray.size() > 0) {
                        // Parse order items as CartItems
                        List<com.fp.foodorderingsystem.models.CartItem> items = new ArrayList<>();
                        List<Integer> menuItemIds = new ArrayList<>();
                        
                        for (int i = 0; i < itemsArray.size(); i++) {
                            try {
                                com.google.gson.JsonObject itemJson = itemsArray.get(i).getAsJsonObject();
                                com.fp.foodorderingsystem.models.CartItem item = new com.fp.foodorderingsystem.models.CartItem();
                                
                                // Map order_items fields to CartItem
                                if (itemJson.has("id")) {
                                    item.setId(itemJson.get("id").getAsString());
                                }
                                if (itemJson.has("menu_item_id")) {
                                    int menuItemId = itemJson.get("menu_item_id").getAsInt();
                                    item.setMenuItemId(menuItemId);
                                    menuItemIds.add(menuItemId);
                                    Log.d(TAG, "Found order item with menu_item_id: " + menuItemId);
                                }
                                if (itemJson.has("quantity")) {
                                    item.setQuantity(itemJson.get("quantity").getAsInt());
                                }
                                if (itemJson.has("unit_price")) {
                                    item.setUnitPrice(itemJson.get("unit_price").getAsDouble());
                                }
                                if (itemJson.has("total_price")) {
                                    item.setTotalPrice(itemJson.get("total_price").getAsDouble());
                                }
                                
                                items.add(item);
                                Log.d(TAG, "Added order item: menu_item_id=" + item.getMenuItemId() + ", quantity=" + item.getQuantity());
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing order item at index " + i, e);
                            }
                        }
                        
                        if (!items.isEmpty()) {
                            Log.d(TAG, "Successfully loaded " + items.size() + " order items for order: " + orderIdString);
                            Log.d(TAG, "Items before menu details: " + items.size());
                            
                            // Load menu item details for better display
                            loadMenuItemDetails(items, menuItemIds, () -> {
                                Log.d(TAG, "Menu details loaded, setting " + items.size() + " items on order");
                                order.setItems(items);
                                Log.d(TAG, "Order items set. Order.getItems() size: " + (order.getItems() != null ? order.getItems().size() : "null"));
                                callback.onSuccess(order);
                            }, () -> {
                                // Even without menu details, return items
                                Log.d(TAG, "Returning items without menu details. Items count: " + items.size());
                                order.setItems(items);
                                Log.d(TAG, "Order items set (no menu details). Order.getItems() size: " + (order.getItems() != null ? order.getItems().size() : "null"));
                                callback.onSuccess(order);
                            });
                            return;
                        } else {
                            Log.w(TAG, "Order items array was empty after parsing. Setting empty list.");
                            order.setItems(new ArrayList<>());
                            callback.onSuccess(order);
                            return;
                        }
                    } else {
                        Log.w(TAG, "No order items found in response for order: " + orderIdString + ". Setting empty list.");
                        order.setItems(new ArrayList<>());
                        callback.onSuccess(order);
                        return;
                    }
                } else {
                    ResponseBody errorBody = orderItemsResponse.body();
                    String errorText = "";
                    if (errorBody != null) {
                        try {
                            errorText = errorBody.string();
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                    }
                    Log.e(TAG, "Failed to load order items. Code: " + orderItemsResponse.code() + ", Error: " + errorText);
                    Log.e(TAG, "Order ID used in query: " + actualOrderId);
                    
                    // Make final copy for use in inner class
                    final String finalOrderId = actualOrderId;
                    final Order finalOrder = order;
                    
                    // If 401 error (JWT expired), try to refresh token and retry
                    if (orderItemsResponse.code() == 401 && (errorText.contains("JWT expired") || errorText.contains("jwt expired") || errorText.contains("token expired"))) {
                        Log.w(TAG, "JWT expired, attempting to refresh token and retry...");
                        authService.refreshAccessToken(new AuthService.TokenCallback() {
                            @Override
                            public void onSuccess(String newAccessToken) {
                                Log.d(TAG, "Token refreshed successfully, retrying order items load...");
                                // Retry with new token
                                try {
                                    String retryUrl = "order_items?order_id=eq." + finalOrderId + 
                                        "&select=id,order_id,menu_item_id,quantity,unit_price,total_price&order=created_at.asc";
                                    
                                    Request retryRequest = supabaseService.createAuthenticatedRequest(retryUrl, newAccessToken)
                                        .get()
                                        .build();
                                    
                                    Response retryResponse = supabaseService.executeRequest(retryRequest);
                                    
                                    if (retryResponse.isSuccessful()) {
                                        ResponseBody retryBody = retryResponse.body();
                                        if (retryBody != null) {
                                            String itemsBody = retryBody.string();
                                            if (itemsBody != null && !itemsBody.trim().isEmpty() && !itemsBody.trim().equals("[]")) {
                                                com.google.gson.JsonArray itemsArray = gson.fromJson(itemsBody, com.google.gson.JsonArray.class);
                                                if (itemsArray != null && itemsArray.size() > 0) {
                                                    List<com.fp.foodorderingsystem.models.CartItem> items = new ArrayList<>();
                                                    List<Integer> menuItemIds = new ArrayList<>();
                                                    
                                                    for (int i = 0; i < itemsArray.size(); i++) {
                                                        try {
                                                            com.google.gson.JsonObject itemJson = itemsArray.get(i).getAsJsonObject();
                                                            com.fp.foodorderingsystem.models.CartItem item = new com.fp.foodorderingsystem.models.CartItem();
                                                            
                                                            if (itemJson.has("id")) {
                                                                item.setId(itemJson.get("id").getAsString());
                                                            }
                                                            if (itemJson.has("menu_item_id")) {
                                                                int menuItemId = itemJson.get("menu_item_id").getAsInt();
                                                                item.setMenuItemId(menuItemId);
                                                                menuItemIds.add(menuItemId);
                                                            }
                                                            if (itemJson.has("quantity")) {
                                                                item.setQuantity(itemJson.get("quantity").getAsInt());
                                                            }
                                                            if (itemJson.has("unit_price")) {
                                                                item.setUnitPrice(itemJson.get("unit_price").getAsDouble());
                                                            }
                                                            if (itemJson.has("total_price")) {
                                                                item.setTotalPrice(itemJson.get("total_price").getAsDouble());
                                                            }
                                                            
                                                            items.add(item);
                                                        } catch (Exception e) {
                                                            Log.e(TAG, "Error parsing order item at index " + i, e);
                                                        }
                                                    }
                                                    
                                                    if (!items.isEmpty()) {
                                                        loadMenuItemDetails(items, menuItemIds, () -> {
                                                            finalOrder.setItems(items);
                                                            callback.onSuccess(finalOrder);
                                                        }, () -> {
                                                            finalOrder.setItems(items);
                                                            callback.onSuccess(finalOrder);
                                                        });
                                                        return;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // If retry also failed, return empty list
                                    Log.w(TAG, "Retry after token refresh also failed. Code: " + retryResponse.code());
                                    finalOrder.setItems(new ArrayList<>());
                                    callback.onSuccess(finalOrder);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error retrying after token refresh", e);
                                    finalOrder.setItems(new ArrayList<>());
                                    callback.onSuccess(finalOrder);
                                }
                            }
                            
                            @Override
                            public void onError(String refreshError) {
                                Log.e(TAG, "Token refresh failed: " + refreshError);
                                // Return order with empty list
                                finalOrder.setItems(new ArrayList<>());
                                callback.onSuccess(finalOrder);
                            }
                        });
                        return;
                    }
                    
                    // For other errors, return order with empty list
                    order.setItems(new ArrayList<>());
                    callback.onSuccess(order);
                    return;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading order items", e);
                e.printStackTrace();
                // Return order with empty list even if items failed to load
                order.setItems(new ArrayList<>());
                callback.onSuccess(order);
            }
        }).start();
    }
    
    public void getOrders(String customerId, OrderCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                String url = "orders?customer_id=eq." + customerId + "&order=created_at.desc";
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    // Parse as JsonArray to handle UUID properly
                    com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                    List<Order> orderList = new ArrayList<>();
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.size(); i++) {
                            Order order = gson.fromJson(jsonArray.get(i).getAsJsonObject(), Order.class);
                            if (order != null) {
                                order.getId(); // Trigger UUID to int conversion
                                orderList.add(order);
                            }
                        }
                    }
                    callback.onSuccess(orderList);
                } else {
                    callback.onError("Failed to load orders");
                }
            } catch (Exception e) {
                Log.e(TAG, "Get orders error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void getAllOrders(OrderCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                String url = "orders?order=created_at.desc";
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    // Parse as JsonArray to handle UUID properly
                    com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                    List<Order> orderList = new ArrayList<>();
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.size(); i++) {
                            Order order = gson.fromJson(jsonArray.get(i).getAsJsonObject(), Order.class);
                            if (order != null) {
                                order.getId(); // Trigger UUID to int conversion
                                orderList.add(order);
                            }
                        }
                    }
                    callback.onSuccess(orderList);
                } else {
                    callback.onError("Failed to load orders");
                }
            } catch (Exception e) {
                Log.e(TAG, "Get all orders error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void createOrder(Order order, List<com.fp.foodorderingsystem.models.CartItem> cartItems, String accessToken, SingleOrderCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
        JsonObject body = new JsonObject();
        body.addProperty("customer_id", order.getCustomerId());
        
        // Ensure total_amount is a valid number (handle NaN or Infinity)
        double amount = order.getTotalAmount();
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            Log.e(TAG, "Invalid total_amount: " + amount);
            callback.onError("Invalid order amount");
            return;
        }
        body.addProperty("total_amount", amount);
        
        body.addProperty("status", order.getStatus() != null ? order.getStatus() : "pending");
        body.addProperty("payment_method", order.getPaymentMethod() != null ? order.getPaymentMethod() : "cod");
        body.addProperty("delivery_address", order.getDeliveryAddress() != null ? order.getDeliveryAddress() : "");
        // Note: delivery_lat and delivery_lng columns don't exist in the orders table
                
                Request request = supabaseService
                    .createAuthenticatedRequest("orders", accessToken)
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    try {
                        Log.d(TAG, "Order creation response: " + responseBody);
                        
                        // Parse response - Supabase returns array with created order
                        // Use JsonArray to parse and handle UUID properly
                        com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                        if (jsonArray != null && jsonArray.size() > 0) {
                            // Parse first order from array
                            com.google.gson.JsonObject orderJson = jsonArray.get(0).getAsJsonObject();
                            Order createdOrder = gson.fromJson(orderJson, Order.class);
                            
                            if (createdOrder != null) {
                                // Ensure ID is properly set (trigger conversion from UUID if needed)
                                createdOrder.getId(); // This will convert UUID to int if needed
                                
                                Log.d(TAG, "Order created successfully - UUID: " + createdOrder.getIdString() + 
                                    ", Numeric ID: " + createdOrder.getId() + 
                                    ", Amount: " + createdOrder.getTotalAmount());
                                
                                // Save order items to order_items table
                                if (cartItems != null && !cartItems.isEmpty() && createdOrder.getIdString() != null) {
                                    saveOrderItems(createdOrder.getIdString(), cartItems, accessToken, new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onSuccess(createdOrder);
                                        }
                                    }, new Runnable() {
                                        @Override
                                        public void run() {
                                            // Even if items fail to save, return the order
                                            Log.w(TAG, "Order created but items failed to save");
                                            callback.onSuccess(createdOrder);
                                        }
                                    });
                                } else {
                                    callback.onSuccess(createdOrder);
                                }
                            } else {
                                Log.e(TAG, "Failed to parse order from JSON");
                                callback.onError("Failed to parse order response");
                            }
                        } else {
                            Log.e(TAG, "Create order response empty: " + responseBody);
                            callback.onError("Failed to create order - empty response");
                        }
                    } catch (com.google.gson.JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse order response JSON: " + responseBody, e);
                        // Try to extract more details from the error
                        String errorDetail = e.getMessage();
                        if (e.getCause() instanceof NumberFormatException) {
                            errorDetail = "Invalid number format in response. " + e.getMessage();
                        }
                        callback.onError("Failed to parse order response: " + errorDetail);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Number format error parsing order response: " + responseBody, e);
                        callback.onError("Invalid data format in response: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error parsing order response: " + responseBody, e);
                        callback.onError("Error parsing response: " + e.getMessage());
                    }
                } else {
                    String errorMsg = "Failed to create order";
                    try {
                        if (!responseBody.isEmpty()) {
                            JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                            if (errorJson != null) {
                                if (errorJson.has("message")) {
                                    errorMsg = errorJson.get("message").getAsString();
                                } else if (errorJson.has("msg")) {
                                    errorMsg = errorJson.get("msg").getAsString();
                                }
                            }
                        }
                    } catch (Exception parseException) {
                        Log.e(TAG, "Failed to parse order error body: " + responseBody, parseException);
                    }
                    Log.e(TAG, "Create order failed: code=" + response.code() + ", body=" + responseBody);
                    callback.onError(errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Create order error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Save order items to order_items table
     */
    private void saveOrderItems(String orderId, List<com.fp.foodorderingsystem.models.CartItem> cartItems, 
                                String accessToken, Runnable onSuccess, Runnable onError) {
        new Thread(() -> {
            try {
                if (cartItems == null || cartItems.isEmpty()) {
                    Log.d(TAG, "No items to save");
                    onSuccess.run();
                    return;
                }
                
                // Create array of order items
                com.google.gson.JsonArray itemsArray = new com.google.gson.JsonArray();
                
                for (com.fp.foodorderingsystem.models.CartItem cartItem : cartItems) {
                    if (cartItem == null) continue;
                    
                    com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                    itemJson.addProperty("order_id", orderId);
                    itemJson.addProperty("menu_item_id", cartItem.getMenuItemId());
                    itemJson.addProperty("quantity", cartItem.getQuantity());
                    itemJson.addProperty("unit_price", cartItem.getUnitPrice());
                    itemJson.addProperty("total_price", cartItem.getTotalPrice());
                    
                    itemsArray.add(itemJson);
                }
                
                if (itemsArray.size() == 0) {
                    Log.d(TAG, "No valid items to save after filtering");
                    onSuccess.run();
                    return;
                }
                
                Log.d(TAG, "Saving " + itemsArray.size() + " order items for order: " + orderId);
                
                // Use Supabase's bulk insert endpoint - send array directly
                String jsonBody = itemsArray.toString();
                Log.d(TAG, "Order items JSON: " + jsonBody);
                
                Request request = supabaseService
                    .createAuthenticatedRequest("order_items", accessToken)
                    .header("Prefer", "return=representation")
                    .post(RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        jsonBody))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    Log.d(TAG, "Order items saved successfully: " + itemsArray.size() + " items. Response: " + responseBody);
                    onSuccess.run();
                } else {
                    Log.e(TAG, "Failed to save order items: " + response.code() + " - " + responseBody);
                    onError.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving order items", e);
                onError.run();
            }
        }).start();
    }
    
    /**
     * Load menu item details for order items
     */
    private void loadMenuItemDetails(List<com.fp.foodorderingsystem.models.CartItem> items, 
                                     List<Integer> menuItemIds, Runnable onSuccess, Runnable onError) {
        if (menuItemIds == null || menuItemIds.isEmpty()) {
            onError.run();
            return;
        }
        
        new Thread(() -> {
            try {
                // Build query to get all menu items at once
                StringBuilder idsQuery = new StringBuilder();
                for (int i = 0; i < menuItemIds.size(); i++) {
                    if (i > 0) idsQuery.append(",");
                    idsQuery.append(menuItemIds.get(i));
                }
                
                String menuItemsUrl = "menu_items?id=in.(" + idsQuery.toString() + ")";
                Request menuItemsRequest = createRequestWithAuth(menuItemsUrl)
                    .get()
                    .build();
                
                Response menuItemsResponse = supabaseService.executeRequest(menuItemsRequest);
                
                if (menuItemsResponse.isSuccessful()) {
                    String menuItemsBody = menuItemsResponse.body().string();
                    com.google.gson.JsonArray menuItemsArray = gson.fromJson(menuItemsBody, com.google.gson.JsonArray.class);
                    
                    if (menuItemsArray != null && menuItemsArray.size() > 0) {
                        // Create a map of menu item ID to FoodItem
                        java.util.Map<Integer, com.fp.foodorderingsystem.models.FoodItem> menuItemMap = new java.util.HashMap<>();
                        for (int i = 0; i < menuItemsArray.size(); i++) {
                            try {
                                com.fp.foodorderingsystem.models.FoodItem foodItem = gson.fromJson(
                                    menuItemsArray.get(i).getAsJsonObject(),
                                    com.fp.foodorderingsystem.models.FoodItem.class
                                );
                                if (foodItem != null) {
                                    menuItemMap.put(foodItem.getId(), foodItem);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing menu item", e);
                            }
                        }
                        
                        // Attach FoodItem to each CartItem
                        for (com.fp.foodorderingsystem.models.CartItem item : items) {
                            com.fp.foodorderingsystem.models.FoodItem foodItem = menuItemMap.get(item.getMenuItemId());
                            if (foodItem != null) {
                                item.setFoodItem(foodItem);
                            }
                        }
                        
                        Log.d(TAG, "Loaded menu item details for " + menuItemMap.size() + " items");
                        onSuccess.run();
                        return;
                    }
                }
                
                Log.w(TAG, "Failed to load menu item details");
                onError.run();
            } catch (Exception e) {
                Log.e(TAG, "Error loading menu item details", e);
                onError.run();
            }
        }).start();
    }
    
    public interface UpdateStatusCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Update order status using UUID string (for Supabase realtime database)
     */
    public void updateOrderStatus(String orderIdString, String status, String accessToken, UpdateStatusCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            if (callback != null) {
                callback.onError("No internet connection");
            }
            return;
        }
        
        if (accessToken == null || accessToken.isEmpty()) {
            if (callback != null) {
                callback.onError("Authentication required. Please login again.");
            }
            return;
        }
        
        if (orderIdString == null || orderIdString.isEmpty()) {
            if (callback != null) {
                callback.onError("Invalid order ID");
            }
            return;
        }
        
        // Validate and normalize status
        if (status == null || status.isEmpty()) {
            if (callback != null) {
                callback.onError("Status cannot be empty");
            }
            return;
        }
        
        // Normalize status to lowercase and trim
        final String normalizedStatus = status.toLowerCase().trim();
        
        // Validate status value
        String[] validStatuses = {"pending", "confirmed", "preparing", "ready", "delivering", "completed", "cancelled"};
        boolean isValidStatus = false;
        for (String validStatus : validStatuses) {
            if (validStatus.equals(normalizedStatus)) {
                isValidStatus = true;
                break;
            }
        }
        
        if (!isValidStatus) {
            if (callback != null) {
                callback.onError("Invalid status value. Must be one of: pending, confirmed, preparing, ready, delivering, completed, cancelled");
            }
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("status", normalizedStatus);
                
                // Use UUID string for the query
                String endpoint = "orders?id=eq." + orderIdString;
                Log.d(TAG, "Updating order status - Order ID (UUID): " + orderIdString + ", Status: " + normalizedStatus);
                Log.d(TAG, "Endpoint: " + endpoint);
                
                Request request = supabaseService
                    .createAuthenticatedRequest(endpoint, accessToken)
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                Log.d(TAG, "Update status response - Code: " + response.code() + ", Body: " + responseBody);
                
                if (response.isSuccessful()) {
                    Log.d(TAG, "Order status updated successfully");
                    
                    // Fetch the updated order to get customer ID and send notifications
                    fetchOrderAndSendNotifications(orderIdString, normalizedStatus);
                    
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    String errorMessage = "Failed to update order status";
                    try {
                        if (response.code() == 401) {
                            errorMessage = "Authentication failed. Please login again.";
                            Log.e(TAG, "Authentication failed - 401");
                        } else if (response.code() == 404) {
                            errorMessage = "Order not found";
                            Log.e(TAG, "Order not found - 404");
                        } else if (response.code() == 403) {
                            errorMessage = "Permission denied. You don't have access to update this order.";
                            Log.e(TAG, "Permission denied - 403");
                        } else if (response.code() == 400) {
                            errorMessage = "Invalid request. Please check the order status value.";
                            Log.e(TAG, "Bad request - 400: " + responseBody);
                        } else if (!responseBody.isEmpty()) {
                            try {
                                JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                                if (errorJson != null && errorJson.has("message")) {
                                    errorMessage = errorJson.get("message").getAsString();
                                } else if (errorJson != null && errorJson.has("error")) {
                                    errorMessage = errorJson.get("error").getAsString();
                                } else {
                                    errorMessage = "Update failed: " + response.code() + " - " + responseBody;
                                }
                            } catch (Exception parseEx) {
                                errorMessage = "Update failed: " + response.code() + " - " + responseBody;
                            }
                        } else {
                            errorMessage = "Update failed: HTTP " + response.code();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                        errorMessage = "Update failed: " + response.code();
                    }
                    
                    Log.e(TAG, "Order status update failed: " + errorMessage);
                    if (callback != null) {
                        callback.onError(errorMessage);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Update order status error", e);
                if (callback != null) {
                    callback.onError("Error updating status: " + e.getMessage());
                }
            }
        }).start();
    }
    
    public interface CancelOrderCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void cancelOrder(int orderId, String customerId, String reason, String accessToken, NotificationService notificationService, CancelOrderCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            if (callback != null) {
                callback.onError("No internet connection");
            }
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("status", "cancelled");
                body.addProperty("cancelled_by", "customer");
                body.addProperty("cancellation_reason", reason);
                
                Request request = supabaseService
                    .createAuthenticatedRequest("orders?id=eq." + orderId, accessToken)
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    // Increment cancellation count
                    incrementCancellationCount(customerId, accessToken);
                    
                    // Send notification to customer
                    notificationService.createNotification(
                        customerId,
                        "Order Cancelled",
                        "Your order has been cancelled. Reason: " + reason,
                        "order",
                        orderId
                    );
                    
                    // Fetch order details and notify all admins
                    notifyAdminsOfCancellation(orderId, customerId, reason, accessToken, notificationService);
                    
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    String errorMsg = "Failed to cancel order: " + response.code();
                    try {
                        if (!responseBody.isEmpty()) {
                            JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                            if (errorJson != null && errorJson.has("message")) {
                                errorMsg = errorJson.get("message").getAsString();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse error response", e);
                    }
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Cancel order error", e);
                if (callback != null) {
                    callback.onError("Error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Notify all admins when a customer cancels an order
     */
    private void notifyAdminsOfCancellation(int orderId, String customerId, String reason, String accessToken, NotificationService notificationService) {
        new Thread(() -> {
            try {
                // Fetch the order to get order details (UUID string)
                // Query by customer_id and find matching order by integer ID
                String url = "orders?customer_id=eq." + customerId + "&order=created_at.desc";
                Request orderRequest = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response orderResponse = supabaseService.executeRequest(orderRequest);
                
                String orderIdDisplay = String.valueOf(orderId);
                if (orderResponse.isSuccessful()) {
                    String responseBody = orderResponse.body() != null ? orderResponse.body().string() : "";
                    com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.size(); i++) {
                            Order order = gson.fromJson(jsonArray.get(i).getAsJsonObject(), Order.class);
                            if (order != null && order.getId() == orderId) {
                                orderIdDisplay = order.getIdString() != null ? order.getIdString() : String.valueOf(order.getId());
                                break;
                            }
                        }
                    }
                }
                
                // Make final copy for use in inner class
                final String finalOrderIdDisplay = orderIdDisplay;
                final String finalReason = reason;
                
                // Get all admin users
                UserService userService = new UserService(context);
                userService.getAdminUsers(accessToken, new UserService.UserListCallback() {
                    @Override
                    public void onSuccess(List<User> adminUsers) {
                        if (adminUsers != null && !adminUsers.isEmpty()) {
                            // Send notification to each admin
                            for (User admin : adminUsers) {
                                if (admin != null && admin.getId() != null) {
                                    notificationService.createNotification(
                                        admin.getId(),
                                        "Order Cancelled by Customer",
                                        "Order #" + finalOrderIdDisplay + " has been cancelled by customer. Reason: " + (finalReason != null ? finalReason : "No reason provided"),
                                        "order",
                                        orderId
                                    );
                                }
                            }
                            Log.d(TAG, "Notified " + adminUsers.size() + " admin(s) about order cancellation: " + finalOrderIdDisplay);
                        } else {
                            Log.d(TAG, "No admin users found to notify about order cancellation");
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to fetch admin users for cancellation notification: " + error);
                        // Don't fail the cancellation if we can't notify admins
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error notifying admins of cancellation", e);
                // Don't fail the cancellation if notification fails
            }
        }).start();
    }
    
    /**
     * Fetch order details and send notifications for status changes
     */
    private void fetchOrderAndSendNotifications(String orderIdString, String newStatus) {
        new Thread(() -> {
            try {
                // Strip any existing quotes from UUID
                String cleanOrderId = orderIdString.replace("\"", "").trim();
                
                // Fetch order by UUID string (for Supabase realtime database)
                // First try without quotes
                String url = "orders?id=eq." + cleanOrderId;
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                
                // If 400 error, retry with quotes
                if (response.code() == 400) {
                    url = "orders?id=eq.\"" + cleanOrderId + "\"";
                    request = supabaseService.createRequest(url).get().build();
                    response = supabaseService.executeRequest(request);
                }
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    com.google.gson.JsonArray jsonArray = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                    if (jsonArray != null && jsonArray.size() > 0) {
                        Order order = gson.fromJson(jsonArray.get(0).getAsJsonObject(), Order.class);
                        if (order != null && order.getCustomerId() != null) {
                            order.getId(); // Trigger UUID to int conversion
                            
                            String orderIdDisplay = order.getIdString() != null ? order.getIdString() : String.valueOf(order.getId());
                            
                            // Send database notification
                            NotificationService notificationService = new NotificationService(context);
                            String statusTitle = getStatusTitle(newStatus);
                            String statusMessage = getStatusMessage(orderIdDisplay, newStatus);
                            notificationService.createNotification(
                                order.getCustomerId(),
                                statusTitle,
                                statusMessage,
                                "order",
                                order.getId()
                            );
                            
                            // Send system notification
                            NotificationHelper notificationHelper = new NotificationHelper(context);
                            notificationHelper.showOrderStatusNotification(
                                orderIdDisplay,
                                newStatus,
                                order.getId()
                            );
                            
                            Log.d(TAG, "Notifications sent for order " + orderIdDisplay + " status: " + newStatus);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching order and sending notifications", e);
                // Don't fail the status update if notification fails
            }
        }).start();
    }
    
    /**
     * Get notification title based on status
     */
    private String getStatusTitle(String status) {
        if (status == null) return "Order Update";
        
        switch (status.toLowerCase()) {
            case "confirmed":
                return "Order Confirmed";
            case "preparing":
                return "Order Being Prepared";
            case "ready":
                return "Order Ready";
            case "delivering":
                return "Order Out for Delivery";
            case "completed":
                return "Order Completed";
            case "cancelled":
                return "Order Cancelled";
            default:
                return "Order Update";
        }
    }
    
    /**
     * Get notification message based on status
     */
    private String getStatusMessage(String orderId, String status) {
        if (status == null) return "Your order #" + orderId + " has been updated.";
        
        switch (status.toLowerCase()) {
            case "confirmed":
                return "Your order #" + orderId + " has been confirmed and is being prepared!";
            case "preparing":
                return "Your order #" + orderId + " is now being prepared. It will be ready soon!";
            case "ready":
                return "Your order #" + orderId + " is ready! You can pick it up or wait for delivery.";
            case "delivering":
                return "Your order #" + orderId + " is out for delivery! It will arrive soon.";
            case "completed":
                return "Your order #" + orderId + " has been completed. Thank you for your order!";
            case "cancelled":
                return "Your order #" + orderId + " has been cancelled.";
            default:
                return "Your order #" + orderId + " status has been updated to " + status + ".";
        }
    }
    
    private void incrementCancellationCount(String userId, String accessToken) {
        try {
            // First get current count
            Request getRequest = supabaseService
                .createAuthenticatedRequest("users?id=eq." + userId, accessToken)
                .get()
                .build();
            
            Response getResponse = supabaseService.executeRequest(getRequest);
            if (getResponse.isSuccessful()) {
                String responseBody = getResponse.body().string();
                User[] users = gson.fromJson(responseBody, User[].class);
                if (users.length > 0) {
                    int currentCount = users[0].getCancellationCount();
                    
                    JsonObject body = new JsonObject();
                    body.addProperty("cancellation_count", currentCount + 1);
                    
                    Request updateRequest = supabaseService
                        .createAuthenticatedRequest("users?id=eq." + userId, accessToken)
                        .patch(RequestBody.create(
                            MediaType.parse("application/json"),
                            body.toString()))
                        .build();
                    
                    supabaseService.executeRequest(updateRequest);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Increment cancellation count error", e);
        }
    }

    private Request.Builder createRequestWithAuth(String endpoint) {
        String accessToken = preferenceUtil != null ? preferenceUtil.getAccessToken() : null;
        if (!TextUtils.isEmpty(accessToken)) {
            return supabaseService.createAuthenticatedRequest(endpoint, accessToken);
        }
        return supabaseService.createRequest(endpoint);
    }
}


