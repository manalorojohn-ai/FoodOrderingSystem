package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MenuItemService {
    private static final String TAG = "MenuItemService";
    private final SupabaseService supabaseService;
    private final Context context;
    private final Gson gson;
    
    public MenuItemService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.gson = new Gson();
    }
    
    public interface MenuItemsCallback {
        void onSuccess(List<FoodItem> items);
        void onError(String error);
    }
    
    public interface MenuItemCallback {
        void onSuccess(FoodItem item);
        void onError(String error);
    }
    
    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void getAllMenuItems(MenuItemsCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("menu_items?select=*&order=updated_at.desc")
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    FoodItem[] items = gson.fromJson(json, FoodItem[].class);
                    List<FoodItem> foodItems = new ArrayList<>();
                    if (items != null) {
                        for (FoodItem item : items) {
                            foodItems.add(item);
                        }
                    }
                    callback.onSuccess(foodItems);
                } else {
                    callback.onError("Failed to load items: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "getAllMenuItems error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void createMenuItem(FoodItem item, MenuItemCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = buildBodyFromItem(item);
                
                Request request = supabaseService.createRequest("menu_items")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"), body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    FoodItem[] items = gson.fromJson(json, FoodItem[].class);
                    if (items != null && items.length > 0) {
                        callback.onSuccess(items[0]);
                    } else {
                        callback.onError("Failed to parse created item");
                    }
                } else {
                    callback.onError("Failed to create item: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "createMenuItem error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void updateMenuItem(FoodItem item, MenuItemCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = buildBodyFromItem(item);
                
                Request request = supabaseService.createRequest("menu_items?id=eq." + item.getId())
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"), body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);

                if (response.isSuccessful()) {
                    String json = response.body() != null ? response.body().string() : null;

                    // Some PostgREST/Supabase configs return 204 No Content for updates.
                    // Treat any successful 2xx with empty body as a successful update
                    // and just return the item we sent.
                    if (json == null || json.trim().isEmpty() || "null".equalsIgnoreCase(json.trim())) {
                        callback.onSuccess(item);
                        return;
                    }

                    FoodItem[] items = gson.fromJson(json, FoodItem[].class);
                    if (items != null && items.length > 0) {
                        callback.onSuccess(items[0]);
                    } else {
                        callback.onSuccess(item); // fall back to the local item instead of failing
                    }
                } else {
                    String errorBody = "";
                    try {
                        if (response.body() != null) {
                            errorBody = response.body().string();
                        }
                    } catch (Exception ignored) { }

                    String errorMsg = "Failed to update item: " + response.code();
                    if (errorBody != null && !errorBody.isEmpty()) {
                        errorMsg += " - " + errorBody;
                    }
                    callback.onError(errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "updateMenuItem error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void updateStatus(int itemId, String status, MenuItemCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("status", status);
                
                Request request = supabaseService.createRequest("menu_items?id=eq." + itemId)
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"), body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    FoodItem[] items = gson.fromJson(json, FoodItem[].class);
                    if (items != null && items.length > 0) {
                        callback.onSuccess(items[0]);
                    } else {
                        callback.onError("Failed to parse updated item");
                    }
                } else {
                    callback.onError("Failed to update status: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "updateStatus error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void deleteMenuItem(int itemId, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("menu_items?id=eq." + itemId)
                    .delete()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to delete item: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteMenuItem error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    private JsonObject buildBodyFromItem(FoodItem item) {
        JsonObject body = new JsonObject();
        body.addProperty("name", item.getName());
        body.addProperty("description", item.getDescription());
        body.addProperty("price", item.getPrice());
        body.addProperty("status", item.getStatus() != null ? item.getStatus() : "available");
        body.addProperty("stock", item.getStock());
        body.addProperty("category_id", item.getCategoryId());
        if (item.getImagePath() != null) {
            body.addProperty("image_path", item.getImagePath());
        }
        if (item.getImageUrl() != null) {
            body.addProperty("image_url", item.getImageUrl());
        }
        return body;
    }
}

