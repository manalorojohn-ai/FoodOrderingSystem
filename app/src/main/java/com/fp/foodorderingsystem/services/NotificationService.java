package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.models.Notification;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private static final String TAG = "NotificationService";
    private SupabaseService supabaseService;
    private PreferenceUtil preferenceUtil;
    private Context context;
    private Gson gson;
    
    public NotificationService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.preferenceUtil = new PreferenceUtil(context);
        this.gson = new Gson();
    }
    
    public interface NotificationCallback {
        void onSuccess(List<Notification> notifications);
        void onError(String error);
    }
    
    public void getNotifications(String userId, NotificationCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            // Return cached notification when offline
            String cachedNotification = preferenceUtil.getLastNotification();
            if (!cachedNotification.isEmpty()) {
                try {
                    Notification notification = gson.fromJson(cachedNotification, Notification.class);
                    List<Notification> notifications = new ArrayList<>();
                    notifications.add(notification);
                    callback.onSuccess(notifications);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing cached notification", e);
                }
            }
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                String url = "notifications?user_id=eq." + userId + "&order=created_at.desc&limit=50";
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Notification[] notifications = gson.fromJson(responseBody, Notification[].class);
                    List<Notification> notificationList = new ArrayList<>();
                    for (Notification notification : notifications) {
                        notificationList.add(notification);
                    }
                    
                    // Cache the latest notification
                    if (!notificationList.isEmpty()) {
                        String latestJson = gson.toJson(notificationList.get(0));
                        preferenceUtil.saveLastNotification(latestJson, System.currentTimeMillis());
                    }
                    
                    callback.onSuccess(notificationList);
                } else {
                    callback.onError("Failed to load notifications");
                }
            } catch (Exception e) {
                Log.e(TAG, "Get notifications error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void getAllNotifications(NotificationCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                String url = "notifications?order=created_at.desc&limit=100";
                Request request = supabaseService
                    .createRequest(url)
                    .get()
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Notification[] notifications = gson.fromJson(responseBody, Notification[].class);
                    List<Notification> notificationList = new ArrayList<>();
                    for (Notification notification : notifications) {
                        notificationList.add(notification);
                    }
                    callback.onSuccess(notificationList);
                } else {
                    callback.onError("Failed to load notifications");
                }
            } catch (Exception e) {
                Log.e(TAG, "Get all notifications error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    public void markAsRead(int notificationId, Runnable callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("is_read", true);
                
                Request request = supabaseService
                    .createRequest("notifications?id=eq." + notificationId)
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()))
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful() && callback != null) {
                    callback.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "Mark as read error", e);
            }
        }).start();
    }
    
    public void createNotification(String userId, String title, String message, String type, Integer orderId) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("user_id", userId);
                body.addProperty("title", title);
                body.addProperty("message", message);
                body.addProperty("type", type);
                if (orderId != null) {
                    body.addProperty("order_id", orderId);
                }
                body.addProperty("is_read", false);
                
                Request request = supabaseService
                    .createRequest("notifications")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()))
                    .build();
                
                supabaseService.executeRequest(request);
            } catch (Exception e) {
                Log.e(TAG, "Create notification error", e);
            }
        }).start();
    }
}

