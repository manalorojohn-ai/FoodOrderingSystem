package com.fp.foodorderingsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.common.WelcomeActivity;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ValidationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private TextInputLayout layoutEmail, layoutPassword;
    private AuthService authService;
    private PreferenceUtil preferenceUtil;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        authService = new AuthService(this);
        preferenceUtil = new PreferenceUtil(this);
        
        // Check if already logged in
        if (preferenceUtil.isLoggedIn()) {
            redirectToDashboard();
            return;
        }
        
        initViews();
        setupClickListeners();
    }
    
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        MaterialToolbar toolbar = findViewById(R.id.toolbarLogin);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        
        // Set up sign up link
        View signUpLink = findViewById(R.id.tvSignUpLink);
        if (signUpLink != null) {
            signUpLink.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                startActivity(intent);
            });
        }

        // Forgot password
        View forgot = findViewById(R.id.tvForgotPassword);
        if (forgot != null) {
            forgot.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
                startActivity(intent);
            });
        }
    }
    
    private void setupClickListeners() {
        View loginButton = findViewById(R.id.loginButton);
        if (loginButton != null) {
            loginButton.setOnClickListener(v -> performLogin());
        }
    }
    
    private void performLogin() {
        if (etEmail == null || etPassword == null) {
            showError("Please try again");
            return;
        }
        
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        
        // Validation
        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email");
            return;
        }
        
        if (!ValidationUtil.isValidEmail(email)) {
            showError("Please enter a valid email");
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password");
            return;
        }
        
        // For login, only check if password is not empty
        // Password strength validation is only for registration
        
        // Show loading
        findViewById(R.id.loginButton).setEnabled(false);
        
        authService.login(email, password, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(User user, String token) {
                runOnUiThread(() -> {
                    findViewById(R.id.loginButton).setEnabled(true);
                    
                    // Log user type for debugging
                    String userType = user.getUserType();
                    android.util.Log.d("LoginActivity", "Login successful - User ID: " + user.getId() + ", Email: " + user.getEmail() + ", User Type: " + userType);
                    
                    // Ensure user type is saved correctly
                    if (userType == null || userType.isEmpty()) {
                        android.util.Log.w("LoginActivity", "User type is null or empty, defaulting to customer");
                        userType = "customer";
                        user.setUserType(userType);
                    }
                    
                    preferenceUtil.saveUserData(user.getId(), user.getEmail(), user.getFullName(), userType);
                    
                    // Verify saved user type
                    String savedUserType = preferenceUtil.getUserType();
                    android.util.Log.d("LoginActivity", "Saved user type to preferences: " + savedUserType);
                    
                    redirectToDashboard();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    findViewById(R.id.loginButton).setEnabled(true);
                    showError(error);
                });
            }
        });
    }
    
    private void redirectToDashboard() {
        // Navigate to welcome page first, then it will redirect to appropriate dashboard
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

