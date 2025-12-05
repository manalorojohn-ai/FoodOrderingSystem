package com.fp.foodorderingsystem.activities.common;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.NotificationAdapter;
import com.fp.foodorderingsystem.models.Notification;
import com.fp.foodorderingsystem.services.NotificationService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {
    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private NotificationService notificationService;
    private PreferenceUtil preferenceUtil;
    private List<Notification> notifications = new ArrayList<>();
    private SupabaseRealtimeClient realtimeClient;
    private NotificationAdapter notificationAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         setContentView(getLayoutResId());
        
        notificationService = new NotificationService(this);
        preferenceUtil = new PreferenceUtil(this);
        
        initViews();
        loadNotifications();
        subscribeToRealtimeNotifications();
    }
    
    protected int getLayoutResId() {
        return R.layout.activity_notifications;
    }

    protected String getScreenTitle() {
        return "Notifications";
    }

    private void initViews() {
        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvEmpty);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getScreenTitle());
        }
        toolbar.setNavigationOnClickListener(v -> onSupportNavigateUp());

        notificationAdapter = new NotificationAdapter(notification -> {
            if (!notification.isRead()) {
                notificationService.markAsRead(notification.getId(), () -> {});
                notificationAdapter.markAsRead(notification.getId());
            }
        });
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(notificationAdapter);
    }
    
    private void loadNotifications() {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            // Show cached notification when offline
            String cachedNotification = preferenceUtil.getLastNotification();
            if (!cachedNotification.isEmpty()) {
                try {
                    Notification notification = new Gson().fromJson(cachedNotification, Notification.class);
                    notifications.clear();
                    notifications.add(notification);
                    updateUI();
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
            return;
        }
        
        notificationService.getAllNotifications(new NotificationService.NotificationCallback() {
            @Override
            public void onSuccess(List<Notification> notificationList) {
                runOnUiThread(() -> {
                    notifications = notificationList != null ? notificationList : new ArrayList<>();
                    updateUI();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // Try to show cached notification
                    String cachedNotification = preferenceUtil.getLastNotification();
                    if (!cachedNotification.isEmpty()) {
                        try {
                            Notification notification = new Gson().fromJson(cachedNotification, Notification.class);
                            notifications.clear();
                            notifications.add(notification);
                            updateUI();
                        } catch (Exception e) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvNotifications.setVisibility(View.GONE);
                        }
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvNotifications.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void subscribeToRealtimeNotifications() {
        realtimeClient = new SupabaseRealtimeClient();
        realtimeClient.subscribeToTable("public", "notifications", new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                runOnUiThread(NotificationActivity.this::loadNotifications);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("NotificationActivity", "Realtime error: " + error);
            }
        });
    }

    private void updateUI() {
        if (notifications.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            if (notificationAdapter != null) {
                notificationAdapter.submitList(new ArrayList<>(notifications));
            }
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
    }
}

