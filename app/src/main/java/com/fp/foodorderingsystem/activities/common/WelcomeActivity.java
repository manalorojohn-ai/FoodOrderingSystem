package com.fp.foodorderingsystem.activities.common;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.admin.AdminDashboardActivity;
import com.fp.foodorderingsystem.activities.customer.CustomerDashboardActivity;
import com.fp.foodorderingsystem.utils.PreferenceUtil;

public class WelcomeActivity extends AppCompatActivity {
    private PreferenceUtil preferenceUtil;
    private static final int WELCOME_DELAY = 2000; // 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        
        preferenceUtil = new PreferenceUtil(this);
        
        // Check if user is logged in
        if (!preferenceUtil.isLoggedIn()) {
            // If not logged in, go back to login
            finish();
            return;
        }
        
        // Get user name
        String userName = preferenceUtil.getUserName();
        
        // Update welcome message
        TextView tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage);
        if (tvWelcomeMessage != null && userName != null && !userName.isEmpty()) {
            tvWelcomeMessage.setText("Welcome, " + userName + "!");
        }
        
        // Auto-navigate to dashboard after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            navigateToDashboard();
        }, WELCOME_DELAY);
        
        // Allow user to skip by tapping the screen
        findViewById(R.id.welcomeContainer).setOnClickListener(v -> {
            navigateToDashboard();
        });
    }
    
    private void navigateToDashboard() {
        String userType = preferenceUtil.getUserType();
        android.util.Log.d("WelcomeActivity", "Navigating to dashboard - User Type: " + userType);
        
        Intent intent;
        
        if (userType != null && "admin".equalsIgnoreCase(userType.trim())) {
            android.util.Log.d("WelcomeActivity", "Routing to Admin Dashboard");
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            android.util.Log.d("WelcomeActivity", "Routing to Customer Dashboard (userType: " + userType + ")");
            intent = new Intent(this, CustomerDashboardActivity.class);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back, just navigate to dashboard
        navigateToDashboard();
    }
}

